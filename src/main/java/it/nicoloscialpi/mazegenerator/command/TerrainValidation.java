package it.nicoloscialpi.mazegenerator.command;

import it.nicoloscialpi.mazegenerator.maze.TerrainHeightMap;
import org.bukkit.Location;

public final class TerrainValidation {

    private TerrainValidation() {}

    public static boolean canLayDown(Location origin, int sizeX, int sizeZ, int cellSize, int wallHeight) {
        if (origin == null || origin.getWorld() == null) return false;
        var sample = TerrainHeightMap.compute(
                origin.getWorld(),
                origin.getBlockX(),
                origin.getBlockZ(),
                origin.getBlockY(),
                cellSize,
                sizeX,
                sizeZ,
                wallHeight
        );
        return sample.valid();
    }
}
