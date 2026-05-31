package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.util.SizeParser;

import java.util.*;

public class MultiLevelMazePlanner {

    public record StairPosition(int r, int c, int direction) {}
    public record LayerGrid(int layerIndex, byte[][] grid, List<StairPosition> stairDown) {}
    public record MultiLevelPlan(byte[][][] grids, List<List<StairPosition>> stairPositions, int sizeN, int sizeM, int layers, int stairs, boolean sharedGrid) {}

    private final int sizeN;
    private final int sizeM;
    private final int layers;
    private final int stairs;
    private final int additionalExits;
    private final double holeProbability;
    private final boolean hasRoom;
    private final int roomHeight;
    private final int roomWidth;
    private final boolean hasExits;
    private final boolean sharedGrid;
    private final Random random = new Random();

    public MultiLevelMazePlanner(int sizeN, int sizeM, int layers, int stairs,
                                  int additionalExits, double holeProbability,
                                  boolean hasRoom, int roomHeight, int roomWidth,
                                  boolean hasExits, boolean sharedGrid) {
        this.sizeN = SizeParser.interiorToGridSize(Math.max(1, sizeN));
        this.sizeM = SizeParser.interiorToGridSize(Math.max(1, sizeM));
        this.layers = Math.max(1, layers);
        this.stairs = Math.max(1, stairs);
        this.additionalExits = additionalExits;
        this.holeProbability = holeProbability;
        this.hasRoom = hasRoom;
        this.roomHeight = roomHeight;
        this.roomWidth = roomWidth;
        this.hasExits = hasExits;
        this.sharedGrid = sharedGrid;
    }

    public MultiLevelPlan generatePlan() {
        byte[][] baseGrid = generateSingleGrid();

        List<byte[][]> grids = new ArrayList<>(layers);
        for (int i = 0; i < layers; i++) {
            if (sharedGrid) {
                grids.add(baseGrid);
            } else {
                grids.add(generateSingleGrid());
            }
        }

        ensureHolesBetweenLayers(grids);

        List<List<StairPosition>> stairPositions = new ArrayList<>(layers - 1);
        for (int i = 0; i < layers - 1; i++) {
            stairPositions.add(findStairPositions(grids.get(i), grids.get(i + 1)));
        }

        byte[][][] gridsArray = new byte[grids.size()][][];
        for (int i = 0; i < grids.size(); i++) {
            gridsArray[i] = grids.get(i);
        }

        return new MultiLevelPlan(
                gridsArray,
                stairPositions,
                sizeN, sizeM, layers, stairs, sharedGrid
        );
    }

    private byte[][] generateSingleGrid() {
        byte[][] maze = new byte[sizeN][sizeM];
        for (int i = 0; i < sizeN; i++) {
            for (int j = 0; j < sizeM; j++) {
                maze[i][j] = MazeCellType.WALL;
            }
        }

        if (hasRoom) {
            addCentralRoom(maze);
        }

        int startX = randomOdd(sizeN);
        int startY = randomOdd(sizeM);
        maze[startX][startY] = MazeCellType.PATH;

        Deque<int[]> stack = new ArrayDeque<>();
        stack.push(new int[]{startX, startY});

        while (!stack.isEmpty()) {
            int[] cell = stack.peek();
            int x = cell[0], y = cell[1];

            List<int[]> directions = shuffledDirections();
            boolean carved = false;

            for (int[] dir : directions) {
                int nx = x + dir[0] * 2;
                int ny = y + dir[1] * 2;

                if (isValidCell(maze, nx, ny)) {
                    maze[x + dir[0]][y + dir[1]] = MazeCellType.PATH;
                    maze[nx][ny] = MazeCellType.PATH;
                    stack.push(new int[]{nx, ny});
                    carved = true;

                    if (random.nextDouble() < holeProbability) {
                        addHole(maze, stack);
                    }
                    break;
                }
            }

            if (!carved) {
                stack.pop();
                if (random.nextDouble() < holeProbability) {
                    addHole(maze, stack);
                }
            }
        }

        if (hasExits) {
            addExits(maze);
        }

        return maze;
    }

