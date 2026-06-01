package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.JobProducer;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.PhaseProgressSnapshot;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import it.nicoloscialpi.mazegenerator.util.SizeParser;
import org.bukkit.Location;
import org.bukkit.World;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static it.nicoloscialpi.mazegenerator.maze.MazeCellType.WALL;

public class MazeStreamPlacer implements JobProducer {

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
    private final int[][] cellHeights;
    private final boolean layDown;
    private final boolean deferWallFill;
    private final long pendingMemoryBudgetBytes;
    private final SpillStorage spillStorage;
    private final long totalCells;
    private boolean carvingDone = false;
    private int fillR = 0;
    private int fillC = 0;
    private final BitSet carved = new BitSet();
    private long filledWalls = 0;
    private long pendingBytes = 0;

    public MazeStreamPlacer(Theme theme,
                            Location location,
                            int height,
                            int cellSize,
                            boolean closed,
                            boolean isHollow,
                            int requestedCellsN,
                            int requestedCellsM,
                            int additionalExits,
                            double erosion,
                            boolean hasRoom,
                            int roomSizeX,
                            int roomSizeZ,
                            boolean hasExits,
                            boolean layDown) {
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
        this.additionalExits = additionalExits;
        this.erosion = erosion;
        this.hasRoom = hasRoom;
        this.roomSizeX = roomSizeX;
        this.roomSizeZ = roomSizeZ;
        this.hasExits = hasExits;
        this.layDown = layDown;
        this.deferWallFill = MazeGeneratorPlugin.plugin.getConfig().getBoolean("defer-wall-fill", false);

        this.generator = new IncrementalMazeGenerator(
                Math.max(1, requestedCellsN), Math.max(1, requestedCellsM),
                additionalExits, erosion, hasRoom, roomSizeX, roomSizeZ, hasExits);
        this.sizeN = generator.getGridSizeN();
        this.sizeM = generator.getGridSizeM();
        this.pendingMemoryBudgetBytes = SizeParser.parseToBytes(
                MazeGeneratorPlugin.plugin.getConfig().getString("placement-max-pending", "8M"),
                8L * 1024L * 1024L
        );
        org.bukkit.configuration.ConfigurationSection diskSpill = MazeGeneratorPlugin.plugin.getConfig().getConfigurationSection("disk-spill");
        boolean diskEnabled = diskSpill != null && diskSpill.getBoolean("enabled", false);
        long diskMaxBytes = SizeParser.parseToBytes(
                diskSpill != null ? diskSpill.getString("max-file-size", "128M") : "128M",
                128L * 1024L * 1024L
        );
        Path spillDir = MazeGeneratorPlugin.plugin.getDataFolder().toPath().resolve("spillover");
        this.spillStorage = new SpillStorage(diskEnabled, diskMaxBytes,
                spillDir.resolve("maze-spill-" + System.currentTimeMillis() + ".yml"));
        this.totalCells = (long) this.sizeN * (long) this.sizeM;
        if (layDown) {
            var sample = TerrainHeightMap.compute(world, baseX, baseZ, baseY, cellSize, this.sizeN, this.sizeM, height);
            this.cellHeights = sample.valid() ? sample.heights() : null;
        } else {
            this.cellHeights = null;
        }
    }

