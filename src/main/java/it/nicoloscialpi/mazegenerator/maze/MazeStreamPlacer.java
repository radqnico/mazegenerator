package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MazeStreamPlacer implements it.nicoloscialpi.mazegenerator.loadbalancer.JobProducer {

    private final Theme theme;
    private final Location location;
    private final World world;
    private final int baseX;
    private final int baseY;
    private final int baseZ;
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
    private final BitSet carved = new BitSet();
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
        this.world = location.getWorld();
        this.baseX = location.getBlockX();
        this.baseY = location.getBlockY();
        this.baseZ = location.getBlockZ();
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
        int maxBlocksPerJob = Math.max(64, MazeGeneratorPlugin.plugin.getConfig().getInt("max-blocks-per-job", 2048));
        int blocksPerCell = Math.max(1, cellSize * cellSize * (height + 1));
        int effectiveCellsPerJob = Math.max(1, Math.min(cellsPerJob, Math.max(1, maxBlocksPerJob / blocksPerCell)));
        ArrayList<LoadBalancerJob> jobs = new ArrayList<>(Math.max(1, batch / effectiveCellsPerJob));
        HashMap<Long, ArrayList<int[]>> groups = new HashMap<>();
        int collected = 0;

        if (deferWallFill) {
            if (!carvingDone) {
                int carveBudget = batch - jobs.size();
                var cells = generator.pollNextCells(carveBudget);
                for (IncrementalMazeGenerator.Cell cell : cells) {
                    int r = cell.r();
                    int c = cell.c();
                    carved.set(r * sizeM + c);
                    int worldX = baseX + r * cellSize;
                    int worldZ = baseZ + c * cellSize;
                    addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, baseY, worldZ, cell.type());
                    collected++;
                    if (collected >= batch) break;
                    if (jobs.size() >= batch) break;
                }
                if (jobs.isEmpty()) {
                    carvingDone = true;
                }
            }

            while (collected < batch && fillR < sizeN) {
                int idx = fillR * sizeM + fillC;
                if (!carved.get(idx)) {
                    int r = fillR;
                    int c = fillC;
                    int worldX = baseX + r * cellSize;
                    int worldZ = baseZ + c * cellSize;
                    addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, baseY, worldZ, IncrementalMazeGenerator.WALL);
                    filledWalls++;
                    collected++;
                }
                fillC++;
                if (fillC >= sizeM) {
                    fillC = 0;
                    fillR++;
                }
            }
            flushRemainingGroups(groups, jobs, setBlockData);
            return jobs;
        }

        while (collected < batch && fillR < sizeN) {
            int idx = fillR * sizeM + fillC;
            if (!carved.get(idx)) {
                int r = fillR;
                int c = fillC;
                int worldX = baseX + r * cellSize;
                int worldZ = baseZ + c * cellSize;
                addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, baseY, worldZ, IncrementalMazeGenerator.WALL);
                collected++;
            }
            fillC++;
            if (fillC >= sizeM) {
                fillC = 0;
                fillR++;
            }
        }
        if (collected >= batch) {
            flushRemainingGroups(groups, jobs, setBlockData);
            return jobs;
        }

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
            addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, baseY, worldZ, cell.type());
            collected++;
            if (collected >= batch) break;
        }
        flushRemainingGroups(groups, jobs, setBlockData);
        return jobs;
    }

    private void addCellToGroup(Map<Long, ArrayList<int[]>> groups,
                                List<LoadBalancerJob> jobs,
                                int effectiveCellsPerJob,
                                boolean setBlockData,
                                int worldX,
                                int worldY,
                                int worldZ,
                                int type) {
        long key = chunkKeyFor(worldX, worldZ);
        ArrayList<int[]> list = groups.computeIfAbsent(key, k -> new ArrayList<>());
        list.add(new int[]{worldX, worldY, worldZ, type});
        if (list.size() >= effectiveCellsPerJob) {
            flushGroup(groups, jobs, key, setBlockData);
        }
    }

    private void flushRemainingGroups(Map<Long, ArrayList<int[]>> groups,
                                      List<LoadBalancerJob> jobs,
                                      boolean setBlockData) {
        for (Long key : new ArrayList<>(groups.keySet())) {
            flushGroup(groups, jobs, key, setBlockData);
        }
        groups.clear();
    }

    private void flushGroup(Map<Long, ArrayList<int[]>> groups,
                            List<LoadBalancerJob> jobs,
                            long chunkKey,
                            boolean setBlockData) {
        ArrayList<int[]> cells = groups.remove(chunkKey);
        if (cells == null || cells.isEmpty()) {
            return;
        }
        int[][] arr = cells.toArray(new int[0][]);
        int cx = (int) (chunkKey >> 32);
        int cz = (int) chunkKey;
        jobs.add(new it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob(
                world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr
        ));
    }

    private long chunkKeyFor(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, 16);
        int cz = Math.floorDiv(worldZ, 16);
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
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
