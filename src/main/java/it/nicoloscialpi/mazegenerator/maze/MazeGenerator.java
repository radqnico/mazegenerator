package it.nicoloscialpi.mazegenerator.maze;

import java.util.*;

public class MazeGenerator {
    public static final byte PATH = 1;
    public static final byte WALL = 0;
    public static final byte EXIT = 2;
    public static final byte HOLE = 3;
    public static final byte ROOM = 4;

    private static long lastGenerationMillis = 0;

    public static long getLastGenerationMillis() {
        return lastGenerationMillis;
    }

    private final int sizeN;
    private final int sizeM;
    private final Random random = new Random();

    public MazeGenerator(int sizeN, int sizeM) {
        // Ensure dimensions are odd
        this.sizeN = (sizeN % 2 == 0) ? sizeN + 1 : sizeN;
        this.sizeM = (sizeM % 2 == 0) ? sizeM + 1 : sizeM;
    }

    public byte[][] generateMaze(int additionalExits, double holeProbability, boolean hasRoom, int roomHeight, int roomWidth, boolean hasExits) {
        long startTime = System.currentTimeMillis();

        byte[][] maze = new byte[sizeN][sizeM];
        for (int i = 0; i < sizeN; i++) {
            for (int j = 0; j < sizeM; j++) {
                maze[i][j] = WALL;
            }
        }

        if (hasRoom) {
            addCentralRoom(maze, roomHeight, roomWidth);
        }

        int startX = randomOdd(sizeN);
        int startY = randomOdd(sizeM);
        maze[startX][startY] = PATH;

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
                    maze[x + dir[0]][y + dir[1]] = PATH;
                    maze[nx][ny] = PATH;
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
            addExits(maze, additionalExits);
        }

        lastGenerationMillis = System.currentTimeMillis() - startTime;
        return maze;
    }

    private void addCentralRoom(byte[][] maze, int roomHeight, int roomWidth) {
        int startRow = (sizeN - roomHeight) / 2;
        int startCol = (sizeM - roomWidth) / 2;

        for (int i = startRow; i < startRow + roomHeight; i++) {
            for (int j = startCol; j < startCol + roomWidth; j++) {
                if (i > 0 && i < sizeN - 1 && j > 0 && j < sizeM - 1) {
                    maze[i][j] = ROOM;
                }
            }
        }
    }

    private void addExits(byte[][] maze, int additionalExits) {
        List<int[]> walls = new ArrayList<>();

        for (int j = 1; j < sizeM - 1; j++) {
            if (maze[1][j] == PATH) walls.add(new int[]{0, j, 1, 0});
            if (maze[sizeN - 2][j] == PATH) walls.add(new int[]{sizeN - 1, j, -1, 0});
        }
        for (int i = 1; i < sizeN - 1; i++) {
            if (maze[i][1] == PATH) walls.add(new int[]{i, 0, 0, 1});
            if (maze[i][sizeM - 2] == PATH) walls.add(new int[]{i, sizeM - 1, 0, -1});
        }

        if (!walls.isEmpty()) {
            int[] exit = walls.get(random.nextInt(walls.size()));
            maze[exit[0]][exit[1]] = EXIT;
            maze[exit[0] + exit[2]][exit[1] + exit[3]] = PATH;
        }

        for (int i = 0; i < additionalExits; i++) {
            if (!walls.isEmpty()) {
                int[] exit = walls.get(random.nextInt(walls.size()));
                maze[exit[0]][exit[1]] = EXIT;
                maze[exit[0] + exit[2]][exit[1] + exit[3]] = PATH;
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
            if (nx > 0 && nx < sizeN - 1 && ny > 0 && ny < sizeM - 1 && maze[nx][ny] == WALL) {
                maze[nx][ny] = HOLE;
                return;
            }
        }
    }

    private boolean isValidCell(byte[][] maze, int x, int y) {
        return x > 0 && x < sizeN - 1 && y > 0 && y < sizeM - 1 && maze[x][y] == WALL;
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

    public static void printMaze(byte[][] maze) {
        for (byte[] row : maze) {
            for (int cell : row) {
                System.out.print(
                        cell == WALL ? "██" :
                                cell == PATH ? "  " :
                                        cell == EXIT ? "EE" :
                                                cell == HOLE ? ".." :
                                                        "RR"
                );
            }
            System.out.println();
        }
    }
}