    @Override
    public List<LoadBalancerJob> getJobs() {
        int batch = Math.max(1, MazeGeneratorPlugin.plugin.getConfig().getInt("jobs-batch-cells", 256));
        boolean setBlockData = MazeGeneratorPlugin.plugin.getConfig().getBoolean("set-block-data", false);
        int configuredCellsPerJob = Math.max(1, MazeGeneratorPlugin.plugin.getConfig().getInt("cells-per-job", 16));
        int maxBlocksPerJob = Math.max(64, MazeGeneratorPlugin.plugin.getConfig().getInt("max-blocks-per-job", 2048));
        int blocksPerCell = Math.max(1, cellSize * cellSize * (height + 1));
        int sizePenalty = Math.max(1, blocksPerCell / 64);
        int adaptiveCellsPerJob = Math.max(1, configuredCellsPerJob / sizePenalty);
        int effectiveCellsPerJob = Math.max(1, Math.min(adaptiveCellsPerJob, Math.max(1, maxBlocksPerJob / blocksPerCell)));
        ArrayList<LoadBalancerJob> jobs = new ArrayList<>(Math.max(1, batch / effectiveCellsPerJob));
        HashMap<Long, CellGroupBuffer> groups = new HashMap<>();
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
                    addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, worldYFor(r, c), worldZ, cell.type());
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
                    addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, worldYFor(r, c), worldZ, WALL);
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
            drainSpillFileToJobs(jobs, setBlockData, effectiveCellsPerJob);
            return jobs;
        }

        while (collected < batch && fillR < sizeN) {
            int idx = fillR * sizeM + fillC;
            if (!carved.get(idx)) {
                int r = fillR;
                int c = fillC;
                int worldX = baseX + r * cellSize;
                int worldZ = baseZ + c * cellSize;
                addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, worldYFor(r, c), worldZ, WALL);
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
            if (cell.type() != WALL) {
                carved.set(idx);
            }
            int worldX = baseX + r * cellSize;
            int worldZ = baseZ + c * cellSize;
            addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, worldYFor(r, c), worldZ, cell.type());
            collected++;
            if (collected >= batch) break;
        }
        flushRemainingGroups(groups, jobs, setBlockData);
        drainSpillFileToJobs(jobs, setBlockData, effectiveCellsPerJob);
        return jobs;
    }

    private void addCellToGroup(Map<Long, CellGroupBuffer> groups,
                                List<LoadBalancerJob> jobs,
                                int effectiveCellsPerJob,
                                boolean setBlockData,
                                int worldX,
                                int worldY,
                                int worldZ,
                                int type) {
        long key = chunkKeyFor(worldX, worldZ);
        CellGroupBuffer buffer = groups.computeIfAbsent(key, k -> new CellGroupBuffer());
        buffer.add(worldX, worldY, worldZ, type);
        pendingBytes += CellGroupBuffer.BYTES_PER_CELL;

        if (buffer.cellCount() >= effectiveCellsPerJob) {
            flushGroup(groups, jobs, key, setBlockData);
        } else if (pendingBytes > pendingMemoryBudgetBytes) {
            if (!attemptSpill(groups, key, buffer)) {
                flushGroup(groups, jobs, key, setBlockData);
            }
        }
    }

    private void flushRemainingGroups(Map<Long, CellGroupBuffer> groups,
                                      List<LoadBalancerJob> jobs,
                                      boolean setBlockData) {
        for (Long key : new ArrayList<>(groups.keySet())) {
            flushGroup(groups, jobs, key, setBlockData);
        }
        groups.clear();
    }

    private void flushGroup(Map<Long, CellGroupBuffer> groups,
                            List<LoadBalancerJob> jobs,
                            long chunkKey,
                            boolean setBlockData) {
        CellGroupBuffer buffer = groups.remove(chunkKey);
        if (buffer == null || buffer.cellCount() == 0) {
            return;
        }
        int[][] arr = buffer.toCellArray();
        pendingBytes = Math.max(0, pendingBytes - buffer.bytes());
        buffer.clear();
        int cx = (int) (chunkKey >> 32);
        int cz = (int) chunkKey;
        jobs.add(new BatchPlaceCellsJob(
                world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr
        ));
    }

    private boolean attemptSpill(Map<Long, CellGroupBuffer> groups,
                                 long chunkKey,
                                 CellGroupBuffer buffer) {
        if (spillStorage.writeChunk(chunkKey, buffer)) {
            pendingBytes = Math.max(0, pendingBytes - buffer.bytes());
            groups.remove(chunkKey);
            buffer.clear();
            return true;
        }
        return false;
    }

    private long chunkKeyFor(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, 16);
        int cz = Math.floorDiv(worldZ, 16);
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    private void drainSpillFileToJobs(List<LoadBalancerJob> jobs,
                                      boolean setBlockData,
                                      int effectiveCellsPerJob) {
        spillStorage.closeQuietly();
        Map<Long, CellGroupBuffer> fromDisk = spillStorage.readAll();
        for (var e : fromDisk.entrySet()) {
            long key = e.getKey();
            CellGroupBuffer buffer = e.getValue();
            if (buffer.cellCount() >= effectiveCellsPerJob) {
                flushGroup(fromDisk, jobs, key, setBlockData);
            }
        }
        flushRemainingGroups(fromDisk, jobs, setBlockData);
    }

    @Override
    public double getProgressPercentage() {
        long phase1Total = totalCells;
        long phase2Total = totalCells;
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

    @Override
    public PhaseProgressSnapshot getPhaseProgress() {
        double generationPct = clampPct((double) generator.getEmittedCount() / (double) totalCells * 100.0);
        double placementPct;
        if (deferWallFill) {
            long wallsToFill = Math.max(1, totalCells - carved.cardinality());
            placementPct = clampPct((double) filledWalls / (double) wallsToFill * 100.0);
        } else {
            long phase1DoneApprox = (long) fillR * (long) sizeM + fillC;
            placementPct = clampPct((double) phase1DoneApprox / (double) totalCells * 100.0);
        }

        Map<BuildPhase, Double> map = new EnumMap<>(BuildPhase.class);
        map.put(BuildPhase.GENERATION, generationPct);
        map.put(BuildPhase.PLACEMENT, placementPct);

        BuildPhase current = determineCurrentPhase(generationPct, placementPct);
        return new PhaseProgressSnapshot(current, map);
    }

    private BuildPhase determineCurrentPhase(double generationPct, double placementPct) {
        if (generationPct < 100.0) return BuildPhase.GENERATION;
        if (placementPct < 100.0) return BuildPhase.PLACEMENT;
        return BuildPhase.PLACEMENT;
    }

    private double clampPct(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }

    private int worldYFor(int r, int c) {
        if (cellHeights != null && r >= 0 && r < cellHeights.length && c >= 0 && c < cellHeights[0].length) {
            return cellHeights[r][c];
        }
        return baseY;
    }
}
