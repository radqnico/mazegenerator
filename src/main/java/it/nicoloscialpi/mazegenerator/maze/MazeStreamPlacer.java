package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.PlaceCellJob;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class MazeStreamPlacer implements it.nicoloscialpi.mazegenerator.loadbalancer.JobProducer {

    private final Theme theme;
    private final Location location;
    private final int height;
    private final int cellSize;
    private final boolean closed;
    private final boolean hollow;

    private final IncrementalMazeGenerator generator;
    private final int sizeN;
    private final int sizeM;
    // parameters used to initialize generator (kept for clarity, not persisted)
    private final int additionalExits;
    private final double erosion;
    private final boolean hasRoom;
    private final int roomSizeX;
    private final int roomSizeZ;
    private final boolean hasExits;

    // Optional deferred wall fill to speed initial placement
    private final boolean deferWallFill = MazeGeneratorPlugin.plugin.getConfig().getBoolean("defer-wall-fill", false);
    private boolean carvingDone = false;
    private int fillR = 0;
    private int fillC = 0;
    private final java.util.BitSet carved = new java.util.BitSet();
    private long filledWalls = 0;

    

    public MazeStreamPlacer(Theme theme,
                            Location location,
                            int height,
                            int cellSize,
                            boolean closed,
                            boolean isHollow,
                            int sizeN,
                            int sizeM,
                            int additionalExits,
                            double erosion,
                            boolean hasRoom,
                            int roomSizeX,
                            int roomSizeZ,
                            boolean hasExits) {
        this.theme = theme;
        this.location = location;
        this.height = height;
        this.cellSize = cellSize;
        this.closed = closed;
        this.hollow = isHollow;
        this.sizeN = (sizeN % 2 == 0) ? sizeN + 1 : sizeN;
        this.sizeM = (sizeM % 2 == 0) ? sizeM + 1 : sizeM;
        this.additionalExits = additionalExits;
        this.erosion = erosion;
        this.hasRoom = hasRoom;
        this.roomSizeX = roomSizeX;
        this.roomSizeZ = roomSizeZ;
        this.hasExits = hasExits;

        this.generator = new IncrementalMazeGenerator(this.sizeN, this.sizeM,
                additionalExits, erosion, hasRoom, roomSizeX, roomSizeZ, hasExits);
    }

    @Override
    public List<LoadBalancerJob> getJobs() {
        int batch = Math.max(1, MazeGeneratorPlugin.plugin.getConfig().getInt("jobs-batch-cells", 256));
        boolean setBlockData = MazeGeneratorPlugin.plugin.getConfig().getBoolean("set-block-data", false);
        ArrayList<LoadBalancerJob> jobs = new ArrayList<>(batch);

        if (deferWallFill) {
            // Carve first for fast initial visual
            if (!carvingDone) {
                var cells = generator.pollNextCells(batch);
                for (IncrementalMazeGenerator.Cell cell : cells) {
                    int r = cell.r();
                    int c = cell.c();
                    int worldX = location.getBlockX() + r * cellSize;
                    int worldZ = location.getBlockZ() + c * cellSize;
                    int worldY = location.getBlockY();
                    carved.set(r * sizeM + c);
                    jobs.add(new PlaceCellJob(worldX, worldY, worldZ, cell.type(), theme, height, cellSize, closed, hollow, location.getWorld(), setBlockData));
                }
                if (jobs.isEmpty()) {
                    carvingDone = true;
                }
                return jobs;
            }
            // After carving, gradually fill the remaining walls
            while (jobs.size() < batch && fillR < sizeN) {
                int worldX = location.getBlockX() + fillR * cellSize;
                int worldZ = location.getBlockZ() + fillC * cellSize;
                int worldY = location.getBlockY();
                int idx = fillR * sizeM + fillC;
                if (!carved.get(idx)) { // only fill true walls
                    jobs.add(new PlaceCellJob(worldX, worldY, worldZ, IncrementalMazeGenerator.WALL, theme, height, cellSize, true, hollow, location.getWorld(), setBlockData));
                    filledWalls++;
                }
                fillC++;
                if (fillC >= sizeM) { fillC = 0; fillR++; }
            }
            return jobs;
        } else {
            // Original: fill first, then carve
            // Phase 1: fill all cells as WALL
            while (jobs.size() < batch && fillR < sizeN) {
                int worldX = location.getBlockX() + fillR * cellSize;
                int worldZ = location.getBlockZ() + fillC * cellSize;
                int worldY = location.getBlockY();
                jobs.add(new PlaceCellJob(worldX, worldY, worldZ, IncrementalMazeGenerator.WALL, theme, height, cellSize, true, hollow, location.getWorld(), setBlockData));
                fillC++;
                if (fillC >= sizeM) { fillC = 0; fillR++; }
            }
            if (jobs.size() >= batch) return jobs;

            // Phase 2: carve
            var cells = generator.pollNextCells(batch - jobs.size());
            for (IncrementalMazeGenerator.Cell cell : cells) {
                int r = cell.r();
                int c = cell.c();
                int worldX = location.getBlockX() + r * cellSize;
                int worldZ = location.getBlockZ() + c * cellSize;
                int worldY = location.getBlockY();
                jobs.add(new PlaceCellJob(worldX, worldY, worldZ, cell.type(), theme, height, cellSize, closed, hollow, location.getWorld(), setBlockData));
            }
            return jobs;
        }
    }

    @Override
    public double getProgressPercentage() {
        long phase1Total = (long) sizeN * (long) sizeM;
        long phase2Total = (long) sizeN * (long) sizeM; // upper bound for carving
        long carvedCount = carved.cardinality();
        long phase2Done = generator.getEmittedCount();
        long total;
        long done;
        if (deferWallFill) {
            // First carve progress, then wall fill
            if (!carvingDone) {
                total = phase2Total;
                done = phase2Done;
            } else {
                long wallsToFill = phase1Total - carvedCount;
                total = Math.max(1, wallsToFill);
                done = Math.min(filledWalls, total);
            }
        } else {
            long phase1DoneApprox = (long) fillR * (long) sizeM + fillC; // approximate
            total = phase1Total + phase2Total;
            done = phase1DoneApprox + phase2Done;
        }
        if (total <= 0) return 100.0;
        double pct = (double) done / (double) total * 100.0;
        return Math.max(0.0, Math.min(100.0, pct));
    }

}
