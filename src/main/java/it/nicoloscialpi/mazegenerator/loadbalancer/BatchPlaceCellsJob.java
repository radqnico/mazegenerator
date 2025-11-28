package it.nicoloscialpi.mazegenerator.loadbalancer;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import it.nicoloscialpi.mazegenerator.themes.Theme;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.persistence.PersistentDataType;
import com.jeff_media.customblockdata.CustomBlockData;

import static it.nicoloscialpi.mazegenerator.maze.MazeGenerator.WALL;

/**
 * Places multiple cells within the same chunk in a single job to reduce scheduling overhead.
 */
public class BatchPlaceCellsJob implements LoadBalancerJob, ChunkAwareJob {

    private final World world;
    private final int chunkX;
    private final int chunkZ;
    private final Theme theme;
    private final int height;
    private final int cellSize;
    private final boolean closed;
    private final boolean hollow;
    private final boolean setBlockData;

    // Packed cells: [worldX, worldY, worldZ, type]
    private final int[][] cells;

    public BatchPlaceCellsJob(World world,
                              int chunkX,
                              int chunkZ,
                              Theme theme,
                              int height,
                              int cellSize,
                              boolean closed,
                              boolean hollow,
                              boolean setBlockData,
                              int[][] cells) {
        this.world = world;
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.theme = theme;
        this.height = height;
        this.cellSize = cellSize;
        this.closed = closed;
        this.hollow = hollow;
        this.setBlockData = setBlockData;
        this.cells = cells;
    }

    @Override
    public boolean prepareChunks() {
        return ChunkLoadLimiter.ensureLoaded(world, chunkX, chunkZ);
    }

    @Override
    public void compute() {
        for (int[] c : cells) {
            placeCell(c[0], c[1], c[2], (byte) c[3]);
        }
    }

    private void placeCell(int worldX, int worldY, int worldZ, byte type) {
        // Floor (full fill)
        for (int x = 0; x < cellSize; x++) {
            for (int z = 0; z < cellSize; z++) {
                Material material = theme.getRandomFloorMaterial();
                setBlock(worldX + x, worldY + 0, worldZ + z, material);
            }
        }

        // Middle layers
        for (int y = 1; y < height; y++) {
            if (type == WALL) {
                if (hollow) {
                    // Perimeter only
                    for (int x = 0; x < cellSize; x++) {
                        Material m1 = theme.getRandomWallMaterial();
                        setBlock(worldX + x, worldY + y, worldZ + 0, m1);
                        Material m2 = theme.getRandomWallMaterial();
                        setBlock(worldX + x, worldY + y, worldZ + (cellSize - 1), m2);
                    }
                    for (int z = 1; z < cellSize - 1; z++) {
                        Material m3 = theme.getRandomWallMaterial();
                        setBlock(worldX + 0, worldY + y, worldZ + z, m3);
                        Material m4 = theme.getRandomWallMaterial();
                        setBlock(worldX + (cellSize - 1), worldY + y, worldZ + z, m4);
                    }
                } else {
                    for (int x = 0; x < cellSize; x++) {
                        for (int z = 0; z < cellSize; z++) {
                            Material material = theme.getRandomWallMaterial();
                            setBlock(worldX + x, worldY + y, worldZ + z, material);
                        }
                    }
                }
            } else {
                // Clear space
                for (int x = 0; x < cellSize; x++) {
                    for (int z = 0; z < cellSize; z++) {
                        setBlock(worldX + x, worldY + y, worldZ + z, Material.AIR);
                    }
                }
            }
        }

        // Top layer at y=height
        int yTop = height;
        if (closed || type == WALL) {
            if (hollow) {
                for (int x = 0; x < cellSize; x++) {
                    Material m1 = theme.getRandomTopMaterial();
                    setBlock(worldX + x, worldY + yTop, worldZ + 0, m1);
                    Material m2 = theme.getRandomTopMaterial();
                    setBlock(worldX + x, worldY + yTop, worldZ + (cellSize - 1), m2);
                }
                for (int z = 1; z < cellSize - 1; z++) {
                    Material m3 = theme.getRandomTopMaterial();
                    setBlock(worldX + 0, worldY + yTop, worldZ + z, m3);
                    Material m4 = theme.getRandomTopMaterial();
                    setBlock(worldX + (cellSize - 1), worldY + yTop, worldZ + z, m4);
                }
            } else {
                for (int x = 0; x < cellSize; x++) {
                    for (int z = 0; z < cellSize; z++) {
                        Material material = theme.getRandomTopMaterial();
                        setBlock(worldX + x, worldY + yTop, worldZ + z, material);
                    }
                }
            }
        } else {
            // Open top for paths
            for (int x = 0; x < cellSize; x++) {
                for (int z = 0; z < cellSize; z++) {
                    setBlock(worldX + x, worldY + yTop, worldZ + z, Material.AIR);
                }
            }
        }
    }

    private void setBlock(int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == material) {
            if (!setBlockData) return;
        } else {
            block.setType(material, false);
        }
        if (setBlockData) {
            CustomBlockData data = new CustomBlockData(block, MazeGeneratorPlugin.plugin);
            NamespacedKey key = new NamespacedKey(MazeGeneratorPlugin.plugin, "block");
            data.set(key, PersistentDataType.STRING, "BLOCK");
        }
    }

}

