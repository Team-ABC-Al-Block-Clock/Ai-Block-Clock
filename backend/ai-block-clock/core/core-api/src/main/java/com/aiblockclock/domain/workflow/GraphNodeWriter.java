package com.aiblockclock.domain.workflow;

import com.aiblockclock.storage.graph.GraphNodeEntity;
import com.aiblockclock.storage.graph.GraphNodeRepository;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
public class GraphNodeWriter {
    private final GraphNodeRepository graphNodeRepository;

    @Transactional
    public void append(GraphId graphId, List<GraphNode> nodes) {
        graphNodeRepository.saveAll(GraphConverter.toNodeEntities(graphId, nodes));
    }

    @Transactional
    public void replace(GraphId graphId, List<GraphNode> nodes) {
        List<GraphNodeEntity> rows = GraphConverter.toNodeEntities(graphId, nodes);
        graphNodeRepository.deleteMissing(
                graphId.value(), rows.stream().map(GraphNodeEntity::getNodeKey).toList());

        for (GraphNodeEntity row : rows) {
            int updated = graphNodeRepository.updatePlacement(graphId.value(), row.getNodeKey(),
                    row.getSourceType(), row.getReferenceId(), row.getVersionId(), row.getInputSource(),
                    row.getControlType(), row.getBranchCondition());

            if (updated == 0) {
                graphNodeRepository.save(row);
            }
        }
    }

    @Transactional
    public void removeAll(GraphId graphId) {
        graphNodeRepository.deleteAllByGraphId(graphId.value());
    }
}
