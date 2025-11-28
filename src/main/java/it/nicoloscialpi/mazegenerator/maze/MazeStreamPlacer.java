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
    private final int additionalExits;
    private final double erosion;
    private final boolean hasRoom;
    private final int roomSizeX;
    private final int roomSizeZ;
    private final boolean hasExits;

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
        int cellsPerJob = Math.max(1, MazeGeneratorPlugin.plugin.getConfig().getInt("cells-per-job", 16));
        ArrayList<LoadBalancerJob> jobs = new ArrayList<>(Math.max(1, batch / cellsPerJob));
        java.util.HashMap<Long, java.util.ArrayList<int[]>> groups = new java.util.HashMap<>();
        final int worldY = location.getBlockY();
        final int baseX = location.getBlockX();
        final int baseZ = location.getBlockZ();
        final org.bukkit.World world = location.getWorld();
        final java.util.function.BiConsumer<Long, int[]> addToGroup = (key, cell) -> {
            long chunkKey = key.longValue();
            var list = groups.computeIfAbsent(chunkKey, k2 -> new java.util.ArrayList<>());
            list.add(cell);
            if (list.size() >= cellsPerJob) {
                int[][] arr = list.toArray(new int[0][]);
                int cx = (int) (chunkKey >> 32);
                int cz = (int) chunkKey;
                jobs.add(new it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob(world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr));
                groups.remove(chunkKey);
            }
        };
        int collected = 0;

        if (deferWallFill) {
            // Phase 1: carve stream first for fast initial visual
            if (!carvingDone) {
                var cells = generator.pollNextCells(batch - jobs.size());
                for (IncrementalMazeGenerator.Cell cell : cells) {
                    int r = cell.r();
                    int c = cell.c();
                    carved.set(r * sizeM + c);
                    // compute cell info and group by chunk
                    int worldX = baseX + r * cellSize;
                    int worldZ = baseZ + c * cellSize;
                    int cx = Math.floorDiv(worldX, 16);
                    int cz = Math.floorDiv(worldZ, 16);
                    long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                    addToGroup.accept(key, new int[]{worldX, worldY, worldZ, cell.type()});
                    collected++;
                    if (collected >= batch) break;
                    if (jobs.size() >= batch) break;
                }
                if (jobs.isEmpty()) {
                    // Carving done, move to wall fill phase
                    carvingDone = true;
                }
                // fallthrough to allow flushing groups below
            }
            // Phase 2: gradually fill remaining wall cells
            while (collected < batch && fillR < sizeN) {
                int idx = fillR * sizeM + fillC;
                if (!carved.get(idx)) {
                    int r = fillR, c = fillC;
                    int worldX = baseX + r * cellSize;
                    int worldZ = baseZ + c * cellSize;
                    int cx = Math.floorDiv(worldX, 16);
                    int cz = Math.floorDiv(worldZ, 16);
                    long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                    addToGroup.accept(key, new int[]{worldX, worldY, worldZ, it.nicoloscialpi.mazegenerator.maze.IncrementalMazeGenerator.WALL});
                    filledWalls++;
                    collected++;
                }
                fillC++;
                if (fillC >= sizeM) { fillC = 0; fillR++; }
            }
            // Flush remaining groups
            for (var e : groups.entrySet()) {
                int[][] arr = e.getValue().toArray(new int[0][]);
                long key = e.getKey();
                int cx = (int) (key >> 32);
                int cz = (int) (key);
                jobs.add(new it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob(world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr));
            }
            groups.clear();
            return jobs;
        } else {
            // Phase 1: fill walls
            while (collected < batch && fillR < sizeN) {
                int idx = fillR * sizeM + fillC;
                if (!carved.get(idx)) {
                    int r = fillR, c = fillC;
                    int worldX = baseX + r * cellSize;
                    int worldZ = baseZ + c * cellSize;
                    int cx = Math.floorDiv(worldX, 16);
                    int cz = Math.floorDiv(worldZ, 16);
                    long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                    addToGroup.accept(key, new int[]{worldX, worldY, worldZ, it.nicoloscialpi.mazegenerator.maze.IncrementalMazeGenerator.WALL});
                    collected++;
                }
                fillC++;
                if (fillC >= sizeM) { fillC = 0; fillR++; }
            }
            if (collected >= batch) {
                for (var e : groups.entrySet()) {
                    int[][] arr = e.getValue().toArray(new int[0][]);
                    long key = e.getKey();
                    int cx = (int) (key >> 32);
                    int cz = (int) (key);
                    jobs.add(new it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob(world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr));
                }
                groups.clear();
                return jobs;
            }

            // Phase 2: carve
            var cells = generator.pollNextCells(batch - collected);
            for (IncrementalMazeGenerator.Cell cell : cells) {
                int r = cell.r();
                int c = cell.c();
                int idx = r * sizeM + c;
                if (cell.type() != IncrementalMazeGenerator.WALL) {
                    carved.set(idx);
                }
                int worldX = baseX + r * cellSize;
                int worldZ = baseZ + c * cellSize;
                int cx = Math.floorDiv(worldX, 16);
                int cz = Math.floorDiv(worldZ, 16);
                long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                addToGroup.accept(key, new int[]{worldX, worldY, worldZ, cell.type()});
                collected++;
                if (collected >= batch) break;
            }
            for (var e : groups.entrySet()) {
                int[][] arr = e.getValue().toArray(new int[0][]);
                long key = e.getKey();
                int cx = (int) (key >> 32);
                int cz = (int) (key);
                jobs.add(new it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob(world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr));
            }
            groups.clear();
            return jobs;
        }
    }

    @Override
    public double getProgressPercentage() {
        long phase1Total = (long) sizeN * (long) sizeM;
        long phase2Total = (long) sizeN * (long) sizeM;
        long carvedCount = carved.cardinality();
        long phase2Done = generator.getEmittedCount();
        long total;
        long done;
        if (deferWallFill) {
            if (!carvingDone) {
                total = phase2Total;
                done = phase2Done;
            } else {
                long wallsToFill = phase1Total - carvedCount;
                total = Math.max(1, wallsToFill);
                done = Math.min(filledWalls, total);
            }
        } else {
            long phase1DoneApprox = (long) fillR * (long) sizeM + fillC;
            total = phase1Total + phase2Total;
            done = phase1DoneApprox + phase2Done;
        }
        if (total <= 0) return 100.0;
        double pct = (double) done / (double) total * 100.0;
        return Math.max(0.0, Math.min(100.0, pct));
    }
}
