package it.nicoloscialpi.mazegenerator.loadbalancer;

import it.nicoloscialpi.mazegenerator.MessageFileReader;
import it.nicoloscialpi.mazegenerator.maze.BuildPhase;
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
    private long lastChatAtMillis = 0;
    private long lastBarAtMillis = 0;
    private static final long CHAT_INTERVAL_MS = 60_000L;
    private static final long BAR_INTERVAL_MS = 1_000L;
    private java.util.EnumMap<BuildPhase, BossBar> phaseBars;
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
            phaseBars = new java.util.EnumMap<>(BuildPhase.class);
            for (BuildPhase phase : BuildPhase.values()) {
                BossBar bar = Bukkit.createBossBar(formatPhaseTitle(phase, 0.0), getPhaseColor(phase), BarStyle.SOLID);
                bar.addPlayer(playerTarget);
                bar.setProgress(0.0);
                bar.setVisible(true);
                phaseBars.put(phase, bar);
            }
            bossBar = phaseBars.get(BuildPhase.GENERATION); // primary bar reference kept for cleanup
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
                if (commandSender != null) {
                    boolean sendChat = shouldSendChat();
                    boolean sendBars = shouldSendBars();
                    if (sendChat || sendBars) {
                        double percentage = jobProducer.getProgressPercentage();
                        PhaseProgressSnapshot phaseSnapshot = jobProducer.getPhaseProgress();
                        if (sendChat) {
                            sendChatStatus(percentage, phaseSnapshot);
                            lastChatAtMillis = System.currentTimeMillis();
                        }
                        if (sendBars) {
                            sendBarStatus(phaseSnapshot);
                            lastBarAtMillis = System.currentTimeMillis();
                        }
                    }
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

    private boolean shouldSendChat() {
        long now = System.currentTimeMillis();
        return (now - lastChatAtMillis) >= CHAT_INTERVAL_MS;
    }

    private boolean shouldSendBars() {
        if (playerTarget == null) return false;
        long now = System.currentTimeMillis();
        return (now - lastBarAtMillis) >= BAR_INTERVAL_MS;
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

    private BarColor getPhaseColor(BuildPhase phase) {
        return switch (phase) {
            case GENERATION -> BarColor.YELLOW;
            case PLACEMENT -> BarColor.BLUE;
            case CARVING -> BarColor.GREEN;
        };
    }

    private String formatPhaseTitle(BuildPhase phase, double pct) {
        return String.format("Maze %s: %.2f%%", phase.getKey(), pct);
    }

    private void sendChatStatus(double percentage, PhaseProgressSnapshot snapshot) {
        String phaseKey = snapshot != null ? snapshot.currentPhase().getKey() : "unknown";
        String chat = MessageFileReader.getMessage("job-status")
                .replace("%percentage%", String.format("%.2f", percentage))
                .replace("%phase%", phaseKey)
                + " [chunk loads: " + ChunkLoadLimiter.getConsumedLoads() + "/" + ChunkLoadLimiter.getBudgetPerTick()
                + ", budget: " + currentMillisPerTick + "ms]";
        commandSender.sendMessage(chat);
        // Console instrumentation
        plugin.getLogger().info("[MazeGen] " + String.format("%.2f", percentage) + "%, "
                + "jobs queue: " + jobs.size()
                + ", chunk loads this tick: " + ChunkLoadLimiter.getConsumedLoads() + "/" + ChunkLoadLimiter.getBudgetPerTick()
                + ", budget " + currentMillisPerTick + "ms");
    }

    private void sendBarStatus(PhaseProgressSnapshot snapshot) {
        if (playerTarget == null || snapshot == null) return;
        double pct = snapshot.get(snapshot.currentPhase());
        double clamped = Math.max(0.0, Math.min(1.0, pct / 100.0));
        playerTarget.sendActionBar(Component.text(String.format("Maze %s: %.2f%%", snapshot.currentPhase().getKey(), pct)));
        if (phaseBars != null) {
            for (BuildPhase phase : BuildPhase.values()) {
                double phasePct = snapshot.get(phase);
                double phaseClamped = Math.max(0.0, Math.min(1.0, phasePct / 100.0));
                BossBar bar = phaseBars.get(phase);
                if (bar != null) {
                    bar.setProgress(phaseClamped);
                    bar.setTitle(formatPhaseTitle(phase, phasePct));
                }
            }
        }
    }

    private void cleanupBars() {
        if (phaseBars != null) {
            for (BossBar bar : phaseBars.values()) {
                bar.removeAll();
                bar.setVisible(false);
            }
            phaseBars.clear();
            phaseBars = null;
        }
        bossBar = null;
        if (playerTarget != null) {
            playerTarget.sendActionBar(Component.text("Maze build finished."));
        }
    }
}
