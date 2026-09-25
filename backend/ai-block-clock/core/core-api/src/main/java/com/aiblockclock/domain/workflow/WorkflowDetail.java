package com.aiblockclock.domain.workflow;

import java.util.Map;

/** 워크플로우와, 그 그래프가 가리키는 원본을 푼 것. 캔버스를 되살리는 데 필요한 전부다. */
public record WorkflowDetail(Workflow workflow, Map<NodeKey, PlacedSource> sources) {
    public PlacedSource sourceOf(NodeKey key) {
        return sources.getOrDefault(key, PlacedSource.unknown());
    }
}
