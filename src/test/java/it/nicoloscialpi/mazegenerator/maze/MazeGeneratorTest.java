package it.nicoloscialpi.mazegenerator.maze;

import it.nicoloscialpi.mazegenerator.MazeGeneratorPlugin;
import org.junit.jupiter.api.Test;

import static it.nicoloscialpi.mazegenerator.maze.MazeGenerator.*;

class MazeGeneratorTest {

    @Test
    void generateMaze() {
        MazeGenerator mazeGenerator = new MazeGenerator(20, 20);
        byte[][] maze = mazeGenerator.generateMaze(3, 0.05, true, 5, 5, true);
        System.out.println("Last generation took: " + MazeGenerator.getLastGenerationMillis() + "ms");
        printMaze(maze);
    }
}