    private List<StairPosition> findStairPositions(byte[][] gridA, byte[][] gridB) {
        Set<IntPair> candidates = new HashSet<>();

        for (int r = 0; r < sizeN; r++) {
            for (int c = 0; c < sizeM; c++) {
                byte typeA = gridA[r][c];
                byte typeB = gridB[r][c];
                boolean isCorridorA = isWalkable(typeA);
                boolean isCorridorB = isWalkable(typeB);

                if (isCorridorA && isCorridorB) {
                    if (hasAdjacentWall(gridA, r, c) || hasAdjacentWall(gridB, r, c)) {
                        candidates.add(new IntPair(r, c));
                    }
                }
            }
        }

        List<IntPair> shuffled = new ArrayList<>(candidates);
        Collections.shuffle(shuffled, random);

        int count = Math.min(stairs, shuffled.size());
        List<StairPosition> result = new ArrayList<>(count);

        int[] dirOptions = new int[]{0, 1, 2, 3};
        for (int i = 0; i < count; i++) {
            IntPair p = shuffled.get(i);
            Collections.shuffle(Arrays.asList(dirOptions), random);
            int bestDir = -1;
            for (int dir : dirOptions) {
                if (hasWallInDirection(gridA, p.r, p.c, dir) || hasWallInDirection(gridB, p.r, p.c, dir)) {
                    bestDir = dir;
                    break;
                }
            }
            result.add(new StairPosition(p.r, p.c, bestDir));
        }

        return result;
    }

    private boolean isWalkable(byte type) {
        return type == MazeCellType.PATH ||
               type == MazeCellType.EXIT ||
               type == MazeCellType.ROOM ||
               type == MazeCellType.HOLE;
    }

