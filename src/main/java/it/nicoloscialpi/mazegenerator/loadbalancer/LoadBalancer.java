package it.nicoloscialpi.mazegenerator.loadbalancer;

import it.nicoloscialpi.mazegenerator.MessageFileReader;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.boss.BossBar;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import net.kyori.adventure.text.Component;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LoadBalancer extends BukkitRunnable {

    private static TickEventListener eventListener = null;

    public static long LAST_TICK_START_NANOS = 0;
    private static final long TARGET_TICK_NANOS = 50_000_000L;

    private final ArrayDeque<LoadBalancerJob> jobs;
    private final Semaphore mutex;
    private final JavaPlugin plugin;
    private final JobProducer jobProducer;
    private boolean isDone;

    private long iterations;

    private final CommandSender commandSender;
    private int currentMillisPerTick;
    private final boolean autoTune;
    private final int minMillisPerTick;
    private final int maxMillisPerTick;
    private final int incStep;
    private final int decStep;
    private final int spareHigh;
    private final int spareLow;
    private final int statusEveryJobs;
    private long lastStatusAtIterations = 0;
    private long lastStatusAtMillis = 0;
    private static final long STATUS_MIN_INTERVAL_MS = 60_000L;
    private double spareNanosAvg = 0;
    private final Player playerTarget;
    private BossBar bossBar;

    public LoadBalancer(JavaPlugin plugin, CommandSender commandSender, JobProducer jobProducer) {
        this.plugin = plugin;
        this.jobProducer = jobProducer;
        if (eventListener == null) {
            eventListener = new TickEventListener(plugin);
        }
        ChunkLoadLimiter.init(plugin);
        this.commandSender = commandSender;
        this.playerTarget = commandSender instanceof Player p ? p : null;
        this.mutex = new Semaphore(1);
        this.jobs = new ArrayDeque<>();
        this.iterations = 0;
        this.isDone = false;
        this.currentMillisPerTick = Math.max(1, plugin.getConfig().getInt("millis-per-tick", 6));
        this.autoTune = plugin.getConfig().getBoolean("autotune.enabled", true);
        this.minMillisPerTick = Math.max(1, plugin.getConfig().getInt("autotune.min-millis-per-tick", 2));
        this.maxMillisPerTick = Math.max(this.currentMillisPerTick, plugin.getConfig().getInt("autotune.max-millis-per-tick", 12));
        this.incStep = Math.max(1, plugin.getConfig().getInt("autotune.increase-step", 1));
        this.decStep = Math.max(1, plugin.getConfig().getInt("autotune.decrease-step", 1));
        this.spareHigh = Math.max(0, plugin.getConfig().getInt("autotune.spare-high", 12));
        this.spareLow = Math.max(0, plugin.getConfig().getInt("autotune.spare-low", 6));
        this.statusEveryJobs = Math.max(1, plugin.getConfig().getInt("status-interval-jobs", 1000));
    }

    // Active tasks tracking to allow /maze stop
    private static final Set<LoadBalancer> ACTIVE = Collections.newSetFromMap(new ConcurrentHashMap<LoadBalancer, Boolean>());

    public synchronized boolean isDone() {
        return isDone;
    }

    public synchronized void start() {
        if (commandSender != null) {
            commandSender.sendMessage(MessageFileReader.getMessage("job-started"));
        }
        jobs.addAll(jobProducer.getJobs());
        ACTIVE.add(this);
        ChunkLoadLimiter.resetBudget();
        if (playerTarget != null) {
            bossBar = Bukkit.createBossBar("Maze build", BarColor.BLUE, BarStyle.SOLID);
            bossBar.addPlayer(playerTarget);
            bossBar.setProgress(0.0);
            bossBar.setVisible(true);
        }
        runTaskTimer(plugin, 0L, 1L);
    }

    public static synchronized void shutdown() {
        if (eventListener != null) {
            eventListener.unregister();
            eventListener = null;
        }
    }

    @Override
    public synchronized void run() {
        try {
            if (isDone()) {
                if (commandSender != null) {
                    commandSender.sendMessage(MessageFileReader.getMessage("job-done"));
                }
                cleanupBars();
                this.cancel();
                ACTIVE.remove(this);
                return;
            }

            // Auto-tune budget based on last tick spare time (Paper tick is ~50ms)
            if (autoTune && LAST_TICK_START_NANOS > 0) {
                long sinceTickStartNanos = System.nanoTime() - LAST_TICK_START_NANOS;
                long spareNanos = TARGET_TICK_NANOS - sinceTickStartNanos;
                if (spareNanosAvg == 0) {
                    spareNanosAvg = spareNanos;
                } else {
                    spareNanosAvg = spareNanosAvg * 0.7 + spareNanos * 0.3; // smooth jitter
                }
                double spareMillis = spareNanosAvg / 1_000_000.0;
                if (spareMillis >= spareHigh) {
                    currentMillisPerTick = Math.min(maxMillisPerTick, currentMillisPerTick + incStep);
                } else if (spareMillis < spareLow) {
                    currentMillisPerTick = Math.max(minMillisPerTick, currentMillisPerTick - decStep);
                }
            }

            long stopTime = System.nanoTime() + (currentMillisPerTick * 1_000_000L);
            mutex.acquire();

            // Consume jobs within the time budget
            boolean executedThisTick = false;
            while (!jobs.isEmpty() && System.nanoTime() <= stopTime) {
                LoadBalancerJob job = jobs.poll();
                if (job != null) {
                    if (job instanceof ChunkAwareJob chunkJob) {
                        if (!chunkJob.prepareChunks()) {
                            jobs.addLast(job); // defer to a future tick/budget
                            if (!executedThisTick) {
                                break; // avoid spinning when first job cannot run
                            }
                            continue;
                        }
                    }
                    job.compute();
                    executedThisTick = true;
                    iterations++;
                }
                if (commandSender != null && shouldSendStatus()) {
                    double percentage = jobProducer.getProgressPercentage();
                    sendStatus(percentage);
                    lastStatusAtIterations = iterations;
                    lastStatusAtMillis = System.currentTimeMillis();
                }
            }
            // Top-up jobs if queue is low
            if (jobs.isEmpty()) {
                List<LoadBalancerJob> next = jobProducer.getJobs();
                if (next.isEmpty()) {
                    if (jobs.isEmpty()) {
                        isDone = true;
                    }
                } else {
                    jobs.addAll(next);
                }
            }

            mutex.release();
        } catch (Exception e) {
            e.printStackTrace();
            this.cancel();
            ACTIVE.remove(this);
        }
    }

    public synchronized void stopNow() {
        try {
            isDone = true;
            if (commandSender != null) {
                commandSender.sendMessage(MessageFileReader.getMessage("job-stopped"));
            }
            this.cancel();
        } finally {
            cleanupBars();
            ACTIVE.remove(this);
        }
    }

    public static void stopAll() {
        for (LoadBalancer lb : ACTIVE.toArray(new LoadBalancer[0])) {
            lb.stopNow();
        }
    }

    private boolean shouldSendStatus() {
        long now = System.currentTimeMillis();
        return (now - lastStatusAtMillis) >= STATUS_MIN_INTERVAL_MS;
    }

    public static LoadBalancer getFor(CommandSender sender) {
        for (LoadBalancer lb : ACTIVE) {
            if (lb.commandSender == sender) {
                return lb;
            }
            if (sender instanceof Player p && lb.playerTarget != null && lb.playerTarget.getUniqueId().equals(p.getUniqueId())) {
                return lb;
            }
        }
        return null;
    }

    public double getProgressPercentage() {
        return jobProducer.getProgressPercentage();
    }

    public int getCurrentMillisPerTick() {
        return currentMillisPerTick;
    }

    private void sendStatus(double percentage) {
        String chat = MessageFileReader.getMessage("job-status")
                .replace("%percentage%", String.format("%.2f", percentage))
                + " [chunk loads: " + ChunkLoadLimiter.getConsumedLoads() + "/" + ChunkLoadLimiter.getBudgetPerTick()
                + ", budget: " + currentMillisPerTick + "ms]";
        commandSender.sendMessage(chat);
        if (playerTarget != null) {
            double clamped = Math.max(0.0, Math.min(1.0, percentage / 100.0));
            playerTarget.sendActionBar(Component.text(String.format("Maze build: %.2f%%", percentage)));
            if (bossBar != null) {
                bossBar.setProgress(clamped);
                bossBar.setTitle(String.format("Maze build: %.2f%%", percentage));
            }
        }
        // Console instrumentation
        plugin.getLogger().info("[MazeGen] " + String.format("%.2f", percentage) + "%, "
                + "jobs queue: " + jobs.size()
                + ", chunk loads this tick: " + ChunkLoadLimiter.getConsumedLoads() + "/" + ChunkLoadLimiter.getBudgetPerTick()
                + ", budget " + currentMillisPerTick + "ms");
    }

    private void cleanupBars() {
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
            bossBar = null;
        }
        if (playerTarget != null) {
            playerTarget.sendActionBar(Component.text("Maze build finished."));
        }
    }
}
