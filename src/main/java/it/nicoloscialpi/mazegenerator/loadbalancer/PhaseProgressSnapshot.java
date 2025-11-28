package it.nicoloscialpi.mazegenerator.loadbalancer;

import it.nicoloscialpi.mazegenerator.maze.BuildPhase;

import java.util.EnumMap;
import java.util.Map;

public record PhaseProgressSnapshot(BuildPhase currentPhase, Map<BuildPhase, Double> percentages) {
    public PhaseProgressSnapshot {
        if (percentages == null) {
            percentages = new EnumMap<>(BuildPhase.class);
        }
    }

    public double get(BuildPhase phase) {
        return percentages.getOrDefault(phase, 0.0);
    }
}
