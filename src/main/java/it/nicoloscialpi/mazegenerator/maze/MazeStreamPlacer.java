package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.PlaceCellJob;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import org.bukkit.Location;
import org.bukkit.World;

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

    // Chunk-aware buffering
    private final java.util.Map<Long, java.util.ArrayDeque<int[]>> pendingByChunk = new java.util.HashMap<>(); // key=cxcz, val=list of [r,c,type]

    

    private final boolean forceChunkLoad;

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
                            boolean hasExits,
                            boolean forceChunkLoad) {
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
        this.forceChunkLoad = forceChunkLoad;

        this.generator = new IncrementalMazeGenerator(this.sizeN, this.sizeM,
                additionalExits, erosion, hasRoom, roomSizeX, roomSizeZ, hasExits);
    }

    @Override
    public List<LoadBalancerJob> getJobs() {
        int batch = Math.max(1, MazeGeneratorPlugin.plugin.getConfig().getInt("jobs-batch-cells", 256));
        boolean setBlockData = MazeGeneratorPlugin.plugin.getConfig().getBoolean("set-block-data", false);
        ArrayList<LoadBalancerJob> jobs = new ArrayList<>(batch);

        // 1) Drain pending by loaded chunks first
        drainPendingForLoadedChunks(jobs, batch, setBlockData);
        if (jobs.size() >= batch) return jobs;

        if (deferWallFill) {
            // Carve first for fast initial visual
            if (!carvingDone) {
                var cells = generator.pollNextCells(batch - jobs.size());
                for (IncrementalMazeGenerator.Cell cell : cells) {
                    int r = cell.r();
                    int c = cell.c();
                    carved.set(r * sizeM + c);
                    if (!enqueueIfChunkLoaded(r, c, cell.type(), jobs, setBlockData)) {
                        bufferCell(r, c, cell.type());
                    }
                    if (jobs.size() >= batch) break;
                }
                if (jobs.isEmpty()) {
                    carvingDone = true;
                }
                return jobs;
            }
            // After carving, gradually fill the remaining walls
            while (jobs.size() < batch && fillR < sizeN) {
                int idx = fillR * sizeM + fillC;
                if (!carved.get(idx)) enqueueOrBuffer(fillR, fillC, IncrementalMazeGenerator.WALL, jobs, setBlockData);
                fillC++;
                if (fillC >= sizeM) { fillC = 0; fillR++; }
            }
            return jobs;
        } else {
            // Original: fill first, then carve
            // Phase 1: fill all cells as WALL
            while (jobs.size() < batch && fillR < sizeN) {
                int idx = fillR * sizeM + fillC;
                if (!carved.get(idx)) { // skip if already carved by later phase
                    enqueueOrBuffer(fillR, fillC, IncrementalMazeGenerator.WALL, jobs, setBlockData);
                }
                fillC++;
                if (fillC >= sizeM) { fillC = 0; fillR++; }
            }
            if (jobs.size() >= batch) return jobs;

            // Phase 2: carve
            var cells = generator.pollNextCells(batch - jobs.size());
            for (IncrementalMazeGenerator.Cell cell : cells) {
                int r = cell.r();
                int c = cell.c();
                int idx = r * sizeM + c;
                if (cell.type() != IncrementalMazeGenerator.WALL) {
                    carved.set(idx); // mark carved so pending wall jobs get skipped
                }
                enqueueOrBuffer(r, c, cell.type(), jobs, setBlockData);
                if (jobs.size() >= batch) break;
            }
            return jobs;
        }
    }

    private void drainPendingForLoadedChunks(ArrayList<LoadBalancerJob> jobs, int batch, boolean setBlockData) {
        if (pendingByChunk.isEmpty()) return;
        World w = location.getWorld();
        int chunkLoadBudget = Math.max(0, it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin.plugin.getConfig().getInt("chunk-loads-per-tick", 1));
        java.util.Iterator<java.util.Map.Entry<Long, java.util.ArrayDeque<int[]>>> it = pendingByChunk.entrySet().iterator();
        while (it.hasNext() && jobs.size() < batch) {
            var e = it.next();
            long key = e.getKey();
            int cx = (int) (key >> 32);
            int cz = (int) key;
            if (!w.isChunkLoaded(cx, cz)) {
                if (forceChunkLoad && chunkLoadBudget > 0) {
                    w.getChunkAt(cx, cz);
                    chunkLoadBudget--;
                } else {
                    continue;
                }
            }
            var q = e.getValue();
            while (!q.isEmpty() && jobs.size() < batch) {
                int[] cell = q.peekFirst();
                int r = cell[0], c = cell[1]; byte t = (byte) cell[2];
                int idx = r * sizeM + c;
                if (t == IncrementalMazeGenerator.WALL && carved.get(idx)) {
                    // skip buffered wall if already carved
                    q.pollFirst();
                    continue;
                }
                q.pollFirst();
                addJobForCell(r, c, t, jobs, setBlockData);
                if (t == IncrementalMazeGenerator.WALL) filledWalls++;
            }
            if (q.isEmpty()) it.remove();
        }
    }

    private void bufferCell(int r, int c, byte type) {
        long key = chunkKeyForCell(r, c);
        int idx = r * sizeM + c;
        if (type == IncrementalMazeGenerator.WALL && carved.get(idx)) return; // don't buffer obsolete wall
        pendingByChunk.computeIfAbsent(key, k -> new java.util.ArrayDeque<>()).add(new int[]{r, c, type});
    }

    private void enqueueOrBuffer(int r, int c, byte type, ArrayList<LoadBalancerJob> jobs, boolean setBlockData) {
        // Always enqueue; PlaceCellJob will load chunks on-demand
        addJobForCell(r, c, type, jobs, setBlockData);
    }

    private boolean enqueueIfChunkLoaded(int r, int c, byte type, ArrayList<LoadBalancerJob> jobs, boolean setBlockData) {
        // Always enqueue; job handles loading.
        addJobForCell(r, c, type, jobs, setBlockData);
        return true;
    }

    private void addJobForCell(int r, int c, byte type, ArrayList<LoadBalancerJob> jobs, boolean setBlockData) {
        int worldX = location.getBlockX() + r * cellSize;
        int worldZ = location.getBlockZ() + c * cellSize;
        int worldY = location.getBlockY();
        jobs.add(new PlaceCellJob(worldX, worldY, worldZ, type, theme, height, cellSize, (type == IncrementalMazeGenerator.WALL) || closed, hollow, location.getWorld(), setBlockData));
    }

    private long chunkKeyForCell(int r, int c) {
        int wx = location.getBlockX() + r * cellSize;
        int wz = location.getBlockZ() + c * cellSize;
        int cx = Math.floorDiv(wx, 16);
        int cz = Math.floorDiv(wz, 16);
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
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
