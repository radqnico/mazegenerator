package it.nicoloscialpi.mazegenerator.dialog;

public record MazeOptions(
    int mazeSizeX,
    int mazeSizeZ,
    int cellSize,
    int wallHeight,
    int layers,
    int stairs,
    int additionalExits,
    float erosion,
    boolean hasExits,
    boolean hasRoom,
    boolean closed,
    boolean hollow,
    boolean layDown,
    String themeName
) {
    public static MazeOptions defaults() {
        return new MazeOptions(
            5, 5,
            1, 3,
            1, 1,
            0, 0f,
            false, false,
            false, false,
            false,
            "desert"
        );
    }
}