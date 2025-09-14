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

public class PlaceCellJob implements LoadBalancerJob, RegionTask {

    private final int worldX;
    private final int worldY;
    private final int worldZ;
    private final byte type;
    private final Theme theme;
    private final int height;
    private final int cellSize;
    private final boolean closed;
    private final boolean hollow;
    private final World world;
    private final boolean setBlockData;

    public PlaceCellJob(int worldX, int worldY, int worldZ, byte type, Theme theme, int height, int cellSize,
                        boolean closed, boolean hollow, World world, boolean setBlockData) {
        this.worldX = worldX;
        this.worldY = worldY;
        this.worldZ = worldZ;
        this.type = type;
        this.theme = theme;
        this.height = height;
        this.cellSize = cellSize;
        this.closed = closed;
        this.hollow = hollow;
        this.world = world;
        this.setBlockData = setBlockData;
    }

    @Override
    public void compute() {
        // Ensure all chunks covering this cell are loaded on-demand (no preloading elsewhere)
        int cx1 = Math.floorDiv(worldX, 16);
        int cz1 = Math.floorDiv(worldZ, 16);
        int cx2 = Math.floorDiv(worldX + Math.max(0, cellSize - 1), 16);
        int cz2 = Math.floorDiv(worldZ + Math.max(0, cellSize - 1), 16);
        // Load up to 4 chunks covering the cell footprint
        if (!world.isChunkLoaded(cx1, cz1)) world.getChunkAt(cx1, cz1);
        if (cx2 != cx1 || cz2 != cz1) {
            if (!world.isChunkLoaded(cx2, cz1)) world.getChunkAt(cx2, cz1);
            if (!world.isChunkLoaded(cx1, cz2)) world.getChunkAt(cx1, cz2);
            if (!world.isChunkLoaded(cx2, cz2)) world.getChunkAt(cx2, cz2);
        }
        // Vanilla placement path (thread-safe on server thread)
        // Floor at y=0 relative
        int y = 0;
        for (int x = 0; x < cellSize; x++) {
            for (int z = 0; z < cellSize; z++) {
                Material material = theme.getRandomFloorMaterial();
                setBlock(worldX + x, worldY + y, worldZ + z, material);
            }
        }

        // Middle layers
        for (y = 1; y < height; y++) {
            for (int x = 0; x < cellSize; x++) {
                for (int z = 0; z < cellSize; z++) {
                    if (type == WALL) {
                        // For hollow columns, only edges
                        if (!hollow || x == 0 || x == cellSize - 1 || z == 0 || z == cellSize - 1) {
                            Material material = theme.getRandomWallMaterial();
                            setBlock(worldX + x, worldY + y, worldZ + z, material);
                        }
                    } else {
                        setBlock(worldX + x, worldY + y, worldZ + z, Material.AIR);
                    }
                }
            }
        }

        // Top layer at y=height
        y = height;
        for (int x = 0; x < cellSize; x++) {
            for (int z = 0; z < cellSize; z++) {
                if (closed || type == WALL) {
                    if (!hollow || x == 0 || x == cellSize - 1 || z == 0 || z == cellSize - 1) {
                        Material material = theme.getRandomTopMaterial();
                        setBlock(worldX + x, worldY + y, worldZ + z, material);
                    }
                } else {
                    // Ensure open top for paths when not closed
                    setBlock(worldX + x, worldY + y, worldZ + z, Material.AIR);
                }
            }
        }
    }

    private void setBlock(int x, int y, int z, Material material) {
        Block block = world.getBlockAt(x, y, z);
        if (block.getType() == material) {
            if (!setBlockData) return;
        } else {
            block.setType(material, false); // no physics for speed
        }
        if (setBlockData) {
            CustomBlockData data = new CustomBlockData(block, MazeGeneratorPlugin.plugin);
            NamespacedKey key = new NamespacedKey(MazeGeneratorPlugin.plugin, "block");
            data.set(key, PersistentDataType.STRING, "BLOCK");
        }
    }

    @Override
    public org.bukkit.World getRegionWorld() { return world; }

    @Override
    public int getRegionChunkX() { return Math.floorDiv(worldX, 16); }

    @Override
    public int getRegionChunkZ() { return Math.floorDiv(worldZ, 16); }
}
