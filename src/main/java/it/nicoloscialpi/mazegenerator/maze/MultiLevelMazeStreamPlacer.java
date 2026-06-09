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
import java.util.*;

import static it.nicoloscialpi.mazegenerator.maze.MazeCellType.*;

public class MultiLevelMazeStreamPlacer implements JobProducer {

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
    private final boolean setBlockData;

    private final MultiLevelMazePlanner.MultiLevelPlan plan;
    private final int totalLayers;
    private final int sizeN;
    private final int sizeM;

    private int currentLayer = 0;
    private int fillR = 0;
    private int fillC = 0;
    private boolean fillWallsMode = false;
    private final BitSet[] layerCarved;
    private long filledWalls = 0;
    private long totalCells;

    private final long pendingMemoryBudgetBytes;
    private final SpillStorage spillStorage;

    private static final int LAYER_PHASE_BOTH = 0;
    private static final int LAYER_PHASE_WALLS_ONLY = 1;

    public MultiLevelMazeStreamPlacer(Theme theme,
                                       Location location,
                                       int height,
                                       int cellSize,
                                       boolean closed,
                                       boolean isHollow,
                                       MultiLevelMazePlanner.MultiLevelPlan plan,
                                       boolean setBlockData) {
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
        this.plan = plan;
        this.totalLayers = plan.layers();
        this.sizeN = plan.sizeN();
        this.sizeM = plan.sizeM();
        this.setBlockData = setBlockData;

        this.layerCarved = new BitSet[totalLayers];
        for (int i = 0; i < totalLayers; i++) {
            layerCarved[i] = new BitSet();
        }

        this.totalCells = (long) sizeN * (long) sizeM * totalLayers;

        this.pendingMemoryBudgetBytes = SizeParser.parseToBytes(
                MazeGeneratorPlugin.getInstance().getConfig().getString("placement-max-pending", "8M"),
                8L * 1024L * 1024L
        );
        org.bukkit.configuration.ConfigurationSection diskSpill = MazeGeneratorPlugin.getInstance().getConfig().getConfigurationSection("disk-spill");
        boolean diskEnabled = diskSpill != null && diskSpill.getBoolean("enabled", false);
        long diskMaxBytes = SizeParser.parseToBytes(
                diskSpill != null ? diskSpill.getString("max-file-size", "128M") : "128M",
                128L * 1024L * 1024L
        );
        Path spillDir = MazeGeneratorPlugin.getInstance().getDataFolder().toPath().resolve("spillover");
        this.spillStorage = new SpillStorage(diskEnabled, diskMaxBytes,
                spillDir.resolve("maze-spill-multi-" + System.currentTimeMillis() + ".yml"));
    }

    private byte determineCellType(int layer, int r, int c) {
        byte[][] grid = plan.grids()[layer];
        byte baseType = grid[r][c];

        List<MultiLevelMazePlanner.StairPosition> stairList = (layer < plan.stairPositions().size())
                ? plan.stairPositions().get(layer)
                : null;

        if (stairList != null) {
            for (MultiLevelMazePlanner.StairPosition sp : stairList) {
                if (sp.r() == r && sp.c() == c) {
                    return STAIR_DOWN;
                }
            }
        }

        if (layer > 0) {
            List<MultiLevelMazePlanner.StairPosition> prevStairs = plan.stairPositions().get(layer - 1);
            if (prevStairs != null) {
                for (MultiLevelMazePlanner.StairPosition sp : prevStairs) {
                    if (sp.r() == r && sp.c() == c) {
                        return STAIR_UP;
                    }
                }
            }
        }

        return baseType;
    }