    private boolean hasAdjacentWall(byte[][] grid, int r, int c) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr >= 0 && nr < sizeN && nc >= 0 && nc < sizeM) {
                if (grid[nr][nc] == MazeCellType.WALL) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasWallInDirection(byte[][] grid, int r, int c, int dir) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        if (dir < 0 || dir >= 4) return false;
        int[] d = dirs[dir];
        int nr = r + d[0];
        int nc = c + d[1];
        if (nr >= 0 && nr < sizeN && nc >= 0 && nc < sizeM) {
            return grid[nr][nc] == MazeCellType.WALL;
        }
        return false;
    }

    private void addCentralRoom(byte[][] maze) {
        int startRow = (sizeN - roomHeight) / 2;
        int startCol = (sizeM - roomWidth) / 2;

        for (int i = startRow; i < startRow + roomHeight; i++) {
            for (int j = startCol; j < startCol + roomWidth; j++) {
                if (i > 0 && i < sizeN - 1 && j > 0 && j < sizeM - 1) {
                    maze[i][j] = MazeCellType.ROOM;
                }
            }
        }
    }

    private void addExits(byte[][] maze) {
        List<int[]> walls = new ArrayList<>();

        for (int j = 1; j < sizeM - 1; j++) {
            if (maze[1][j] == MazeCellType.PATH) walls.add(new int[]{0, j, 1, 0});
            if (maze[sizeN - 2][j] == MazeCellType.PATH) walls.add(new int[]{sizeN - 1, j, -1, 0});
        }
        for (int i = 1; i < sizeN - 1; i++) {
            if (maze[i][1] == MazeCellType.PATH) walls.add(new int[]{i, 0, 0, 1});
            if (maze[i][sizeM - 2] == MazeCellType.PATH) walls.add(new int[]{i, sizeM - 1, 0, -1});
        }

        if (!walls.isEmpty()) {
            int[] exit = walls.get(random.nextInt(walls.size()));
            maze[exit[0]][exit[1]] = MazeCellType.EXIT;
            maze[exit[0] + exit[2]][exit[1] + exit[3]] = MazeCellType.PATH;
        }

        for (int i = 0; i < additionalExits; i++) {
            if (!walls.isEmpty()) {
                int[] exit = walls.get(random.nextInt(walls.size()));
                maze[exit[0]][exit[1]] = MazeCellType.EXIT;
                maze[exit[0] + exit[2]][exit[1] + exit[3]] = MazeCellType.PATH;
            }
        }
    }

    private void addHole(byte[][] maze, Deque<int[]> stack) {
        if (stack.isEmpty()) return;
        int[] cell = stack.peek();
        int x = cell[0], y = cell[1];

        List<int[]> directions = shuffledDirections();
        for (int[] dir : directions) {
            int nx = x + dir[0];
            int ny = y + dir[1];
            if (nx > 0 && nx < sizeN - 1 && ny > 0 && ny < sizeM - 1 && maze[nx][ny] == MazeCellType.WALL) {
                maze[nx][ny] = MazeCellType.HOLE;
                return;
            }
        }
    }

    private void ensureHolesBetweenLayers(List<byte[][]> grids) {
        if (layers < 2) return;

        for (int layerIdx = 0; layerIdx < layers - 1; layerIdx++) {
            byte[][] lower = grids.get(layerIdx);
            byte[][] upper = grids.get(layerIdx + 1);

            int holeCount = 0;
            for (int r = 0; r < sizeN; r++) {
                for (int c = 0; c < sizeM; c++) {
                    if (lower[r][c] == MazeCellType.HOLE && upper[r][c] == MazeCellType.HOLE) {
                        holeCount++;
                    }
                }
            }

            if (holeCount == 0) {
                forceHoleBetweenLayers(lower, upper);
            }
        }
    }

    private void forceHoleBetweenLayers(byte[][] gridA, byte[][] gridB) {
        List<IntPair> wallCandidates = new ArrayList<>();

        for (int r = 1; r < sizeN - 1; r++) {
            for (int c = 1; c < sizeM - 1; c++) {
                boolean isWallA = gridA[r][c] == MazeCellType.WALL;
                boolean isWallB = gridB[r][c] == MazeCellType.WALL;

                if (!isWallA && !isWallB) continue;

                if (hasAdjacentCorridor(gridA, r, c) || hasAdjacentCorridor(gridB, r, c)) {
                    wallCandidates.add(new IntPair(r, c));
                }
            }
        }

        Collections.shuffle(wallCandidates, random);

        for (IntPair p : wallCandidates) {
            if (gridA[p.r][p.c] == MazeCellType.WALL) {
                gridA[p.r][p.c] = MazeCellType.HOLE;
            }
            if (gridB[p.r][p.c] == MazeCellType.WALL) {
                gridB[p.r][p.c] = MazeCellType.HOLE;
            }
            if (gridA[p.r][p.c] == MazeCellType.HOLE || gridB[p.r][p.c] == MazeCellType.HOLE) {
                return;
            }
        }
    }

    private boolean hasAdjacentCorridor(byte[][] grid, int r, int c) {
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (int[] d : dirs) {
            int nr = r + d[0];
            int nc = c + d[1];
            if (nr >= 0 && nr < sizeN && nc >= 0 && nc < sizeM) {
                if (isWalkable(grid[nr][nc])) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isValidCell(byte[][] maze, int x, int y) {
        return x > 0 && x < sizeN - 1 && y > 0 && y < sizeM - 1 && maze[x][y] == MazeCellType.WALL;
    }

    private int randomOdd(int limit) {
        int value = random.nextInt(limit / 2) * 2 + 1;
        return Math.min(value, limit - 2);
    }

    private List<int[]> shuffledDirections() {
        List<int[]> directions = Arrays.asList(
                new int[]{0, 1},
                new int[]{1, 0},
                new int[]{0, -1},
                new int[]{-1, 0}
        );
        Collections.shuffle(directions, random);
        return directions;
    }

    private static class IntPair {
        final int r, c;
        IntPair(int r, int c) { this.r = r; this.c = c; }
        @Override public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof IntPair)) return false;
            IntPair that = (IntPair) o;
            return this.r == that.r && this.c == that.c;
        }
        @Override public int hashCode() { return Objects.hash(r, c); }
    }
}