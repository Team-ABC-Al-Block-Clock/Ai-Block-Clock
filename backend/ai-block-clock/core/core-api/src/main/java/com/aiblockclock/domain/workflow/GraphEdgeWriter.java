package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.graph.GraphEdgeEntity;
import com.aiblockclock.storage.graph.GraphEdgeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GraphEdgeWriter {
    private final GraphEdgeRepository graphEdgeRepository;

    @Transactional
    public void append(GraphId graphId, List<GraphEdge> edges) {
        graphEdgeRepository.saveAll(GraphConverter.toEdgeEntities(graphId, edges));
    }

    @Transactional
    public void replace(GraphId graphId, List<GraphEdge> edges) {
        List<GraphEdgeEntity> rows = GraphConverter.toEdgeEntities(graphId, edges);
        graphEdgeRepository.deleteMissing(
                graphId.value(), rows.stream().map(GraphEdgeEntity::getEdgeKey).toList());

        for (GraphEdgeEntity row : rows) {
            int updated = graphEdgeRepository.updateEnds(
                    graphId.value(), row.getEdgeKey(), row.getSourceNodeKey(), row.getTargetNodeKey(),
                    row.getBranchWhen());

            if (updated == 0) {
                graphEdgeRepository.save(row);
            }
        }
    }

    @Transactional
    public void removeAll(GraphId graphId) {
        graphEdgeRepository.deleteAllByGraphId(graphId.value());
    }
}
