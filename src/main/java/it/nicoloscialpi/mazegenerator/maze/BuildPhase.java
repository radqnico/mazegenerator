package it.nicoloscialpi.mazegenerator.maze;

public enum BuildPhase {
    GENERATION("generation"),
    PLACEMENT("placement"),
    CARVING("carving");

    private final String key;

    BuildPhase(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }
}
