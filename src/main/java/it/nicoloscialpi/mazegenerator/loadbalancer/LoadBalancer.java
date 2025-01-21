package it.nicoloscialpi.mazegenerator.loadbalancer;

import it.nicoloscialpi.mazegenerator.MessageFileReader;
import it.nicoloscialpi.mazegenerator.maze.MazePlacer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.Semaphore;

public class LoadBalancer extends BukkitRunnable {

    private static TickEventListener eventListener = null;

    private static final int MAX_MILLIS_PER_TICK = 5;
    public static long LAST_TICK_START_TIME = 0;

    private final ArrayDeque<LoadBalancerJob> jobs;
    private final Semaphore mutex;
    private final JavaPlugin plugin;
    private final MazePlacer mazePlacer;
    private boolean isDone;

    private long iterations;

    private final CommandSender commandSender;

    public LoadBalancer(JavaPlugin plugin, CommandSender commandSender, MazePlacer mazePlacer) {
        this.plugin = plugin;
        this.mazePlacer = mazePlacer;
        if (eventListener == null) {
            eventListener = new TickEventListener(plugin);
        }
        this.commandSender = commandSender;
        this.mutex = new Semaphore(1);
        jobs = new ArrayDeque<>();
        iterations = 0;
        this.isDone = false;
    }

    public synchronized boolean isDone() {
        return isDone;
    }

    public synchronized void start() {
        if (commandSender != null) {
            commandSender.sendMessage(MessageFileReader.getMessage("job-started"));
            jobs.addAll(mazePlacer.getJobs());
        }
        runTaskTimer(plugin, 0L, 1L);
    }

    @Override
    public synchronized void run() {
        try {
            if (isDone()) {
                if (commandSender != null) {
                    commandSender.sendMessage(MessageFileReader.getMessage("job-done"));
                }
                this.cancel();
                return;
            }
            long stopTime = System.currentTimeMillis() + MAX_MILLIS_PER_TICK;
            mutex.acquire();
            if (stopTime < LAST_TICK_START_TIME + 50) {
                if (jobs.isEmpty()) {
                    isDone = true;
                    return;
                }
                while (!jobs.isEmpty() && System.currentTimeMillis() <= stopTime) {
                    LoadBalancerJob poll = jobs.poll();
                    if (poll != null) {
                        poll.compute();
                        iterations++;
                        if (commandSender != null && iterations % 100000 == 0) {
                            double percentage = mazePlacer.getProgressPercentage();
                            commandSender.sendMessage(MessageFileReader.getMessage("job-status").replace("%percentage%", percentage + " " + mazePlacer.getLmLastR() + " " + mazePlacer.getLmLastC()));
                        }
                    }
                }
                jobs.addAll(mazePlacer.getJobs());
            }
            mutex.release();
        } catch (Exception e) {
            e.printStackTrace();
            this.cancel();
        }
    }
}
