package it.nicoloscialpi.mazegenerator.loadbalancer;

import it.nicoloscialpi.mazegenerator.MessageFileReader;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LoadBalancer extends BukkitRunnable {

    private static TickEventListener eventListener = null;

    public static long LAST_TICK_START_TIME = 0;

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

    public LoadBalancer(JavaPlugin plugin, CommandSender commandSender, JobProducer jobProducer) {
        this.plugin = plugin;
        this.jobProducer = jobProducer;
        if (eventListener == null) {
            eventListener = new TickEventListener(plugin);
        }
        this.commandSender = commandSender;
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
                this.cancel();
                ACTIVE.remove(this);
                return;
            }

            // Auto-tune budget based on last tick spare time (Paper tick is ~50ms)
            if (autoTune && LAST_TICK_START_TIME > 0) {
                long sinceTickStart = System.currentTimeMillis() - LAST_TICK_START_TIME;
                long spare = 50 - sinceTickStart; // ms left in this tick window
                if (spare >= spareHigh) {
                    currentMillisPerTick = Math.min(maxMillisPerTick, currentMillisPerTick + incStep);
                } else if (spare < spareLow) {
                    currentMillisPerTick = Math.max(minMillisPerTick, currentMillisPerTick - decStep);
                }
            }

            long stopTime = System.currentTimeMillis() + currentMillisPerTick;
            mutex.acquire();

            // Consume jobs within the time budget
            while (!jobs.isEmpty() && System.currentTimeMillis() <= stopTime) {
                LoadBalancerJob job = jobs.poll();
                if (job != null) {
                    job.compute();
                    iterations++;
                    if (commandSender != null && (iterations - lastStatusAtIterations) >= statusEveryJobs) {
                        double percentage = jobProducer.getProgressPercentage();
                        commandSender.sendMessage(
                                MessageFileReader.getMessage("job-status")
                                        .replace("%percentage%", String.format("%.2f", percentage))
                        );
                        lastStatusAtIterations = iterations;
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
            ACTIVE.remove(this);
        }
    }

    public static void stopAll() {
        for (LoadBalancer lb : ACTIVE.toArray(new LoadBalancer[0])) {
            lb.stopNow();
        }
    }
}
