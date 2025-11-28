package it.nicoloscialpi.mazegenerator.command;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Renders a lightweight particle outline for the maze footprint.
 */
public final class MazePreviewer {
    private MazePreviewer() {}

    public static void showPreview(Player player, Location origin, int mazeSizeX, int mazeSizeZ, int cellSize) {
        if (player == null || origin == null || player.getWorld() == null) return;
        World world = player.getWorld();
        int width = mazeSizeX * cellSize;
        int depth = mazeSizeZ * cellSize;

        int step = Math.max(1, Math.min(4, cellSize)); // denser for small cells, capped for big mazes

        int maxParticles = 800;
        int count = 0;
        double y = origin.getY() + 1.5;
        Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(255, 64, 64), 1.0f);

        // Outline rectangle perimeter
        for (int dx = 0; dx <= width; dx += step) {
            if (count >= maxParticles) break;
            spawn(world, origin.getX() + dx, y, origin.getZ(), dust); count++;
            if (count >= maxParticles) break;
            spawn(world, origin.getX() + dx, y, origin.getZ() + depth, dust); count++;
        }
        for (int dz = 0; dz <= depth && count < maxParticles; dz += step) {
            if (count >= maxParticles) break;
            spawn(world, origin.getX(), y, origin.getZ() + dz, dust); count++;
            if (count >= maxParticles) break;
            spawn(world, origin.getX() + width, y, origin.getZ() + dz, dust); count++;
        }

        // Corner emphasis
        spawn(world, origin.getX(), y, origin.getZ(), dust);
        spawn(world, origin.getX() + width, y, origin.getZ(), dust);
        spawn(world, origin.getX(), y, origin.getZ() + depth, dust);
        spawn(world, origin.getX() + width, y, origin.getZ() + depth, dust);
    }

    private static void spawn(World world, double x, double y, double z, Particle.DustOptions dust) {
        world.spawnParticle(Particle.REDSTONE, x, y, z, 1, 0, 0, 0, 0, dust);
    }
}
