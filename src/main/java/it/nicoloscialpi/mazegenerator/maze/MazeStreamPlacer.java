package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.loadbalancer.LoadBalancerJob;
import it.nicoloscialpi.mazegenerator.loadbalancer.PhaseProgressSnapshot;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import it.nicoloscialpi.mazegenerator.util.SizeParser;
import org.bukkit.Location;
import org.bukkit.World;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final long pendingMemoryBudgetBytes;
    private final boolean diskSpillEnabled;
    private final long diskSpillMaxBytes;
    private final Path spillFilePath;
    private final long totalCells;
    private boolean carvingDone = false;
    private int fillR = 0;
    private int fillC = 0;
    private final BitSet carved = new BitSet();
    private long filledWalls = 0;
    private long pendingBytes = 0;
    private BufferedWriter spillWriter;
    private long spillFileBytes = 0;

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
        this.pendingMemoryBudgetBytes = SizeParser.parseToBytes(
                MazeGeneratorPlugin.plugin.getConfig().getString("placement-max-pending", "8M"),
                8L * 1024L * 1024L
        );
        org.bukkit.configuration.ConfigurationSection diskSpill = MazeGeneratorPlugin.plugin.getConfig().getConfigurationSection("disk-spill");
        this.diskSpillEnabled = diskSpill != null && diskSpill.getBoolean("enabled", false);
        this.diskSpillMaxBytes = SizeParser.parseToBytes(
                diskSpill != null ? diskSpill.getString("max-file-size", "128M") : "128M",
                128L * 1024L * 1024L
        );
        Path spillDir = MazeGeneratorPlugin.plugin.getDataFolder().toPath().resolve("spillover");
        try {
            Files.createDirectories(spillDir);
        } catch (IOException ignored) {
        }
        this.spillFilePath = spillDir.resolve("maze-spill-" + System.currentTimeMillis() + ".yml");
        this.totalCells = (long) this.sizeN * (long) this.sizeM;

        this.generator = new IncrementalMazeGenerator(this.sizeN, this.sizeM,
                additionalExits, erosion, hasRoom, roomSizeX, roomSizeZ, hasExits);
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
        jobs.add(new it.nicoloscialpi.mazegenerator.loadbalancer.BatchPlaceCellsJob(
                world, cx, cz, theme, height, cellSize, closed, hollow, setBlockData, arr
        ));
    }

    private boolean attemptSpill(Map<Long, CellGroupBuffer> groups,
                                 long chunkKey,
                                 CellGroupBuffer buffer) {
        if (!diskSpillEnabled || buffer.cellCount() == 0) {
            return false;
        }
        long estimatedAppend = estimateSpillBytes(buffer);
        if (spillFileBytes + estimatedAppend > diskSpillMaxBytes) {
            return false;
        }
        try {
            ensureSpillWriter();
            int cx = (int) (chunkKey >> 32);
            int cz = (int) chunkKey;
            for (int i = 0; i < buffer.size; i += 4) {
                int worldX = buffer.data[i];
                int worldY = buffer.data[i + 1];
                int worldZ = buffer.data[i + 2];
                int type = buffer.data[i + 3];
                String line = "- [" + cx + ", " + cz + ", " + worldX + ", " + worldY + ", " + worldZ + ", " + type + "]\n";
                spillWriter.write(line);
                spillFileBytes += line.getBytes(StandardCharsets.UTF_8).length;
            }
            spillWriter.flush();
            pendingBytes = Math.max(0, pendingBytes - buffer.bytes());
            groups.remove(chunkKey);
            buffer.clear();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private long estimateSpillBytes(CellGroupBuffer buffer) {
        // Approximate YAML line length per cell
        int perLine = 50;
        return (long) perLine * buffer.cellCount();
    }

    private void ensureSpillWriter() throws IOException {
        if (spillWriter != null) {
            return;
        }
        spillWriter = Files.newBufferedWriter(spillFilePath, StandardCharsets.UTF_8);
        spillWriter.write("cells:\n");
        spillFileBytes = "cells:\n".getBytes(StandardCharsets.UTF_8).length;
    }

    private long chunkKeyFor(int worldX, int worldZ) {
        int cx = Math.floorDiv(worldX, 16);
        int cz = Math.floorDiv(worldZ, 16);
        return (((long) cx) << 32) ^ (cz & 0xffffffffL);
    }

    private void drainSpillFileToJobs(List<LoadBalancerJob> jobs,
                                      boolean setBlockData,
                                      int effectiveCellsPerJob) {
        if (spillWriter != null) {
            try {
                spillWriter.close();
            } catch (IOException ignored) {
            }
            spillWriter = null;
        }
        if (!Files.exists(spillFilePath)) {
            return;
        }
        Map<Long, CellGroupBuffer> fromDisk = new HashMap<>();
        try {
            List<String> lines = Files.readAllLines(spillFilePath, StandardCharsets.UTF_8);
            for (String raw : lines) {
                String line = raw.trim();
                if (!line.startsWith("- [")) {
                    continue;
                }
                String inner = line.substring(3, line.length() - 1);
                String[] parts = inner.split(",");
                if (parts.length != 6) {
                    continue;
                }
                int cx = Integer.parseInt(parts[0].trim());
                int cz = Integer.parseInt(parts[1].trim());
                int worldX = Integer.parseInt(parts[2].trim());
                int worldY = Integer.parseInt(parts[3].trim());
                int worldZ = Integer.parseInt(parts[4].trim());
                int type = Integer.parseInt(parts[5].trim());
                long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                CellGroupBuffer buffer = fromDisk.computeIfAbsent(key, k -> new CellGroupBuffer());
                buffer.add(worldX, worldY, worldZ, type);
                if (buffer.cellCount() >= effectiveCellsPerJob) {
                    flushGroup(fromDisk, jobs, key, setBlockData);
                }
            }
            flushRemainingGroups(fromDisk, jobs, setBlockData);
        } catch (Exception ignored) {
        } finally {
            try {
                Files.deleteIfExists(spillFilePath);
            } catch (IOException ignored) {
            }
            spillFileBytes = 0;
        }
    }

    private static final class CellGroupBuffer {
        private static final int BYTES_PER_CELL = Integer.BYTES * 4;
        private int[] data = new int[16];
        private int size = 0;

        void add(int worldX, int worldY, int worldZ, int type) {
            ensureCapacity(size + 4);
            data[size++] = worldX;
            data[size++] = worldY;
            data[size++] = worldZ;
            data[size++] = type;
        }

        int cellCount() {
            return size / 4;
        }

        int[][] toCellArray() {
            int cells = cellCount();
            int[][] arr = new int[cells][4];
            int idx = 0;
            for (int i = 0; i < cells; i++) {
                arr[i][0] = data[idx++];
                arr[i][1] = data[idx++];
                arr[i][2] = data[idx++];
                arr[i][3] = data[idx++];
            }
            return arr;
        }

        int bytes() {
            return cellCount() * BYTES_PER_CELL;
        }

        void clear() {
            size = 0;
        }

        private void ensureCapacity(int wanted) {
            if (wanted <= data.length) {
                return;
            }
            int newLen = Math.max(data.length * 2, wanted);
            int[] next = new int[newLen];
            System.arraycopy(data, 0, next, 0, size);
            data = next;
        }
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

        Map<BuildPhase, Double> map = new java.util.EnumMap<>(BuildPhase.class);
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
}
