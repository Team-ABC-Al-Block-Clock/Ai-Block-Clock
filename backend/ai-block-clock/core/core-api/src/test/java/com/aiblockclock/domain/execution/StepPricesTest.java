package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.node.NodeOutput;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.GraphFixtures;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.OutputKind;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class StepPricesTest {
    // 캔버스에 노드 하나와 단계가 둘인 에이전트 하나가 놓여, 엔진에는 단계가 셋 간다.
    @Test
    void 에이전트_안의_단계는_제_원가에_그_에이전트의_배율을_곱한_값을_받는다() {
        NodeKey node = NodeKey.of("n1");
        NodeKey agent = NodeKey.of("n2");
        ExecutionPlan plan = ExecutionPlanner.plan(new UnitGraph(List.of(
                new PlacedUnit(node, step("0.02")),
                new PlacedUnit(agent, new AgentUnit(AgentId.newId(), new UnitGraph(List.of(
                        new PlacedUnit(NodeKey.of("s1"), step("0.01")),
                        new PlacedUnit(NodeKey.of("s2"), step("0.03"))),
                        List.of(GraphFixtures.edge("e1", "s1", "s2")))))),
                List.of(GraphFixtures.edge("e1", "n1", "n2"))));
        CostEstimate estimate = CostEstimate.of(List.of(
                item(node, SourceType.NODE, BigDecimal.ONE),
                item(agent, SourceType.AGENT, new BigDecimal("1.30"))));

        StepPrices prices = StepPrices.of(plan, estimate);

        assertThat(prices.of("n1")).get().satisfies(step -> {
            assertThat(step.multiplier()).isEqualByComparingTo("1");
            assertThat(step.price().value()).isEqualByComparingTo("0.02");
        });
        assertThat(prices.of("a1.n1")).get().extracting(step -> step.price().value())
                .isEqualTo(new BigDecimal("0.013000"));
        assertThat(prices.of("a1.n2")).get().extracting(step -> step.price().value())
                .isEqualTo(new BigDecimal("0.039000"));
    }

    // 0.0042 × 1.3 = 0.00546 이고, 0.000005 × 1.3 = 0.0000065 는 여섯째 자리에서 반올림해 0.000007 이다.
    @Test
    void 단가는_장부와_같은_소수_여섯째_자리에서_반올림한다() {
        NodeKey agent = NodeKey.of("n1");
        ExecutionPlan plan = ExecutionPlanner.plan(new UnitGraph(List.of(
                new PlacedUnit(agent, new AgentUnit(AgentId.newId(), new UnitGraph(List.of(
                        new PlacedUnit(NodeKey.of("s1"), step("0.0042")),
                        new PlacedUnit(NodeKey.of("s2"), step("0.000005"))),
                        List.of(GraphFixtures.edge("e1", "s1", "s2")))))),
                List.of()));
        CostEstimate estimate = CostEstimate.of(List.of(item(agent, SourceType.AGENT, new BigDecimal("1.30"))));

        StepPrices prices = StepPrices.of(plan, estimate);

        assertThat(prices.of("a1.n1")).get().extracting(step -> step.price().value())
                .isEqualTo(new BigDecimal("0.005460"));
        assertThat(prices.of("a1.n2")).get().extracting(step -> step.price().value())
                .isEqualTo(new BigDecimal("0.000007"));
    }

    @Test
    void 기록이_없는_단계는_받을_값이_없다() {
        assertThat(new StepPrices(Map.of()).of("n1")).isEmpty();
    }

    private static NodeUnit step(String baseCost) {
        return new NodeUnit(InputSource.PREV, UserId.of(1L), "단계", Prompt.of("프롬프트"), AiModel.defaultModel(),
                new NodeOutput(OutputKind.TEXT, null), List.of(), AbcAmount.of(new BigDecimal(baseCost)));
    }

    private static CostItem item(NodeKey key, SourceType sourceType, BigDecimal multiplier) {
        return new CostItem(key, sourceType, "이름", AbcAmount.of(new BigDecimal("3")), multiplier);
    }
}
