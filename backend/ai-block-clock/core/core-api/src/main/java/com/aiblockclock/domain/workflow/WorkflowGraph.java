package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Getter
public final class WorkflowGraph {
    private static final WorkflowGraph EMPTY = new WorkflowGraph(List.of(), List.of());

    private final List<GraphNode> nodes;
    private final List<GraphEdge> edges;

    @Getter(AccessLevel.NONE)
    private final Map<NodeKey, GraphNode> nodeIndex;

    @Getter(AccessLevel.NONE)
    private final Map<NodeKey, List<GraphEdge>> outgoing;

    private WorkflowGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodeIndex = indexNodes(nodes);
        validateEdges(edges, this.nodeIndex);

        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.outgoing = indexOutgoing(this.nodes, this.edges);
    }

    public static WorkflowGraph empty() {
        return EMPTY;
    }

    public static WorkflowGraph of(List<GraphNode> nodes, List<GraphEdge> edges) {
        if (nodes.isEmpty() && edges.isEmpty()) {
            return EMPTY;
        }

        return new WorkflowGraph(nodes, edges);
    }

    private static Map<NodeKey, GraphNode> indexNodes(List<GraphNode> nodes) {
        Map<NodeKey, GraphNode> index = new HashMap<>();
        for (GraphNode node : nodes) {
            if (index.put(node.key(), node) != null) {
                throw new CoreException(ErrorType.WORKFLOW_DUPLICATE_NODE_KEY,
                        Map.of("nodeKey", node.key().value()));
            }
        }

        return Map.copyOf(index);
    }

    private static void validateEdges(List<GraphEdge> edges, Map<NodeKey, GraphNode> nodeIndex) {
        Set<EdgeKey> seen = new LinkedHashSet<>();
        for (GraphEdge edge : edges) {
            if (!seen.add(edge.key())) {
                throw new CoreException(ErrorType.WORKFLOW_DUPLICATE_EDGE_KEY,
                        Map.of("edgeKey", edge.key().value()));
            }

            requireNodeExists(nodeIndex, edge.sourceNodeKey(), edge);
            requireNodeExists(nodeIndex, edge.targetNodeKey(), edge);
        }
    }

    private static void requireNodeExists(Map<NodeKey, GraphNode> nodeIndex, NodeKey key, GraphEdge edge) {
        if (!nodeIndex.containsKey(key)) {
            throw new CoreException(ErrorType.WORKFLOW_DANGLING_EDGE,
                    Map.of("edgeKey", edge.key().value(), "nodeKey", key.value()));
        }
    }

    private static Map<NodeKey, List<GraphEdge>> indexOutgoing(List<GraphNode> nodes, List<GraphEdge> edges) {
        Map<NodeKey, List<GraphEdge>> outgoing = new HashMap<>();
        nodes.forEach(node -> outgoing.put(node.key(), new ArrayList<>()));
        edges.forEach(edge -> outgoing.get(edge.sourceNodeKey()).add(edge));

        return Map.copyOf(outgoing);
    }

    public boolean isEmpty() {
        return nodes.isEmpty();
    }

    public int nodeCount() {
        return nodes.size();
    }

    public int edgeCount() {
        return edges.size();
    }

    public Optional<GraphNode> findNode(NodeKey key) {
        return Optional.ofNullable(nodeIndex.get(key));
    }

    public List<NodeReference> agentReferences() {
        return referencesOf(SourceType.AGENT);
    }

    public List<NodeReference> baseNodeReferences() {
        return referencesOf(SourceType.NODE);
    }

    private List<NodeReference> referencesOf(SourceType sourceType) {
        return nodes.stream()
                .filter(node -> node.sourceType() == sourceType)
                .map(GraphNode::reference)
                .toList();
    }

    public int chainLength() {
        return isEmpty() ? 0 : chain().size();
    }

    /** 저장한 연결 순서대로 줄 세운 노드. 한 줄 그래프라 답은 하나뿐이다. */
    public List<GraphNode> nodesInOrder() {
        if (isEmpty()) {
            return List.of();
        }

        return chain().stream().map(nodeIndex::get).toList();
    }

    /** 위 노드 순서를 따라가며 지나친 연결선. 마지막 노드는 나가는 연결선이 없다. */
    public List<GraphEdge> edgesInOrder() {
        if (isEmpty()) {
            return List.of();
        }

        List<NodeKey> walked = chain();

        return walked.stream()
                .limit(walked.size() - 1L)
                .map(this::outgoingEdgeOf)
                .toList();
    }

    private List<NodeKey> chain() {
        NodeKey current = soleStart();
        List<NodeKey> walked = new ArrayList<>();
        walked.add(current);

        while (hasSuccessor(current)) {
            current = outgoingEdgeOf(current).targetNodeKey();
            walked.add(current);
        }

        if (walked.size() != nodes.size()) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }

        return walked;
    }

    private NodeKey soleStart() {
        Set<NodeKey> targeted = targetedNodes();
        List<NodeKey> starts = nodes.stream()
                .map(GraphNode::key)
                .filter(key -> !targeted.contains(key))
                .toList();

        if (starts.isEmpty()) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }

        if (starts.size() > 1) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR,
                    Map.of("startCount", starts.size()));
        }

        return starts.get(0);
    }

    private Set<NodeKey> targetedNodes() {
        Set<NodeKey> targeted = new HashSet<>();
        for (GraphEdge edge : edges) {
            if (!targeted.add(edge.targetNodeKey())) {
                throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR,
                        Map.of("nodeKey", edge.targetNodeKey().value()));
            }
        }

        return targeted;
    }

    private boolean hasSuccessor(NodeKey key) {
        return !outgoing.get(key).isEmpty();
    }

    private GraphEdge outgoingEdgeOf(NodeKey key) {
        List<GraphEdge> next = outgoing.get(key);
        if (next.size() > 1) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR,
                    Map.of("nodeKey", key.value()));
        }

        return next.get(0);
    }
}
