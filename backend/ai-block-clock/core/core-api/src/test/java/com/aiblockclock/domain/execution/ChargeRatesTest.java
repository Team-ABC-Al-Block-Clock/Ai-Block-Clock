package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.workflow.InputSource;
import com.aiblockclock.enums.workflow.SourceType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ChargeRatesTest {
    // 캔버스에 노드 하나와 단계가 둘인 에이전트 하나가 놓여, 엔진에는 단계가 셋 간다.
    @Test
    void 에이전트_안의_단계는_모두_그_에이전트의_배율을_따른다() {
        NodeKey node = NodeKey.of("n1");
        NodeKey agent = NodeKey.of("n2");
        ExecutionPlan plan = new ExecutionPlan(List.of(prompt(node), prompt(agent), prompt(agent)));
        CostEstimate estimate = CostEstimate.of(List.of(
                item(node, SourceType.NODE, BigDecimal.ONE),
                item(agent, SourceType.AGENT, new BigDecimal("1.30"))));

        ChargeRates rates = ChargeRates.of(plan, estimate);

        assertThat(rates.of("n1")).isEqualByComparingTo("1");
        assertThat(rates.of("n2")).isEqualByComparingTo("1.30");
        assertThat(rates.of("n3")).isEqualByComparingTo("1.30");
    }

    @Test
    void 기록이_없는_단계는_원가만_받는다() {
        assertThat(new ChargeRates(Map.of()).of("n1")).isEqualByComparingTo("1");
    }

    private static PlannedPrompt prompt(NodeKey placedKey) {
        return new PlannedPrompt(placedKey, "단계", UserId.of(1L), Prompt.of("프롬프트"), InputSource.PREV, List.of());
    }

    private static CostItem item(NodeKey key, SourceType sourceType, BigDecimal multiplier) {
        return new CostItem(key, sourceType, "이름", AbcAmount.of(new BigDecimal("3")), multiplier);
    }
}
