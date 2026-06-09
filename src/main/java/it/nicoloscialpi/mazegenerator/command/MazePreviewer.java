package it.nicoloscialpi.mazegenerator.command;

import it.nicoloscialpi.mazegenerator.maze.TerrainHeightMap;
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
    private static final double MAX_VIEW_DISTANCE = 80.0;
    private static final double MAX_VIEW_DISTANCE_SQ = MAX_VIEW_DISTANCE * MAX_VIEW_DISTANCE;

    public static void showPreview(JavaPlugin plugin, Player player, Location origin, int mazeSizeX, int mazeSizeZ, int cellSize, int wallHeight, boolean layDown) {
        if (plugin == null || player == null || origin == null || player.getWorld() == null) return;
        stopPreview(player);

        World world = player.getWorld();
        int width = mazeSizeX * cellSize;
        int depth = mazeSizeZ * cellSize;
        int step = Math.max(1, Math.min(4, cellSize)); // denser for small cells, capped for big mazes
        int maxParticles = 1400;
        double baseY = origin.getY();
        int[][] heights = null;
        if (layDown) {
            var sample = TerrainHeightMap.compute(world, origin.getBlockX(), origin.getBlockZ(), origin.getBlockY(), cellSize, mazeSizeX, mazeSizeZ, wallHeight);
            heights = sample.valid() ? sample.heights() : null;
        }
        List<Location> perimeter = new ArrayList<>();
        List<Location> topOutline = new ArrayList<>();
        List<Location> heightLines = new ArrayList<>();
        List<Location> diagonals = new ArrayList<>();

        int count = 0;
        for (int dx = 0; dx <= width && count < maxParticles; dx += step) {
            double yBaseNear = heightFor(heights, cellSize, dx, 0, baseY) + (layDown ? 0.5 : 0.0);
            double yBaseFar = heightFor(heights, cellSize, dx, depth, baseY) + (layDown ? 0.5 : 0.0);
            double yTopNear = yBaseNear + wallHeight + 1;
            double yTopFar = yBaseFar + wallHeight + 1;
            perimeter.add(new Location(world, origin.getX() + dx, yBaseNear, origin.getZ()));
            perimeter.add(new Location(world, origin.getX() + dx, yBaseFar, origin.getZ() + depth));
            topOutline.add(new Location(world, origin.getX() + dx, yTopNear, origin.getZ()));
            topOutline.add(new Location(world, origin.getX() + dx, yTopFar, origin.getZ() + depth));
            count += 2;
        }
        for (int dz = 0; dz <= depth && count < maxParticles; dz += step) {
            double yBaseNear = heightFor(heights, cellSize, 0, dz, baseY) + (layDown ? 0.5 : 0.0);
            double yBaseFar = heightFor(heights, cellSize, width, dz, baseY) + (layDown ? 0.5 : 0.0);
            double yTopNear = yBaseNear + wallHeight + 1;
            double yTopFar = yBaseFar + wallHeight + 1;
            perimeter.add(new Location(world, origin.getX(), yBaseNear, origin.getZ() + dz));
            perimeter.add(new Location(world, origin.getX() + width, yBaseFar, origin.getZ() + dz));
            topOutline.add(new Location(world, origin.getX(), yTopNear, origin.getZ() + dz));
            topOutline.add(new Location(world, origin.getX() + width, yTopFar, origin.getZ() + dz));
            count += 2;
        }

        // Diagonal cross lines across footprint for orientation
        for (int d = 0; d <= Math.max(width, depth) && count < maxParticles; d += Math.max(2, step)) {
            int rel = Math.min(d, Math.max(width, depth));
            double yDiag1 = heightFor(heights, cellSize, rel, rel, baseY) + (layDown ? 0.5 : 0.2);
            double yDiag2 = heightFor(heights, cellSize, rel, Math.max(0, depth - rel), baseY) + (layDown ? 0.5 : 0.2);
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), yDiag1, origin.getZ() + Math.min(d, depth)));
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), yDiag1 + wallHeight + 1, origin.getZ() + Math.min(d, depth)));
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), yDiag2, origin.getZ() + Math.max(0, depth - Math.min(d, depth))));
            diagonals.add(new Location(world, origin.getX() + Math.min(d, width), yDiag2 + wallHeight + 1, origin.getZ() + Math.max(0, depth - Math.min(d, depth))));
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
                double columnBase = heightFor(heights, cellSize, c[0], c[1], baseY) + (layDown ? 0.0 : 1.0);
                heightLines.add(new Location(world, origin.getX() + c[0], columnBase + dy, origin.getZ() + c[1]));
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
                spawnAll(perimeter, player);
                spawnAll(topOutline, player);
                spawnAll(heightLines, player);
                spawnAll(diagonals, player);
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

    private static void spawnAll(List<Location> points, Player viewer) {
        if (viewer == null) return;
        Location viewerLoc = viewer.getLocation();
        for (Location loc : points) {
            World w = loc.getWorld();
            if (w == null) continue;
            if (viewerLoc.getWorld() != null && !viewerLoc.getWorld().equals(w)) continue;
            if (viewerLoc.distanceSquared(loc) > MAX_VIEW_DISTANCE_SQ) continue;
            w.spawnParticle(Particle.END_ROD, loc.getX(), loc.getY(), loc.getZ(), 2, 0, 0, 0, 0);
        }
    }

    private static double heightFor(int[][] heights, int cellSize, int dx, int dz, double fallback) {
        if (heights == null || cellSize <= 0) return fallback;
        int r = Math.min(Math.max(dx / cellSize, 0), heights.length - 1);
        int c = Math.min(Math.max(dz / cellSize, 0), heights[0].length - 1);
        return heights[r][c];
    }
}
