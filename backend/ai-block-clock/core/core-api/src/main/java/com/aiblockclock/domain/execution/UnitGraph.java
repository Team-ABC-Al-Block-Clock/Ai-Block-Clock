package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.GraphEdge;

import java.util.List;

public record UnitGraph(List<PlacedUnit> units, List<GraphEdge> edges) {
    public UnitGraph {
        units = List.copyOf(units);
        edges = List.copyOf(edges);
    }
}
