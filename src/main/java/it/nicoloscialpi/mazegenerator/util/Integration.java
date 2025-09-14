package it.nicoloscialpi.mazegenerator.util;

import org.bukkit.World;

import java.lang.reflect.Method;
import java.util.HashSet;
import java.util.Set;

public final class Integration {

    private static Boolean hasFolia;
    // FAWE integration dropped

    private Integration() {}

    public static boolean isFolia() {
        if (hasFolia != null) return hasFolia;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            hasFolia = true;
        } catch (Throwable ignored) {
            hasFolia = false;
        }
        return hasFolia;
    }

    // Chunk preloading dropped

    public static boolean runRegionTask(org.bukkit.plugin.java.JavaPlugin plugin, World world, int cx, int cz, Runnable task) {
        try {
            if (!isFolia()) return false;
            Object server = plugin.getServer();
            java.lang.reflect.Method getRegionScheduler = null;
            for (java.lang.reflect.Method m : server.getClass().getMethods()) {
                if (m.getName().equals("getRegionScheduler") && m.getParameterCount() == 0) { getRegionScheduler = m; break; }
            }
            if (getRegionScheduler == null) return false;
            Object regionScheduler = getRegionScheduler.invoke(server);
            // Try method: run(JavaPlugin, World, int, int, Runnable)
            for (java.lang.reflect.Method m : regionScheduler.getClass().getMethods()) {
                if (m.getName().equals("run") && m.getParameterCount() == 5) {
                    m.invoke(regionScheduler, plugin, world, cx, cz, task);
                    return true;
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
