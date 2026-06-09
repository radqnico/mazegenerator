package it.nicoloscialpi.mazegenerator.maze;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public class SpillStorage {

    private final boolean enabled;
    private final long maxBytes;
    private final Path filePath;
    private BufferedWriter writer;
    private long fileBytes = 0;

    public SpillStorage(boolean enabled, long maxBytes, Path filePath) {
        this.enabled = enabled;
        this.maxBytes = maxBytes;
        this.filePath = filePath;
    }

    public boolean writeChunk(long chunkKey, CellGroupBuffer buffer) {
        if (!enabled || buffer == null || buffer.cellCount() == 0) {
            return false;
        }
        long estimatedAppend = estimateSpillBytes(buffer);
        if (fileBytes + estimatedAppend > maxBytes) {
            return false;
        }
        try {
            ensureWriter();
            int cx = (int) (chunkKey >> 32);
            int cz = (int) chunkKey;
            int[] raw = buffer.raw();
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < buffer.size(); i += 4) {
                int worldX = raw[i];
                int worldY = raw[i + 1];
                int worldZ = raw[i + 2];
                int type = raw[i + 3];
                sb.append("-").append(" [").append(cx).append(", ").append(cz).append(", ")
                  .append(worldX).append(", ").append(worldY).append(", ").append(worldZ)
                  .append(", ").append(type).append("]\n");
            }
            String content = sb.toString();
            writer.write(content);
            fileBytes += content.getBytes(StandardCharsets.UTF_8).length;
            writer.flush();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public Map<Long, CellGroupBuffer> readAll() {
        closeQuietly();
        Map<Long, CellGroupBuffer> fromDisk = new HashMap<>();
        if (!enabled || !Files.exists(filePath)) {
            return fromDisk;
        }
        try {
            for (String raw : Files.readAllLines(filePath, StandardCharsets.UTF_8)) {
                String line = raw.trim();
                if (!line.startsWith("- [")) continue;
                if (!line.endsWith("]")) continue;
                String inner = line.substring(3, line.length() - 1);
                String[] parts = inner.split(",");
                if (parts.length != 6) continue;
                int cx = Integer.parseInt(parts[0].trim());
                int cz = Integer.parseInt(parts[1].trim());
                int worldX = Integer.parseInt(parts[2].trim());
                int worldY = Integer.parseInt(parts[3].trim());
                int worldZ = Integer.parseInt(parts[4].trim());
                int type = Integer.parseInt(parts[5].trim());
                long key = (((long) cx) << 32) ^ (cz & 0xffffffffL);
                CellGroupBuffer buffer = fromDisk.computeIfAbsent(key, k -> new CellGroupBuffer());
                buffer.add(worldX, worldY, worldZ, type);
            }
        } catch (Exception e) {
            System.getLogger("MazeGenerator").log(System.Logger.Level.WARNING, "Failed to read spill file: " + filePath, e);
        } finally {
            try {
                Files.deleteIfExists(filePath);
            } catch (IOException ignored) {
            }
            fileBytes = 0;
        }
        return fromDisk;
    }

    public void closeQuietly() {
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {
            } finally {
                writer = null;
            }
        }
    }

    private void ensureWriter() throws IOException {
        if (writer != null) return;
        Files.createDirectories(filePath.getParent());
        writer = Files.newBufferedWriter(filePath, StandardCharsets.UTF_8);
        writer.write("cells:\n");
        fileBytes = "cells:\n".getBytes(StandardCharsets.UTF_8).length;
    }

    private long estimateSpillBytes(CellGroupBuffer buffer) {
        // Estimate: "- [cx, cz, x, y, z, type]\n" where each number can be up to 11 chars (INT_MAX)
        // Average case: ~40-50 bytes per cell entry, but be conservative
        int cells = buffer.cellCount();
        // Each line: "- [" + chunkX + ", " + chunkZ + ", " + worldX + ", " + worldY + ", " + worldZ + ", " + type + "]\n"
        // Maximum: "- [-2147483648, -2147483648, -2147483648, -2147483648, -2147483648, 255]\n" = ~80 chars
        // But typically much smaller. Use 70 as estimate to be safe
        return (long) 70 * cells;
    }
}
