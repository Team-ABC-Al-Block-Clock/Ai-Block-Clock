package com.aiblockclock.domain.workflow;

import com.aiblockclock.enums.workflow.ControlType;
import com.aiblockclock.enums.workflow.InputFieldType;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.IoType;
import com.aiblockclock.enums.workflow.ResultFormat;
import com.aiblockclock.enums.workflow.ResultShape;
import com.aiblockclock.enums.workflow.SourceType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class GraphFixtures {
    private GraphFixtures() {
    }

    public static GraphNode node(String key) {
        return new BaseNode(NodeKey.of(key), deterministicId(key), InputSource.defaultSource());
    }

    public static AgentNode agent(String key, UUID agentId, UUID versionId) {
        return new AgentNode(NodeKey.of(key), agentId, versionId, InputSource.defaultSource());
    }

    public static NodePlacement placement(String key, UUID sourceId) {
        return placement(key, sourceId, InputSource.defaultSource());
    }

    public static NodePlacement placement(String key, UUID sourceId, InputSource inputSource) {
        return new NodePlacement(NodeKey.of(key), SourceType.NODE, sourceId, inputSource,
                null, null, null, null, null, null, null);
    }

    public static IoNode start(String key) {
        return new IoNode(NodeKey.of(key), IoType.START, List.of(field("brand_name")), null, null);
    }

    public static IoNode freeStart(String key) {
        return new IoNode(NodeKey.of(key), IoType.START, List.of(), null, null);
    }

    public static IoNode end(String key) {
        return new IoNode(NodeKey.of(key), IoType.END, List.of(), ResultShape.DOCUMENT, ResultFormat.PDF);
    }

    public static InputField field(String key) {
        return new InputField(key, "브랜드명", InputFieldType.TEXT, true, List.of());
    }

    public static NodePlacement startPlacement(String key, List<InputField> fields) {
        return new NodePlacement(NodeKey.of(key), SourceType.IO, null, InputSource.defaultSource(),
                null, null, null, IoType.START, fields, null, null);
    }

    public static NodePlacement endPlacement(String key, ResultShape shape, ResultFormat format) {
        return new NodePlacement(NodeKey.of(key), SourceType.IO, null, InputSource.defaultSource(),
                null, null, null, IoType.END, null, shape, format);
    }

    public static ControlNode branch(String key) {
        return branch(key, "n1.competition_level");
    }

    public static ControlNode branch(String key, String left) {
        return new ControlNode(NodeKey.of(key), ControlType.BRANCH,
                new BranchCondition(left, ConditionOperator.EQ, "high"));
    }

    public static GraphEdge edge(String key, String source, String target) {
        return new GraphEdge(EdgeKey.of(key), NodeKey.of(source), NodeKey.of(target), null);
    }

    public static GraphEdge branchEdge(String key, String source, String target, boolean when) {
        return new GraphEdge(EdgeKey.of(key), NodeKey.of(source), NodeKey.of(target), when);
    }

    public static WorkflowGraph branched() {
        return WorkflowGraph.of(
                List.of(node("n1"), branch("br1"), node("n2"), node("n3"), node("n4")),
                List.of(edge("e1", "n1", "br1"),
                        branchEdge("e2", "br1", "n2", true),
                        branchEdge("e3", "br1", "n4", false),
                        edge("e4", "n2", "n3")));
    }

    public static WorkflowGraph straightLine() {
        return chainOf(3);
    }

    public static WorkflowGraph chainOf(int length) {
        List<GraphNode> nodes = new ArrayList<>();
        List<GraphEdge> edges = new ArrayList<>();
        for (int i = 1; i <= length; i++) {
            nodes.add(node("n" + i));
            if (i > 1) {
                edges.add(edge("e" + i, "n" + (i - 1), "n" + i));
            }
        }

        return WorkflowGraph.of(nodes, edges);
    }

    public static WorkflowGraph withAgent(UUID agentId, UUID versionId) {
        return WorkflowGraph.of(
                List.of(node("n1"), agent("n2", agentId, versionId), node("n3")),
                List.of(edge("e1", "n1", "n2"), edge("e2", "n2", "n3")));
    }

    private static UUID deterministicId(String key) {
        return UUID.nameUUIDFromBytes(key.getBytes(StandardCharsets.UTF_8));
    }
}
