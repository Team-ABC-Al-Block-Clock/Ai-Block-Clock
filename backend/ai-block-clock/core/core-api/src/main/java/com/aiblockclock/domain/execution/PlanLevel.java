package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.workflow.BranchCondition;
import com.aiblockclock.domain.workflow.GraphEdge;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class PlanLevel {
    private final String prefix;
    private final NodeKey slot;
    private final Numbering numbering;
    private final List<PlannedNode> nodes = new ArrayList<>();
    private final List<PlannedEdge> edges = new ArrayList<>();
    private final Map<NodeKey, Placement> placements = new HashMap<>();
    private final List<PendingBranch> pending = new ArrayList<>();

    private PlanLevel(String prefix, NodeKey slot, Numbering numbering) {
        this.prefix = prefix;
        this.slot = slot;
        this.numbering = numbering;
    }

    static PlanLevel canvas() {
        return new PlanLevel("", null, new Numbering());
    }

    PlanLevel lay(UnitGraph graph) {
        graph.units().forEach(placed -> placed.unit().place(placed.key(), this));
        pending.forEach(this::resolve);
        graph.edges().forEach(this::connect);

        return this;
    }

    List<PlannedNode> nodes() {
        return nodes;
    }

    List<PlannedEdge> edges() {
        return edges;
    }

    void step(NodeKey key, NodeUnit unit) {
        String id = numbering.nextStep();
        nodes.add(new PlannedStep(id, unit.plan(prefix + id, slot == null ? key : slot)));
        placements.put(key, Placement.of(id));
    }

    void branch(NodeKey key, BranchCondition condition) {
        String id = numbering.nextBranch();
        pending.add(new PendingBranch(nodes.size(), id, condition));
        nodes.add(null);
        placements.put(key, Placement.of(id));
    }

    void agent(NodeKey key, AgentUnit unit) {
        if (slot == null) {
            String id = numbering.nextAgent();
            PlanLevel inside = new PlanLevel(id + ".", key, new Numbering()).lay(unit.inside());
            nodes.add(new PlannedAgent(id, unit.agent(), inside.nodes, inside.edges));
            placements.put(key, new Placement(List.of(id), List.of(id),
                    inside.soleTerminal().map(terminal -> id + "." + terminal)));
            return;
        }

        PlanLevel inside = new PlanLevel(prefix, slot, numbering).lay(unit.inside());
        nodes.addAll(inside.nodes);
        edges.addAll(inside.edges);
        placements.put(key, new Placement(inside.entries(), inside.terminals(), inside.soleTerminal()));
    }

    private void resolve(PendingBranch branch) {
        BranchCondition condition = branch.condition();
        String source = condition.source()
                .map(placements::get)
                .flatMap(Placement::reference)
                .orElseThrow(() -> new CoreException(ErrorType.EXECUTION_BRANCH_NOT_SUPPORTED));
        String path = condition.left().substring(condition.left().indexOf('.') + 1);

        nodes.set(branch.index(), new PlannedBranch(branch.id(), source + "." + path, condition.operator(),
                condition.right()));
    }

    private void connect(GraphEdge edge) {
        Placement from = placements.get(edge.sourceNodeKey());
        Placement to = placements.get(edge.targetNodeKey());
        if (from == null || to == null) {
            return;
        }

        for (String source : from.exits()) {
            for (String target : to.entries()) {
                edges.add(new PlannedEdge(source, target, edge.when()));
            }
        }
    }

    private List<String> entries() {
        Set<String> targeted = new HashSet<>();
        edges.forEach(edge -> targeted.add(edge.to()));

        return nodes.stream().map(PlannedNode::id).filter(id -> !targeted.contains(id)).toList();
    }

    private List<String> terminals() {
        Set<String> leaving = new HashSet<>();
        edges.forEach(edge -> leaving.add(edge.from()));

        return nodes.stream().map(PlannedNode::id).filter(id -> !leaving.contains(id)).toList();
    }

    private Optional<String> soleTerminal() {
        List<String> terminals = terminals();

        return terminals.size() == 1 ? Optional.of(terminals.get(0)) : Optional.empty();
    }

    private record Placement(List<String> entries, List<String> exits, Optional<String> reference) {
        static Placement of(String id) {
            return new Placement(List.of(id), List.of(id), Optional.of(id));
        }
    }

    private record PendingBranch(int index, String id, BranchCondition condition) {
    }

    private static final class Numbering {
        private int steps;
        private int branches;
        private int agents;

        String nextStep() {
            return "n" + ++steps;
        }

        String nextBranch() {
            return "b" + ++branches;
        }

        String nextAgent() {
            return "a" + ++agents;
        }
    }
}
