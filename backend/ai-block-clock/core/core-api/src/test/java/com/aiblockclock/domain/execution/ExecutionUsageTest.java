package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionUsageTest {
    private static final AbcAmount RESERVED = AbcAmount.of(new BigDecimal("6"));
    private static final ChargeRates AT_COST = new ChargeRates(Map.of());

    @Test
    void 모두_성공하면_실측_원가의_합만_쓰고_나머지를_돌려준다() {
        ExecutionUsage usage = usage(
                run(null, step("n1", "succeeded", "0.5"), step("n2", "succeeded", "0.25")), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("0.75");
        assertThat(usage.refund()).isEqualByComparingTo("5.25");
        assertThat(usage.hasRefund()).isTrue();
    }

    @Test
    void 단계마다_접수할_때_정한_배율을_곱한다() {
        ChargeRates rates = new ChargeRates(Map.of("n1", BigDecimal.ONE, "n2", new BigDecimal("1.30")));

        ExecutionUsage usage = usage(
                run(null, step("n1", "succeeded", "1"), step("n2", "succeeded", "2")), rates);

        assertThat(usage.used()).isEqualByComparingTo("3.6");
        assertThat(usage.refund()).isEqualByComparingTo("2.4");
    }

    @Test
    void 실패한_단계는_토큰을_썼어도_세지_않는다() {
        ExecutionUsage usage = usage(
                run(failure("supplier"), step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("0.5");
        assertThat(usage.refund()).isEqualByComparingTo("5.5");
    }

    @Test
    void 돌지_않은_단계는_세지_않는다() {
        ExecutionUsage usage = usage(
                run(failure("consumer"), step("n1", "failed", "0.1"), step("n2", "skipped", null)), AT_COST);

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void 플랫폼_탓이면_성공한_단계까지_전부_돌려준다() {
        ExecutionUsage usage = usage(
                run(failure("platform"), step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")), AT_COST);

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void 엔진이_기한_안에_끝을_알리지_않아_끊은_실행은_전부_돌려준다() {
        ExecutionUsage usage = usage(null, AT_COST);

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void mock_실행은_실측_원가가_0_이라_전부_돌려준다() {
        ExecutionUsage usage = usage(
                run(null, step("n1", "succeeded", "0.0"), step("n2", "succeeded", "0.0")), AT_COST);

        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    @Test
    void 실측이_떼어_둔_것을_넘어도_떼어_둔_것까지만_쓴다() {
        ExecutionUsage usage = usage(
                run(null, step("n1", "succeeded", "5"), step("n2", "succeeded", "4")), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("6");
        assertThat(usage.refund()).isZero();
        assertThat(usage.hasRefund()).isFalse();
    }

    @Test
    void 성공했는데_실측이_비어_있으면_0_으로_센다() {
        ExecutionUsage usage = usage(
                run(null, step("n1", "succeeded", null), step("n2", "succeeded", "1")), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("1");
    }

    @Test
    void 사용액은_소수_여섯째_자리에서_반올림한다() {
        ChargeRates rates = new ChargeRates(Map.of("n1", new BigDecimal("1.30")));

        ExecutionUsage usage = usage(run(null, step("n1", "succeeded", "0.0012345")), rates);

        assertThat(usage.used()).isEqualTo(new BigDecimal("0.001605"));
        assertThat(usage.used().add(usage.refund())).isEqualByComparingTo("6");
    }

    @Test
    void 책임을_알_수_없는_실패는_플랫폼_탓으로_보고_전부_돌려준다() {
        ExecutionUsage usage = usage(
                run(failure(null), step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")), AT_COST);

        assertThat(usage.used()).isZero();
        assertThat(usage.refund()).isEqualByComparingTo("6");
    }

    // 운영 코드처럼 실패한 실행만 책임을 판정해 함께 넘긴다.
    private static ExecutionUsage usage(RunStatus run, ChargeRates rates) {
        boolean failed = run == null || !run.isSucceeded();

        return ExecutionUsage.of(RESERVED, run, failed ? ExecutionFailure.judge(run) : null, rates);
    }

    private static RunStatus run(RunStatus.Failure failure, RunStatus.Node... steps) {
        return new RunStatus("run-1", failure == null ? "succeeded" : "failed", null, List.of(steps),
                List.of(), List.of(), null, failure);
    }

    private static RunStatus.Node step(String id, String state, String costAbc) {
        return new RunStatus.Node(id, id, "llm", state, null, null,
                costAbc == null ? null : new BigDecimal(costAbc), null);
    }

    private static RunStatus.Failure failure(String fault) {
        return new RunStatus.Failure("n2", "MODEL_API_ERROR", fault, true);
    }
}
