package it.nicoloscialpi.mazegenerator.loadbalancer;

import org.bukkit.World;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Simple per-tick chunk load budget enforcer.
 */
public final class ChunkLoadLimiter {

    private static int remainingLoads = 0;
    private static boolean forceChunkLoad = true;
    private static JavaPlugin plugin;

    private ChunkLoadLimiter() {}

    public static void init(JavaPlugin pluginInstance) {
        plugin = pluginInstance;
    }

    public static void resetBudget() {
        if (plugin == null) return;
        remainingLoads = Math.max(0, plugin.getConfig().getInt("chunk-loads-per-tick", 0));
        forceChunkLoad = plugin.getConfig().getBoolean("force-chunk-load", true);
    }

    /**
     * Ensure the given chunk is loaded respecting budget/config.
     * @return true if the chunk is loaded (already loaded or loaded now); false if loading is disallowed or budget is exhausted.
     */
    public static boolean ensureLoaded(World world, int chunkX, int chunkZ) {
        if (world.isChunkLoaded(chunkX, chunkZ)) return true;
        if (!forceChunkLoad) {
            return false;
        }
        if (remainingLoads <= 0) {
            return false;
        }
        remainingLoads--;
        world.getChunkAt(chunkX, chunkZ);
        return true;
    }
}
