package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.AccessLevel;
import lombok.Getter;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

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

    public int longestPathLength() {
        if (isEmpty()) {
            return 0;
        }

        return depthFrom(walk().get(0));
    }

    public List<GraphNode> nodesInOrder() {
        if (isEmpty()) {
            return List.of();
        }

        return walk().stream().map(nodeIndex::get).toList();
    }

    public List<GraphEdge> edgesInOrder() {
        if (isEmpty()) {
            return List.of();
        }

        return walk().stream().flatMap(key -> branchesOf(key).stream()).toList();
    }

    private List<NodeKey> walk() {
        NodeKey start = soleStart();
        nodes.forEach(this::ensureOutgoingShape);

        List<NodeKey> walked = new ArrayList<>();
        Deque<NodeKey> pending = new ArrayDeque<>();
        pending.push(start);

        while (!pending.isEmpty()) {
            NodeKey current = pending.pop();
            walked.add(current);

            List<GraphEdge> next = branchesOf(current);
            for (int index = next.size() - 1; index >= 0; index--) {
                pending.push(next.get(index).targetNodeKey());
            }
        }

        if (walked.size() != nodes.size()) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_CYCLE);
        }

        nodes.forEach(this::ensureConditionReadsUpstream);

        return walked;
    }

    private int depthFrom(NodeKey key) {
        return 1 + outgoing.get(key).stream()
                .mapToInt(edge -> depthFrom(edge.targetNodeKey()))
                .max()
                .orElse(0);
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
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_TREE,
                    Map.of("startCount", starts.size()));
        }

        return starts.get(0);
    }

    private Set<NodeKey> targetedNodes() {
        Set<NodeKey> targeted = new HashSet<>();
        for (GraphEdge edge : edges) {
            if (!targeted.add(edge.targetNodeKey())) {
                throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_TREE,
                        Map.of("nodeKey", edge.targetNodeKey().value()));
            }
        }

        return targeted;
    }

    private void ensureOutgoingShape(GraphNode node) {
        List<GraphEdge> next = outgoing.get(node.key());

        if (node.sourceType() == SourceType.CONTROL) {
            if (!splitsTrueAndFalse(next)) {
                throw new CoreException(ErrorType.WORKFLOW_BRANCH_EDGES_INVALID,
                        Map.of("nodeKey", node.key().value()));
            }

            return;
        }

        if (next.size() > 1) {
            throw new CoreException(ErrorType.WORKFLOW_GRAPH_NOT_TREE,
                    Map.of("nodeKey", node.key().value()));
        }

        if (next.stream().anyMatch(edge -> edge.when() != null)) {
            throw new CoreException(ErrorType.WORKFLOW_BRANCH_EDGES_INVALID,
                    Map.of("nodeKey", node.key().value()));
        }
    }

    private void ensureConditionReadsUpstream(GraphNode node) {
        if (!(node instanceof ControlNode branch)) {
            return;
        }

        boolean readsUpstreamNode = branch.condition().source()
                .filter(source -> upstreamOf(branch.key()).contains(source))
                .flatMap(this::findNode)
                .filter(source -> source.sourceType() == SourceType.NODE)
                .isPresent();

        if (!readsUpstreamNode) {
            throw new CoreException(ErrorType.WORKFLOW_BRANCH_CONDITION_INVALID,
                    Map.of("nodeKey", branch.key().value(), "left", branch.condition().left()));
        }
    }

    private Set<NodeKey> upstreamOf(NodeKey key) {
        Map<NodeKey, NodeKey> parents = new HashMap<>();
        edges.forEach(edge -> parents.put(edge.targetNodeKey(), edge.sourceNodeKey()));

        Set<NodeKey> upstream = new HashSet<>();
        for (NodeKey current = parents.get(key); current != null; current = parents.get(current)) {
            upstream.add(current);
        }

        return upstream;
    }

    private static boolean splitsTrueAndFalse(List<GraphEdge> next) {
        return next.size() == 2
                && next.stream().map(GraphEdge::when).collect(Collectors.toSet()).equals(Set.of(true, false));
    }

    private List<GraphEdge> branchesOf(NodeKey key) {
        return outgoing.get(key).stream()
                .sorted(Comparator.comparing(edge -> !Boolean.TRUE.equals(edge.when())))
                .toList();
    }
}
