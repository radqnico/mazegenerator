package it.nicoloscialpi.mazegenerator.maze;

public final class MazeCellType {
    public static final byte WALL = 0;
    public static final byte PATH = 1;
    public static final byte EXIT = 2;
    public static final byte HOLE = 3;
    public static final byte ROOM = 4;
    public static final byte STAIR_UP = 5;
    public static final byte STAIR_DOWN = 6;

    private MazeCellType() {}
}
