package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

class ExecutionUsageTest {
    private static final AbcAmount RESERVED = AbcAmount.of(new BigDecimal("6"));
    private static final UserId SUPPLIER = UserId.of(7001L);
    private static final UserId OTHER_SUPPLIER = UserId.of(7002L);

    // 접수할 때 단계마다 단가를 정해 두었다. 셋을 모두 받아도 떼어 둔 6 을 넘지 않는다.
    private static final StepPrices PRICES = prices("n1", "2", "n2", "1", "n3", "3");

    @Test
    void 모두_성공하면_단계마다_정해_둔_단가의_합만_쓰고_나머지를_돌려준다() {
        ExecutionUsage usage = usage(run(null, step("n1", "succeeded"), step("n2", "succeeded")));

        assertThat(usage.used()).isEqualByComparingTo("3");
        assertThat(usage.refund()).isEqualByComparingTo("3");
        assertThat(usage.hasRefund()).isTrue();
    }

    @Test
    void 엔진이_잰_원가가_얼마든_단가만큼_받는다() {
        ExecutionUsage usage = usage(run(null,
                measured("n1", "succeeded", "0.0001"), measured("n2", "succeeded", "5"), step("n3", "succeeded")));

        assertThat(usage.used()).isEqualByComparingTo("6");
        assertThat(usage.refund()).isZero();
        assertThat(usage.hasRefund()).isFalse();
    }

    @Test
    void 실패한_단계는_토큰을_썼어도_받지_않는다() {
        ExecutionUsage usage = usage(run(failure("supplier"),
                step("n1", "succeeded"), measured("n2", "failed", "0.3")));

        assertThat(usage.used()).isEqualByComparingTo("2");
        assertThat(usage.refund()).isEqualByComparingTo("4");
    }

