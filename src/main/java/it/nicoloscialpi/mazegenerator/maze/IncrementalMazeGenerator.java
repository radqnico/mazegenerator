package it.nicoloscialpi.mazegenerator.maze;

import java.util.*;

/**
 * Incremental maze generator that streams carved cells on demand.
 * Uses an iterative backtracking algorithm over a cell grid where paths reside on odd indices.
 */
public class IncrementalMazeGenerator {

    public static final byte PATH = MazeGenerator.PATH;
    public static final byte WALL = MazeGenerator.WALL;
    public static final byte EXIT = MazeGenerator.EXIT;
    public static final byte HOLE = MazeGenerator.HOLE;
    public static final byte ROOM = MazeGenerator.ROOM;

    private final int sizeN;
    private final int sizeM;
    private final double holeProbability;
    private final boolean hasExits;
    private final int additionalExits;
    private final boolean hasRoom;
    private final int roomHeight;
    private final int roomWidth;

    private final Random random = new Random();

    // Visited bitset for carving (only for grid coordinates)
    private final BitSet visited;
    // Emitted bitset to ensure we place each grid coordinate at most once
    private final BitSet emitted;

    private final Deque<int[]> stack = new ArrayDeque<>();
    private final List<int[]> directions = Arrays.asList(
            new int[]{0, 1}, new int[]{1, 0}, new int[]{0, -1}, new int[]{-1, 0}
    );

    // Outbox buffer of cells to emit to the placer
    private final ArrayDeque<Cell> outbox = new ArrayDeque<>();

    private int exitsToPlace;
    private int exitsPlaced = 0;

    private long emittedCount = 0;
    private final long totalCells;

    public record Cell(int r, int c, byte type) {}

    public IncrementalMazeGenerator(int sizeN, int sizeM,
                                    int additionalExits,
                                    double holeProbability,
                                    boolean hasRoom,
                                    int roomHeight,
                                    int roomWidth,
                                    boolean hasExits) {
        this.sizeN = (sizeN % 2 == 0) ? sizeN + 1 : sizeN;
        this.sizeM = (sizeM % 2 == 0) ? sizeM + 1 : sizeM;
        this.holeProbability = Math.max(0.0, Math.min(1.0, holeProbability));
        this.hasRoom = hasRoom;
        this.roomHeight = Math.max(1, roomHeight);
        this.roomWidth = Math.max(1, roomWidth);
        this.hasExits = hasExits;
        this.additionalExits = Math.max(0, additionalExits);
        this.exitsToPlace = (hasExits ? 1 : 0) + this.additionalExits;

        int cells = this.sizeN * this.sizeM;
        this.visited = new BitSet(cells);
        this.emitted = new BitSet(cells);
        this.totalCells = cells;

        // Initialize: everything is WALL (implicit). Create start point and push to stack
        int startR = randomOdd(this.sizeN);
        int startC = randomOdd(this.sizeM);
        pushPath(startR, startC, PATH);
        stack.push(new int[]{startR, startC});

        if (hasRoom) {
            addCentralRoom();
        }
    }

    public long getTotalCells() { return totalCells; }
    public long getEmittedCount() { return emittedCount; }

    public boolean isComplete() {
        return stack.isEmpty() && outbox.isEmpty();
    }

    public List<Cell> pollNextCells(int max) {
        ArrayList<Cell> result = new ArrayList<>(Math.max(1, max));
        // Fill from outbox first
        while (!outbox.isEmpty() && result.size() < max) {
            Cell cell = outbox.poll();
            result.add(cell);
            emittedCount++;
        }

        while (result.size() < max && !stack.isEmpty()) {
            int[] cell = stack.peek();
            int r = cell[0], c = cell[1];

            Collections.shuffle(directions, random);
            boolean carved = false;
            for (int[] dir : directions) {
                int nr = r + dir[0] * 2;
                int nc = c + dir[1] * 2;
                if (isWithin(nr, nc) && !isVisited(nr, nc)) {
                    int wr = r + dir[0];
                    int wc = c + dir[1];

                    pushPath(wr, wc, PATH); // carve wall between
                    pushPath(nr, nc, PATH); // carve next cell
                    stack.push(new int[]{nr, nc});
                    carved = true;

                    maybeAddHole(nr, nc);
                    maybeOpenExit(nr, nc, dir);
                    break;
                }
            }
            if (!carved) {
                stack.pop();
                maybeAddHole(r, c);
            }

            // Drain outbox up to max
            while (!outbox.isEmpty() && result.size() < max) {
                Cell out = outbox.poll();
                result.add(out);
                emittedCount++;
            }
        }
        return result;
    }

    private void maybeAddHole(int r, int c) {
        if (holeProbability <= 0) return;
        if (random.nextDouble() < holeProbability) {
            Collections.shuffle(directions, random);
            for (int[] d : directions) {
                int nr = r + d[0];
                int nc = c + d[1];
                if (isWithinInner(nr, nc) && !isVisited(nr, nc)) {
                    // Turn a wall cell adjacent into a hole (treated as PATH for placement)
                    pushPath(nr, nc, HOLE);
                    break;
                }
            }
        }
    }

    private void maybeOpenExit(int r, int c, int[] dir) {
        if (!hasExits || exitsPlaced >= exitsToPlace) return;
        // If this carved cell is adjacent to the border in the direction we moved, open the border cell as exit.
        int br = r + dir[0];
        int bc = c + dir[1];
        if (isBorder(br, bc)) {
            if (!isVisited(br, bc)) {
                pushPath(br, bc, EXIT);
                exitsPlaced++;
            }
        }
    }

    private void addCentralRoom() {
        int startR = Math.max(1, (sizeN - roomHeight) / 2);
        int startC = Math.max(1, (sizeM - roomWidth) / 2);
        int endR = Math.min(sizeN - 2, startR + roomHeight - 1);
        int endC = Math.min(sizeM - 2, startC + roomWidth - 1);
        for (int r = startR; r <= endR; r++) {
            for (int c = startC; c <= endC; c++) {
                pushPath(r, c, ROOM);
            }
        }
    }

    private void pushPath(int r, int c, byte type) {
        setVisited(r, c);
        // Emit only once per coordinate
        int idx = index(r, c);
        if (!emitted.get(idx)) {
            emitted.set(idx);
            outbox.add(new Cell(r, c, type));
        }
    }

    private boolean isWithin(int r, int c) {
        return r > 0 && r < sizeN - 1 && c > 0 && c < sizeM - 1;
    }

    private boolean isWithinInner(int r, int c) { // strictly inside borders
        return r > 0 && r < sizeN - 1 && c > 0 && c < sizeM - 1;
    }

    private boolean isBorder(int r, int c) {
        return (r == 0 || r == sizeN - 1 || c == 0 || c == sizeM - 1);
    }

    private int index(int r, int c) { return r * sizeM + c; }
    private boolean isVisited(int r, int c) { return visited.get(index(r, c)); }
    private void setVisited(int r, int c) { visited.set(index(r, c)); }

    private int randomOdd(int limit) {
        int value = random.nextInt(Math.max(1, limit / 2)) * 2 + 1;
        return Math.min(value, limit - 2);
    }
}
