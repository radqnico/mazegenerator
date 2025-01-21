package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.PlaceBlockJob;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Biome;

import java.util.ArrayList;
import java.util.List;

import static it.nicoloscialpi.mazegenerator.maze.MazeGenerator.*;

public class MazePlacer {

    private final Theme theme;
    private final byte[][] mazeGrid;
    private final Location location;
    private final int height;
    private final int cellSize;
    private final boolean closed;
    private final boolean hollow;
    private final long maxMemoryBytes; // Max memory in bytes

    private int lmLastR;
    private int lmLastC;

    public long getTotalJobs() {
        return (long) mazeGrid.length * (long) mazeGrid[0].length;
    }

    public int getLmLastR() {
        return lmLastR;
    }

    public int getLmLastC() {
        return lmLastC;
    }

    public MazePlacer(Theme theme, byte[][] mazeGrid, Location location, int height, int cellSize, boolean closed, boolean isHollow, double maxMemoryGB) {
        this.theme = theme;
        this.mazeGrid = mazeGrid;
        this.location = location;
        this.height = height;
        this.cellSize = cellSize;
        this.closed = closed;
        this.lmLastR = 0;
        this.lmLastC = 0;
        this.hollow = isHollow;
        if (maxMemoryGB <= 0) {
            Runtime runtime = Runtime.getRuntime();
            this.maxMemoryBytes = runtime.totalMemory();
        } else {
            this.maxMemoryBytes = (long) (maxMemoryGB * 1024 * 1024 * 1024); // Convert GB to bytes
        }
    }

    public int getTotalCells() {
        return (int) mazeGrid.length * (int) mazeGrid[0].length;
    }

    public int getComputedCells() {
        // computed cells calculation from lmLastR and lmLastC
        return (int) lmLastR * (int) mazeGrid[0].length + (int) lmLastC;
    }

    public double getProgressPercentage() {
        return (double) getComputedCells() / (double) getTotalCells() * 100;
    }

    private long getUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        return runtime.totalMemory() - runtime.freeMemory();
    }

    public List<LoadBalancerJob> getNextJobsUntilMemoryLimit() {
        List<LoadBalancerJob> jobs = new ArrayList<>();

        // Return an empty list if all jobs are processed
        if (lmLastR >= mazeGrid.length) {
            return jobs;
        }

        while (lmLastR < mazeGrid.length) {
            // Generate jobs for the current column
            List<LoadBalancerJob> colJob = getColJob(lmLastR, lmLastC);
            jobs.addAll(colJob);

            // Update lmLastC and lmLastR for the next call
            lmLastC++;
            if (lmLastC >= mazeGrid[0].length) {
                lmLastC = 0;
                lmLastR++;
            }

            // Check memory usage and stop if we exceed the limit
            if (getUsedMemory() >= maxMemoryBytes) {
                MazeGeneratorPlugin.plugin.getLogger().info("Memory limit reached. Saving progress at row: " + lmLastR + ", column: " + lmLastC);
                break;
            }
        }

        System.out.println("lmLastR: " + lmLastR + ", lmLastC: " + lmLastC);
        System.out.println("Total cells: " + getTotalCells() + ", Computed cells: " + getComputedCells());
        System.out.println("Progress: " + getProgressPercentage() + "%");

        return jobs;
    }

    public List<LoadBalancerJob> getColJob(int r, int c) {
        byte mazeCell = mazeGrid[r][c];

        Location placingLocation = location.clone();
        placingLocation = placingLocation.add(r * cellSize, 0, c * cellSize);
        List<LoadBalancerJob> columnJobs = createColumnJobs(placingLocation.getBlockX(), placingLocation.getBlockY(), placingLocation.getBlockZ(), mazeCell, hollow);

        return new ArrayList<>(columnJobs);
    }

    public List<LoadBalancerJob> getJobs() {
        return getNextJobsUntilMemoryLimit();
    }

    public List<LoadBalancerJob> createColumnJobs(int worldX, int worldY, int worldZ, byte type, boolean hollow) {
        List<LoadBalancerJob> jobs = new ArrayList<>();

        // Height 0 floor
        int y = 0;
        for (int x = 0; x < cellSize; x++) {
            for (int z = 0; z < cellSize; z++) {
                Material material = theme.getRandomFloorMaterial();
                jobs.add(new PlaceBlockJob(worldX + x, worldY + y, worldZ + z, material, location.getWorld()));
            }
        }

        // Height 1 to height-1 walls or hollow walls
        for (y = 1; y < height; y++) {
            for (int x = 0; x < cellSize; x++) {
                for (int z = 0; z < cellSize; z++) {
                    if (type == WALL) {
                        // For hollow columns, only generate jobs for the edges
                        if (!hollow || x == 0 || x == cellSize - 1 || z == 0 || z == cellSize - 1) {
                            Material material = theme.getRandomWallMaterial();
                            jobs.add(new PlaceBlockJob(worldX + x, worldY + y, worldZ + z, material, location.getWorld()));
                        }
                    } else {
                        jobs.add(new PlaceBlockJob(worldX + x, worldY + y, worldZ + z, Material.AIR, location.getWorld()));
                    }
                }
            }
        }

        // Top
        y = height;
        for (int x = 0; x < cellSize; x++) {
            for (int z = 0; z < cellSize; z++) {
                if (closed || type == WALL) {
                    // For hollow columns, only generate jobs for the edges of the top layer
                    if (!hollow || x == 0 || x == cellSize - 1 || z == 0 || z == cellSize - 1) {
                        Material material = theme.getRandomTopMaterial();
                        jobs.add(new PlaceBlockJob(worldX + x, worldY + y, worldZ + z, material, location.getWorld()));
                    }
                } else {
                    jobs.add(new PlaceBlockJob(worldX + x, worldY + y, worldZ + z, Material.AIR, location.getWorld()));
                }
            }
        }

        return jobs;
    }


}
