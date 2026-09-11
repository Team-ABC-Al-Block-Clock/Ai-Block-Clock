package com.aiblockclock.domain.workflow;

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
    private final Map<NodeKey, List<NodeKey>> successors;

    private WorkflowGraph(List<GraphNode> nodes, List<GraphEdge> edges) {
        this.nodeIndex = indexNodes(nodes);
        validateEdges(edges, this.nodeIndex);

        this.nodes = List.copyOf(nodes);
        this.edges = List.copyOf(edges);
        this.successors = indexSuccessors(this.nodes, this.edges);
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

    private static Map<NodeKey, List<NodeKey>> indexSuccessors(List<GraphNode> nodes, List<GraphEdge> edges) {
        Map<NodeKey, List<NodeKey>> successors = new HashMap<>();
        nodes.forEach(node -> successors.put(node.key(), new ArrayList<>()));
        edges.forEach(edge -> successors.get(edge.sourceNodeKey()).add(edge.targetNodeKey()));

        return Map.copyOf(successors);
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

    public int chainLength() {
        if (isEmpty()) {
            return 0;
        }

        NodeKey current = soleStart();
        int visited = 1;

        while (hasSuccessor(current)) {
            current = successorOf(current);
            visited++;
        }

        if (visited != nodes.size()) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }

        return visited;
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
        return !successors.get(key).isEmpty();
    }

    private NodeKey successorOf(NodeKey key) {
        List<NodeKey> next = successors.get(key);
        if (next.size() > 1) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_LINEAR,
                    Map.of("nodeKey", key.value()));
        }

        return next.get(0);
    }
}