    @Override
    public List<LoadBalancerJob> getJobs() {
        int batch = Math.max(1, MazeGeneratorPlugin.getInstance().getConfig().getInt("jobs-batch-cells", 256));
        int configuredCellsPerJob = Math.max(1, MazeGeneratorPlugin.getInstance().getConfig().getInt("cells-per-job", 16));
        int maxBlocksPerJob = Math.max(64, MazeGeneratorPlugin.getInstance().getConfig().getInt("max-blocks-per-job", 2048));
        int blocksPerCell = Math.max(1, cellSize * cellSize * (height + 1));
        int sizePenalty = Math.max(1, blocksPerCell / 64);
        int adaptiveCellsPerJob = Math.max(1, configuredCellsPerJob / sizePenalty);
        int effectiveCellsPerJob = Math.max(1, Math.min(adaptiveCellsPerJob, Math.max(1, maxBlocksPerJob / blocksPerCell)));
        ArrayList<LoadBalancerJob> jobs = new ArrayList<>(Math.max(1, batch / effectiveCellsPerJob));
        HashMap<Long, CellGroupBuffer> groups = new HashMap<>();
        int collected = 0;

        while (collected < batch && currentLayer < totalLayers) {
            int layer = currentLayer;
            int layerBaseY = baseY + layer * (height + 1);
            byte[][] grid = plan.grids()[layer];

            if (!fillWallsMode) {
                int r = fillR;
                int c = fillC;
                int idx = r * sizeM + c;

                if (!layerCarved[layer].get(idx)) {
                    byte cellType = grid[r][c];
                    byte emitType = cellType;

                    List<MultiLevelMazePlanner.StairPosition> stairList = (layer < plan.stairPositions().size())
                            ? plan.stairPositions().get(layer)
                            : null;

                    if (stairList != null) {
                        for (MultiLevelMazePlanner.StairPosition sp : stairList) {
                            if (sp.r() == r && sp.c() == c) {
                                emitType = STAIR_UP;
                                break;
                            }
                        }
                    }

                    if (emitType != WALL && layer > 0) {
                        List<MultiLevelMazePlanner.StairPosition> prevStairs = plan.stairPositions().get(layer - 1);
                        if (prevStairs != null) {
                            for (MultiLevelMazePlanner.StairPosition sp : prevStairs) {
                                if (sp.r() == r && sp.c() == c) {
                                    emitType = STAIR_DOWN;
                                    break;
                                }
                            }
                        }
                    }

                    int worldX = baseX + c * cellSize;
                    int worldZ = baseZ + r * cellSize;
                    addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, layerBaseY, worldZ, emitType);
                    collected++;

                    if (emitType != WALL) {
                        layerCarved[layer].set(idx);
                    }
                }

                fillC++;
                if (fillC >= sizeM) {
                    fillC = 0;
                    fillR++;
                }

                if (fillR >= sizeN) {
                    fillWallsMode = true;
                    fillR = 0;
                    fillC = 0;
                }
            } else {
                int r = fillR;
                int c = fillC;
                int idx = r * sizeM + c;

                if (!layerCarved[layer].get(idx)) {
                    int worldX = baseX + c * cellSize;
                    int worldZ = baseZ + r * cellSize;
                    addCellToGroup(groups, jobs, effectiveCellsPerJob, setBlockData, worldX, layerBaseY, worldZ, WALL);
                    collected++;
                    filledWalls++;
                }

                fillC++;
                if (fillC >= sizeM) {
                    fillC = 0;
                    fillR++;
                }

                if (fillR >= sizeN) {
                    currentLayer++;
                    fillWallsMode = false;
                    fillR = 0;
                    fillC = 0;
                }
            }

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

        if (buffer.cellCount() >= effectiveCellsPerJob) {
            flushGroup(groups, jobs, key, setBlockData);
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
        buffer.clear();
        int cx = (int) (chunkKey >> 32);
        int cz = (int) chunkKey;
        jobs.add(new BatchPlaceCellsJob(
                world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr
        ));
    }

    private long chunkKeyFor(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, 16);
        int cz = Math.floorDiv(worldZ, 16);
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
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
        if (totalCells <= 0) return 100.0;
        long done = 0;
        for (int i = 0; i < totalLayers; i++) {
            done += layerCarved[i].cardinality();
        }
        done += filledWalls;
        double pct = (double) done / (double) totalCells * 100.0;
        return Math.max(0.0, Math.min(100.0, pct));
    }

    @Override
    public PhaseProgressSnapshot getPhaseProgress() {
        double genPct = (currentLayer >= totalLayers && fillR >= sizeN) ? 100.0 : getProgressPercentage();
        double placePct = genPct;

        Map<BuildPhase, Double> map = new EnumMap<>(BuildPhase.class);
        map.put(BuildPhase.GENERATION, genPct);
        map.put(BuildPhase.PLACEMENT, placePct);

        BuildPhase current = (currentLayer >= totalLayers) ? BuildPhase.PLACEMENT : BuildPhase.GENERATION;
        return new PhaseProgressSnapshot(current, map);
    }
}