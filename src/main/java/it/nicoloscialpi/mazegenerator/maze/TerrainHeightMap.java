package it.nicoloscialpi.mazegenerator.maze;

import org.bukkit.World;

public final class TerrainHeightMap {

    private TerrainHeightMap() {}

    public static TerrainSample compute(World world,
                                        int baseX,
                                        int baseZ,
                                        int baseY,
                                        int cellSize,
                                        int sizeN,
                                        int sizeM,
                                        int wallHeight) {
        int[][] heights = new int[sizeN][sizeM];
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight() - 1;
        int effectiveMaxY = Math.min(maxY, baseY + wallHeight);
        boolean hasGround = false;

        // Initial sampling at cell center
        for (int r = 0; r < sizeN; r++) {
            for (int c = 0; c < sizeM; c++) {
                int worldX = baseX + r * cellSize + Math.max(0, cellSize / 2);
                int worldZ = baseZ + c * cellSize + Math.max(0, cellSize / 2);
                int h = world.getHighestBlockYAt(worldX, worldZ);
                if (h > world.getMinHeight()) {
                    hasGround = true;
                }
                h = Math.min(Math.max(minY, h), effectiveMaxY);
                heights[r][c] = h;
            }
        }

        if (!hasGround) {
            return new TerrainSample(null, false);
        }

        // Smooth to enforce walkable steps (max diff 1)
        int iterations = sizeN + sizeM;
        for (int iter = 0; iter < iterations; iter++) {
            for (int r = 0; r < sizeN; r++) {
                for (int c = 0; c < sizeM; c++) {
                    int h = heights[r][c];
                    if (r > 0) h = clampStep(h, heights[r - 1][c]);
                    if (c > 0) h = clampStep(h, heights[r][c - 1]);
                    if (r < sizeN - 1) h = clampStep(h, heights[r + 1][c]);
                    if (c < sizeM - 1) h = clampStep(h, heights[r][c + 1]);
                    h = Math.min(Math.max(minY, h), effectiveMaxY);
                    heights[r][c] = h;
                }
            }
        }
        return new TerrainSample(heights, true);
    }

    private static int clampStep(int current, int neighbor) {
        if (current > neighbor + 1) return neighbor + 1;
        if (current < neighbor - 1) return neighbor - 1;
        return current;
    }

    public record TerrainSample(int[][] heights, boolean valid) {}
}