    @Test
    void 돌지_않은_단계는_받지_않는다() {
        ExecutionUsage usage = usage(run(failure("consumer"), step("n1", "failed"), step("n2", "skipped")));

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void 플랫폼_탓이면_성공한_단계까지_전부_돌려준다() {
        ExecutionUsage usage = usage(run(failure("platform"), step("n1", "succeeded"), step("n2", "failed")));

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void 엔진이_기한_안에_끝을_알리지_않아_끊은_실행은_전부_돌려준다() {
        ExecutionUsage usage = usage(null);

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void mock_실행은_모델을_부르지_않아_받을_것이_없고_전부_돌려준다() {
        ExecutionUsage usage = usage(mockRun(step("n1", "succeeded"), step("n2", "succeeded")));

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
        assertThat(usage.hasSettlement()).isFalse();
    }

    @Test
    void 단가를_남기지_않은_단계는_받지_않는다() {
        ExecutionUsage usage = usage(run(null, step("n1", "succeeded"), step("n9", "succeeded")));

        assertThat(usage.used()).isEqualByComparingTo("2");
        assertThat(usage.settlements()).extracting(ExecutionSettlement::stepId).containsExactly("n1");
    }

    @Test
    void 책임을_알_수_없는_실패는_플랫폼_탓으로_보고_전부_돌려준다() {
        ExecutionUsage usage = usage(run(failure(null), step("n1", "succeeded"), step("n2", "failed")));

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void 쓴_만큼을_성공한_단계의_주인에게_제_단가대로_정산금으로_준다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "7001", null), owned("n2", "succeeded", "7002", null)));

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId, ExecutionSettlement::nodeName, ExecutionSettlement::supplier,
                        ExecutionSettlement::agent, settlement -> settlement.amount().value())
                .containsExactly(
                        tuple("n1", "노드 n1", SUPPLIER, null, new BigDecimal("2.000000")),
                        tuple("n2", "노드 n2", OTHER_SUPPLIER, null, new BigDecimal("1.000000")));
        assertThat(usage.hasSettlement()).isTrue();
        assertBalanced(usage);
    }

    @Test
    void 한_주인의_단계가_여럿이면_장부에는_한_줄로_합친다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "7001", null),
                owned("n2", "succeeded", "7002", null),
                owned("n3", "succeeded", "7001", null)));

        assertThat(usage.settlements()).hasSize(3);
        assertThat(usage.payouts()).containsExactly(
                entry(SUPPLIER, AbcAmount.of(new BigDecimal("5"))),
                entry(OTHER_SUPPLIER, AbcAmount.of(new BigDecimal("1"))));
    }

    // 에이전트 판매가 1 을 한 번에 반올림한 견적과, 단계마다 반올림한 단가 0.333334 + 0.666667 이 끝자리에서 어긋난 경우다.
    // 떼어 둔 1 까지만 받고 두 몫을 같은 비율로 줄여 0.333333 · 0.666666 이 되고, 남은 0.000001 은 큰 쪽에 붙는다.
    @Test
    void 단가의_합이_끝자리_반올림으로_떼어_둔_것을_넘으면_떼어_둔_것까지만_비례해_나눈다() {
        StepPrices prices = prices("n1", "0.333334", "n2", "0.666667");

        ExecutionUsage usage = ExecutionUsage.of(AbcAmount.of(BigDecimal.ONE), run(null,
                owned("n1", "succeeded", "7001", null), owned("n2", "succeeded", "7002", null)), null, prices);

        assertThat(usage.used()).isEqualByComparingTo("1");
        assertThat(usage.refund()).isZero();
        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::supplier, settlement -> settlement.amount().value())
                .containsExactly(
                        tuple(SUPPLIER, new BigDecimal("0.333333")),
                        tuple(OTHER_SUPPLIER, new BigDecimal("0.666667")));
    }

    @Test
    void 공급자_탓으로_실패해도_앞서_성공한_단계는_그_주인에게_준다() {
        ExecutionUsage usage = usage(run(failure("supplier"),
                owned("n1", "succeeded", "7001", null), owned("n2", "failed", "7002", null)));

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId, ExecutionSettlement::supplier)
                .containsExactly(tuple("n1", SUPPLIER));
        assertBalanced(usage);
    }

    @Test
    void 플랫폼_탓이면_공급자에게도_주지_않는다() {
        ExecutionUsage usage = usage(run(failure("platform"), step("n1", "succeeded"), step("n2", "failed")));

        assertThat(usage.settlements()).isEmpty();
        assertThat(usage.hasSettlement()).isFalse();
        assertBalanced(usage);
    }

    @Test
    void 주인을_가리킬_수_없는_단계는_사용자에게_받지도_공급자에게_주지도_않는다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", null, null),
                owned("n2", "succeeded", "u_7001", null),
                owned("n3", "succeeded", "7001", null)));

        assertThat(usage.used()).isEqualByComparingTo("3");
        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId)
                .containsExactly("n3");
        assertBalanced(usage);
    }

    @Test
    void 에이전트_안의_단계는_그_에이전트를_함께_남긴다() {
        UUID agent = UUID.randomUUID();

        ExecutionUsage usage = usage(run(null, owned("n1", "succeeded", "7001", agent)));

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::agent)
                .containsExactly(AgentId.of(agent));
    }

    // 운영 코드처럼 실패한 실행만 책임을 판정해 함께 넘긴다.
    private static ExecutionUsage usage(RunStatus run) {
        boolean failed = run == null || !run.isSucceeded();

        return ExecutionUsage.of(RESERVED, run, failed ? ExecutionFailure.judge(run) : null, PRICES);
    }

    private static StepPrices prices(String... stepAndPrice) {
        Map<String, StepPrice> byStep = new LinkedHashMap<>();

        for (int index = 0; index < stepAndPrice.length; index += 2) {
            byStep.put(stepAndPrice[index],
                    new StepPrice(BigDecimal.ONE, AbcAmount.of(new BigDecimal(stepAndPrice[index + 1]))));
        }

        return new StepPrices(byStep);
    }

    private static RunStatus run(RunStatus.Failure failure, RunStatus.Node... steps) {
        return new RunStatus("run-1", failure == null ? "succeeded" : "failed", null, List.of(steps),
                List.of(), List.of(), null, failure, false, null);
    }

    private static RunStatus mockRun(RunStatus.Node... steps) {
        return new RunStatus("run-1", "succeeded", null, List.of(steps), List.of(), List.of(), null, null, true,
                null);
    }

    // 엔진은 우리가 보낸 주인을 단계마다 그대로 돌려준다. 따로 적지 않은 단계는 모두 SUPPLIER 의 것이다.
    private static RunStatus.Node step(String id, String state) {
        return owned(id, state, Long.toString(SUPPLIER.value()), null);
    }

    private static RunStatus.Node measured(String id, String state, String costUsd) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, null, Long.toString(SUPPLIER.value()),
                new BigDecimal(costUsd), null);
    }

    private static RunStatus.Node owned(String id, String state, String owner, UUID agent) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, agent == null ? null : agent.toString(), owner,
                new BigDecimal("0.003"), null);
    }

    // 떼어 둔 것 = 쓴 만큼 + 돌려줄 만큼, 쓴 만큼 = 정산금의 합. 어떤 실행이든 지켜야 한다.
    private static void assertBalanced(ExecutionUsage usage) {
        BigDecimal settled = usage.settlements().stream()
                .map(settlement -> settlement.amount().value())
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(usage.used().add(usage.refund())).isEqualByComparingTo(RESERVED.value());
        assertThat(settled).isEqualByComparingTo(usage.used());
    }

    private static RunStatus.Failure failure(String fault) {
        return new RunStatus.Failure("n2", "MODEL_API_ERROR", fault, true);
    }
}
