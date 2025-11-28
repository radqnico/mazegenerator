package it.nicoloscialpi.mazegenerator.command;

import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Renders a persistent particle outline for the maze footprint until confirmed/cancelled.
 */
public final class MazePreviewer {
    private MazePreviewer() {}

    private static final Map<UUID, BukkitTask> ACTIVE = new HashMap<>();
    private static final double MAX_VIEW_DISTANCE = 50.0;
    private static final double MAX_VIEW_DISTANCE_SQ = MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE;

    public static void showPreview(JavaPlugin plugin, Player player, Location origin, int mazeSizeX, int mazeSizeZ, int cellSize, int wallHeight) {
        if (plugin == null || player == null || origin == null || player.getWorld() == null) return;
        stopPreview(player);

        World world = player.getWorld();
        int width = mazeSizeX * cellSize;
        int depth = mazeSizeZ * cellSize;
        int step = Math.max(1, Math.min(4, cellSize)); // denser for small cells, capped for big mazes
        int maxParticles = 1400;
        double baseY = origin.getY();
        Particle.DustOptions outlineDust = new Particle.DustOptions(Color.fromRGB(255, 64, 64), 1.2f);
        Particle.DustOptions topDust = new Particle.DustOptions(Color.fromRGB(64, 160, 255), 1.1f);
        Particle.DustOptions diagDust = new Particle.DustOptions(Color.fromRGB(120, 255, 120), 0.9f);

        List<Location> perimeter = new ArrayList<>();
        List<Location> topOutline = new ArrayList<>();
        List<Location> heightLines = new ArrayList<>();
        List<Location> diagonals = new ArrayList<>();

        int count = 0;
        for (int dx = 0; dx <= width && count < maxParticles; dx += step) {
            perimeter.add(new Location(world, origin.getX() + dx, baseY, origin.getZ()));
            perimeter.add(new Location(world, origin.getX() + dx, baseY, origin.getZ() + depth));
            topOutline.add(new Location(world, origin.getX() + dx, baseY + wallHeight, origin.getZ()));
            topOutline.add(new Location(world, origin.getX() + dx, baseY + wallHeight, origin.getZ() + depth));
            count += 2;
        }
        for (int dz = 0; dz <= depth && count < maxParticles; dz += step) {
            perimeter.add(new Location(world, origin.getX(), baseY, origin.getZ() + dz));
            perimeter.add(new Location(world, origin.getX() + width, baseY, origin.getZ() + dz));
            topOutline.add(new Location(world, origin.getX(), baseY + wallHeight, origin.getZ() + dz));
            topOutline.add(new Location(world, origin.getX() + width, baseY + wallHeight, origin.getZ() + dz));
            count += 2;
        }

        // Diagonal cross lines across footprint for orientation
        for (int d = 0; d <= Math.max(width, depth) && count < maxParticles; d += Math.max(2, step)) {
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), baseY + 0.2, origin.getZ() + Math.min(d, depth)));
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), baseY + wallHeight, origin.getZ() + Math.min(d, depth)));
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), baseY + 0.2, origin.getZ() + Math.max(0, depth - Math.min(d, depth))));
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), baseY + wallHeight, origin.getZ() + Math.max(0, depth - Math.min(d, depth))));
            count += 4;
        }

        // Height columns on corners
        int stepY = Math.max(1, Math.min(2, wallHeight));
        int heightLimit = Math.max(1, wallHeight);
        int[][] corners = new int[][]{
                {0, 0}, {width, 0}, {0, depth}, {width, depth}
        };
        for (int[] c : corners) {
            int dy = heightLimit;
            while (count < maxParticles) {
                heightLines.add(new Location(world, origin.getX() + c[0], baseY + dy, origin.getZ() + c[1]));
                count++;
                if (dy == 0) {
                    break; // reached ground
                }
                dy = Math.max(0, dy - stepY);
            }
            if (count >= maxParticles) {
                break;
            }
        }

        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                spawnAll(perimeter, outlineDust, player);
                spawnAll(topOutline, topDust, player);
                spawnAll(heightLines, outlineDust, player);
                spawnAll(diagonals, diagDust, player);
            }
        }.runTaskTimer(plugin, 0L, 10L); // every 0.5s

        ACTIVE.put(player.getUniqueId(), task);
    }

    public static void stopPreview(Player player) {
        if (player == null) return;
        BukkitTask task = ACTIVE.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private static void spawnAll(List<Location> points, Particle.DustOptions dust, Player viewer) {
        if (viewer == null) return;
        Location viewerLoc = viewer.getLocation();
        for (Location loc : points) {
            World w = loc.getWorld();
            if (w == null) continue;
            if (viewerLoc.getWorld() != null && !viewerLoc.getWorld().equals(w)) continue;
            if (viewerLoc.distanceSquared(loc) > MAX_VIEW_DISTANCE_SQ) continue;
            w.spawnParticle(Particle.DUST, loc.getX(), loc.getY(), loc.getZ(), 1, 0, 0, 0, 0, dust);
        }
    }
}
