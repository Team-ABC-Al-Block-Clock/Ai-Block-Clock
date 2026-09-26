package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.assertj.core.api.Assertions.tuple;

class ExecutionUsageTest {
    private static final AbcAmount RESERVED = AbcAmount.of(new BigDecimal("6"));
    private static final ChargeRates AT_COST = new ChargeRates(Map.of());
    private static final UserId SUPPLIER = UserId.of(7001L);
    private static final UserId OTHER_SUPPLIER = UserId.of(7002L);

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

    @Test
    void 쓴_만큼을_성공한_단계의_주인에게_정산금으로_나눈다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "0.5", "7001", null),
                owned("n2", "succeeded", "0.25", "7002", null)), AT_COST);

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId, ExecutionSettlement::nodeName, ExecutionSettlement::supplier,
                        ExecutionSettlement::agent, settlement -> settlement.amount().value())
                .containsExactly(
                        tuple("n1", "노드 n1", SUPPLIER, null, new BigDecimal("0.500000")),
                        tuple("n2", "노드 n2", OTHER_SUPPLIER, null, new BigDecimal("0.250000")));
        assertThat(usage.hasSettlement()).isTrue();
        assertBalanced(usage);
    }

    @Test
    void 남의_에이전트면_할증까지_주인의_몫이다() {
        ChargeRates rates = new ChargeRates(Map.of("n1", new BigDecimal("1.30")));

        ExecutionUsage usage = usage(run(null, step("n1", "succeeded", "2")), rates);

        assertThat(usage.settlements())
                .extracting(settlement -> settlement.amount().value())
                .containsExactly(new BigDecimal("2.600000"));
        assertBalanced(usage);
    }

    @Test
    void 한_주인의_단계가_여럿이면_장부에는_한_줄로_합친다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "0.5", "7001", null),
                owned("n2", "succeeded", "0.25", "7002", null),
                owned("n3", "succeeded", "0.25", "7001", null)), AT_COST);

        assertThat(usage.settlements()).hasSize(3);
        assertThat(usage.payouts()).containsExactly(
                entry(SUPPLIER, AbcAmount.of(new BigDecimal("0.75"))),
                entry(OTHER_SUPPLIER, AbcAmount.of(new BigDecimal("0.25"))));
    }

    // 실측 5 + 4 = 9 가 떼어 둔 6 을 넘는다. 둘 다 6/9 로 줄어 3.333333 · 2.666666 이 되고, 남은 0.000001 은 큰 쪽에 붙는다.
    @Test
    void 실측이_떼어_둔_것을_넘으면_모두_실측_몫에_비례해_줄여_나눈다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "5", "7001", null),
                owned("n2", "succeeded", "4", "7002", null)), AT_COST);

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::supplier, settlement -> settlement.amount().value())
                .containsExactly(
                        tuple(SUPPLIER, new BigDecimal("3.333334")),
                        tuple(OTHER_SUPPLIER, new BigDecimal("2.666666")));
        assertBalanced(usage);
    }

    // 셋의 합 1.0000000 은 1.000000 이지만 하나씩 여섯째 자리 아래를 버리면 0.333333 × 3 = 0.999999 다.
    @Test
    void 끝자리를_버려_남은_것은_가장_큰_몫에_붙여_합을_쓴_만큼에_맞춘다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "0.3333333", "7001", null),
                owned("n2", "succeeded", "0.3333333", "7002", null),
                owned("n3", "succeeded", "0.3333334", "7001", null)), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("1");
        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId, settlement -> settlement.amount().value())
                .containsExactly(
                        tuple("n1", new BigDecimal("0.333333")),
                        tuple("n2", new BigDecimal("0.333333")),
                        tuple("n3", new BigDecimal("0.333334")));
        assertBalanced(usage);
    }

    @Test
    void 공급자_탓으로_실패해도_앞서_성공한_단계는_그_주인에게_준다() {
        ExecutionUsage usage = usage(run(failure("supplier"),
                owned("n1", "succeeded", "0.5", "7001", null),
                owned("n2", "failed", "0.3", "7002", null)), AT_COST);

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId, ExecutionSettlement::supplier)
                .containsExactly(tuple("n1", SUPPLIER));
        assertBalanced(usage);
    }

    @Test
    void 플랫폼_탓이면_공급자에게도_주지_않는다() {
        ExecutionUsage usage = usage(
                run(failure("platform"), step("n1", "succeeded", "0.5"), step("n2", "failed", "0.3")), AT_COST);

        assertThat(usage.settlements()).isEmpty();
        assertThat(usage.hasSettlement()).isFalse();
        assertBalanced(usage);
    }

    @Test
    void mock_실행은_쓴_것이_없어_정산할_것도_없다() {
        ExecutionUsage usage = usage(
                run(null, step("n1", "succeeded", "0.0"), step("n2", "succeeded", "0.0")), AT_COST);

        assertThat(usage.hasSettlement()).isFalse();
    }

    @Test
    void 주인을_가리킬_수_없는_단계는_사용자에게_받지도_공급자에게_주지도_않는다() {
        ExecutionUsage usage = usage(run(null,
                owned("n1", "succeeded", "0.5", null, null),
                owned("n2", "succeeded", "0.25", "u_7001", null),
                owned("n3", "succeeded", "1", "7001", null)), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("1");
        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId)
                .containsExactly("n3");
        assertBalanced(usage);
    }

    // 번호가 없으면 배율도 찾을 수 없고 정산 내역의 단계 칸(not null)도 채울 수 없다.
    @Test
    void 단계_번호가_빈_성공_단계도_받지도_주지도_않는다() {
        ExecutionUsage usage = usage(run(null,
                owned(null, "succeeded", "0.5", "7001", null),
                owned("n2", "succeeded", "1", "7001", null)), AT_COST);

        assertThat(usage.used()).isEqualByComparingTo("1");
        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::stepId)
                .containsExactly("n2");
        assertBalanced(usage);
    }

    @Test
    @ExtendWith(OutputCaptureExtension.class)
    void 주인을_알_수_없어_건너뛴_성공_단계는_어느_실행의_어느_단계인지_로그로_남긴다(CapturedOutput output) {
        usage(run(null, owned("n1", "succeeded", "0.5", "u_7001", null), step("n2", "succeeded", "1")), AT_COST);

        assertThat(output.getOut())
                .contains("주인을 알 수 없는 성공 단계라 받지도 주지도 않는다: run=run-1 step=n1 owner=u_7001")
                .doesNotContain("step=n2");
    }

    @Test
    void 에이전트_안의_단계는_그_에이전트를_함께_남긴다() {
        UUID agent = UUID.randomUUID();

        ExecutionUsage usage = usage(run(null, owned("n1", "succeeded", "0.5", "7001", agent)), AT_COST);

        assertThat(usage.settlements())
                .extracting(ExecutionSettlement::agent)
                .containsExactly(AgentId.of(agent));
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

    // 엔진은 우리가 보낸 주인을 단계마다 그대로 돌려준다. 따로 적지 않은 단계는 모두 SUPPLIER 의 것이다.
    private static RunStatus.Node step(String id, String state, String costAbc) {
        return owned(id, state, costAbc, Long.toString(SUPPLIER.value()), null);
    }

    private static RunStatus.Node owned(String id, String state, String costAbc, String owner, UUID agent) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, agent == null ? null : agent.toString(), owner,
                costAbc == null ? null : new BigDecimal(costAbc), null);
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
