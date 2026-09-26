package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentRunOutcomesTest {
    private static final UserId BUYER = UserId.of(8001L);
    private static final String SELLER = "8002";
    private static final String OTHER_SELLER = "8003";
    private static final UUID REPORT = UUID.fromString("2a6f0c1e-3b4d-4e5f-8a9b-0c1d2e3f4a5b");
    private static final UUID LETTER = UUID.fromString("7c8d9e0f-1a2b-4c3d-9e4f-5a6b7c8d9e0f");

    @Test
    void 성공한_실행이면_쓰인_에이전트마다_성공을_한_번_센다() {
        AgentRunOutcomes outcomes = outcomesOf(run(null,
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "succeeded", "0.4", OTHER_SELLER, LETTER)));

        assertThat(outcomes.succeeded()).containsExactlyInAnyOrder(AgentId.of(REPORT), AgentId.of(LETTER));
        assertThat(outcomes.failed()).isEmpty();
    }

    @Test
    void 에이전트_안의_단계가_여럿이어도_한_번만_센다() {
        AgentRunOutcomes outcomes = outcomesOf(run(null,
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "succeeded", "0.4", SELLER, REPORT),
                step("n3", "succeeded", "0.3", SELLER, REPORT)));

        assertThat(outcomes.succeeded()).containsExactly(AgentId.of(REPORT));
    }

    @Test
    void 성공률에_넣는_공급자_탓_실패는_그_에이전트의_실패로_세고_앞서_끝난_에이전트는_성공으로_센다() {
        AgentRunOutcomes outcomes = outcomesOf(run(failure("n2", "supplier", true),
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "failed", "0.1", OTHER_SELLER, LETTER)));

        assertThat(outcomes.succeeded()).containsExactly(AgentId.of(REPORT));
        assertThat(outcomes.failed()).containsExactly(AgentId.of(LETTER));
    }

    @Test
    void 에이전트_안의_앞_단계가_성공했어도_뒤_단계가_공급자_탓으로_실패하면_실패로만_센다() {
        AgentRunOutcomes outcomes = outcomesOf(run(failure("n2", "supplier", true),
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "failed", "0.1", SELLER, REPORT)));

        assertThat(outcomes.succeeded()).isEmpty();
        assertThat(outcomes.failed()).containsExactly(AgentId.of(REPORT));
    }

    @Test
    void 성공률에_넣지_말라는_공급자_오류면_실패한_에이전트를_성공으로도_실패로도_세지_않는다() {
        AgentRunOutcomes outcomes = outcomesOf(run(failure("n2", "supplier", false),
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "failed", "0", OTHER_SELLER, LETTER)));

        assertThat(outcomes.succeeded()).containsExactly(AgentId.of(REPORT));
        assertThat(outcomes.failed()).isEmpty();
    }

    @Test
    void 수요자_탓으로_실패하면_실패한_에이전트를_세지_않는다() {
        AgentRunOutcomes outcomes = outcomesOf(run(failure("n2", "consumer", false),
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "failed", "0", OTHER_SELLER, LETTER)));

        assertThat(outcomes.succeeded()).containsExactly(AgentId.of(REPORT));
        assertThat(outcomes.failed()).isEmpty();
    }

    @Test
    void 플랫폼_탓으로_실패하면_아무_에이전트도_세지_않는다() {
        AgentRunOutcomes outcomes = outcomesOf(run(failure("n2", "platform", true),
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "failed", "0", OTHER_SELLER, LETTER)));

        assertThat(outcomes.succeeded()).isEmpty();
        assertThat(outcomes.failed()).isEmpty();
    }

    @Test
    void 엔진이_끝을_알리지_않아_끊은_실행은_세지_않는다() {
        AgentRunOutcomes outcomes = AgentRunOutcomes.of(null, ExecutionFailure.judge(null), BUYER);

        assertThat(outcomes.succeeded()).isEmpty();
        assertThat(outcomes.failed()).isEmpty();
    }

    @Test
    void 앞_단계에서_멈춰_돌지_못한_에이전트는_세지_않는다() {
        AgentRunOutcomes outcomes = outcomesOf(run(failure("n1", "consumer", false),
                step("n1", "failed", "0", SELLER, REPORT),
                step("n2", "pending", "0", OTHER_SELLER, LETTER)));

        assertThat(outcomes.succeeded()).isEmpty();
        assertThat(outcomes.failed()).isEmpty();
    }

    @Test
    void 자기_에이전트를_자기가_쓰면_성공도_실패도_세지_않는다() {
        String buyer = Long.toString(BUYER.value());

        AgentRunOutcomes succeeded = outcomesOf(run(null, step("n1", "succeeded", "0.5", buyer, REPORT)));
        AgentRunOutcomes failed = outcomesOf(run(failure("n1", "supplier", true),
                step("n1", "failed", "0.1", buyer, REPORT)));

        assertThat(succeeded.succeeded()).isEmpty();
        assertThat(failed.failed()).isEmpty();
    }

    @Test
    void 모델을_부르지_않은_mock_성공은_세지_않는다() {
        AgentRunOutcomes outcomes = outcomesOf(run(null,
                step("n1", "succeeded", "0", SELLER, REPORT),
                step("n2", "succeeded", "0", SELLER, REPORT)));

        assertThat(outcomes.succeeded()).isEmpty();
    }

    @Test
    void 분기에서_고르지_않아_건너뛴_단계가_있어도_나머지가_성공하면_성공으로_센다() {
        AgentRunOutcomes outcomes = outcomesOf(run(null,
                step("n1", "succeeded", "0.5", SELLER, REPORT),
                step("n2", "skipped", "0", SELLER, REPORT)));

        assertThat(outcomes.succeeded()).containsExactly(AgentId.of(REPORT));
    }

    @Test
    void 캔버스에_바로_놓인_노드는_에이전트가_아니라서_세지_않는다() {
        AgentRunOutcomes outcomes = outcomesOf(run(null, step("n1", "succeeded", "0.5", SELLER, null)));

        assertThat(outcomes.succeeded()).isEmpty();
        assertThat(outcomes.failed()).isEmpty();
    }

    // 운영 코드처럼 실패한 실행만 책임을 판정해 함께 넘긴다.
    private static AgentRunOutcomes outcomesOf(RunStatus run) {
        ExecutionFailure failure = run.isSucceeded() ? null : ExecutionFailure.judge(run);

        return AgentRunOutcomes.of(run, failure, BUYER);
    }

    private static RunStatus run(RunStatus.Failure failure, RunStatus.Node... steps) {
        return new RunStatus("run-1", failure == null ? "succeeded" : "failed", null, List.of(steps),
                List.of(), List.of(), null, failure, false, null);
    }

    // 엔진은 우리가 보낸 주인과 에이전트를 단계마다 그대로 돌려준다.
    private static RunStatus.Node step(String id, String state, String costAbc, String owner, UUID agent) {
        return new RunStatus.Node(id, "노드 " + id, "llm", state, agent == null ? null : agent.toString(), owner,
                new BigDecimal(costAbc), null);
    }

    private static RunStatus.Failure failure(String stepId, String fault, boolean affectsSuccessRate) {
        return new RunStatus.Failure(stepId, "MODEL_API_ERROR", fault, affectsSuccessRate);
    }
}
