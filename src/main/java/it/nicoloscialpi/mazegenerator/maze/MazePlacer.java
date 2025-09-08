package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.PlaceCellJob;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

import static it.nicoloscialpi.mazegenerator.maze.MazeGenerator.WALL;

public class MazePlacer implements it.nicoloscialpi.mazegenerator.loadbalancer.JobProducer {

    private final Theme theme;
    private final byte[][] mazeGrid;
    private final Location location;
    private final int height;
    private final int cellSize;
    private final boolean closed;
    private final boolean hollow;

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

    public MazePlacer(Theme theme, byte[][] mazeGrid, Location location, int height, int cellSize, boolean closed, boolean isHollow) {
        this.theme = theme;
        this.mazeGrid = mazeGrid;
        this.location = location;
        this.height = height;
        this.cellSize = cellSize;
        this.closed = closed;
        this.lmLastR = 0;
        this.lmLastC = 0;
        this.hollow = isHollow;
    }

    public int getTotalCells() {
        return mazeGrid.length * mazeGrid[0].length;
    }

    public int getComputedCells() {
        return lmLastR * mazeGrid[0].length + lmLastC;
    }

    

    public List<LoadBalancerJob> getNextJobs(int maxCells) {
        List<LoadBalancerJob> jobs = new ArrayList<>();

        if (lmLastR >= mazeGrid.length) {
            return jobs;
        }

        final boolean setBlockData = MazeGeneratorPlugin.plugin.getConfig().getBoolean("set-block-data", false);

        while (lmLastR < mazeGrid.length && jobs.size() < maxCells) {
            byte mazeCell = mazeGrid[lmLastR][lmLastC];
            Location placingLocation = location.clone().add(lmLastR * cellSize, 0, lmLastC * cellSize);
            jobs.add(new PlaceCellJob(
                    placingLocation.getBlockX(),
                    placingLocation.getBlockY(),
                    placingLocation.getBlockZ(),
                    mazeCell,
                    theme,
                    height,
                    cellSize,
                    closed,
                    hollow,
                    location.getWorld(),
                    setBlockData
            ));

            // Advance indices
            lmLastC++;
            if (lmLastC >= mazeGrid[0].length) {
                lmLastC = 0;
                lmLastR++;
            }
        }

        return jobs;
    }

    @Override
    public List<LoadBalancerJob> getJobs() {
        int batch = Math.max(1, MazeGeneratorPlugin.plugin.getConfig().getInt("jobs-batch-cells", 256));
        return getNextJobs(batch);
    }

    @Override
    public double getProgressPercentage() {
        return (double) getComputedCells() / (double) getTotalCells() * 100.0;
    }
}
