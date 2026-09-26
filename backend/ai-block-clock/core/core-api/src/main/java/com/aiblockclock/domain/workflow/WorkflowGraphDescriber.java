package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.SourceType;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 그래프에 박힌 참조를 이름과 소유 판정으로 되돌린다.
 *
 * <p>그래프 자체는 UUID 만 들고 있어서 캔버스를 복원할 수 없다. 이름은 {@code node_definition} ·
 * {@code agent} 에 있고, 그 두 테이블은 워크플로우를 읽을 때 손대지 않는다. 여기서 종류별로 한 번씩만
 * 읽어 붙인다.
 */
@Component
public class WorkflowGraphDescriber {
    private final Map<SourceType, NodeSourceDescriber> describers;

    public WorkflowGraphDescriber(List<NodeSourceDescriber> describers) {
        this.describers = describers.stream()
                .collect(Collectors.toUnmodifiableMap(NodeSourceDescriber::sourceType, Function.identity()));
    }

    @Transactional(readOnly = true)
    public Map<NodeKey, PlacedSource> describe(UserId requester, WorkflowGraph graph) {
        if (graph.isEmpty()) {
            return Map.of();
        }

        Map<SourceType, Map<UUID, PlacedSource>> found = describeBySourceType(requester, graph);
        Map<NodeKey, PlacedSource> described = new HashMap<>();
        for (GraphNode node : graph.getNodes()) {
            UUID sourceId = node.reference().referenceId();
            described.put(node.key(), sourceId == null
                    ? new PlacedSource(null, null)
                    : found.getOrDefault(node.sourceType(), Map.of()).getOrDefault(sourceId, PlacedSource.unknown()));
        }

        return Map.copyOf(described);
    }

    private Map<SourceType, Map<UUID, PlacedSource>> describeBySourceType(UserId requester, WorkflowGraph graph) {
        Map<SourceType, Map<UUID, PlacedSource>> found = new EnumMap<>(SourceType.class);
        referencesBySourceType(graph).forEach((sourceType, references) -> {
            NodeSourceDescriber describer = describers.get(sourceType);
            found.put(sourceType, describer == null ? Map.of() : describer.describe(requester, references));
        });

        return found;
    }

    private static Map<SourceType, List<NodeReference>> referencesBySourceType(WorkflowGraph graph) {
        return graph.getNodes().stream().collect(Collectors.groupingBy(
                GraphNode::sourceType,
                () -> new EnumMap<>(SourceType.class),
                Collectors.mapping(GraphNode::reference, Collectors.toList())));
    }
}
