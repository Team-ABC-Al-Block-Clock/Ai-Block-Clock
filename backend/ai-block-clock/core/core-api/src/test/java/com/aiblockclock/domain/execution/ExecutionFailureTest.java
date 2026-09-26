package com.aiblockclock.domain.execution;

import com.aiblockclock.client.ai.RunStatus;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.execution.ExecutionFault;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ExecutionFailureTest {
    private static final UUID AGENT = UUID.fromString("5f0c7b2e-8a1d-4c3b-9e6f-2d4a8b1c7e90");

    @Test
    void 공급자_탓이면_실패한_단계의_주인과_에이전트를_가리킨다() {
        ExecutionFailure failure = ExecutionFailure.judge(failedAt("n2", "MODEL_API_ERROR", "supplier", true));

        assertThat(failure).isEqualTo(new ExecutionFailure("n2", "MODEL_API_ERROR", ExecutionFault.SUPPLIER, true,
                UserId.of(7002L), AgentId.of(AGENT)));
    }

    @Test
    void 공급자_탓이어도_성공률에_넣지_말라고_오면_넣지_않는다() {
        ExecutionFailure failure = ExecutionFailure.judge(failedAt("n2", "REF_FILE_REJECTED", "supplier", false));

        assertThat(failure.fault()).isEqualTo(ExecutionFault.SUPPLIER);
        assertThat(failure.affectsSuccessRate()).isFalse();
    }

    @Test
    void 공급자_탓인데_성공률_반영_여부가_없으면_넣지_않는다() {
        ExecutionFailure failure = ExecutionFailure.judge(failedAt("n2", "MODEL_API_ERROR", "supplier", null));

        assertThat(failure.affectsSuccessRate()).isFalse();
    }

    @Test
    void 수요자_탓이면_공급자를_가리키지_않고_성공률에도_넣지_않는다() {
        ExecutionFailure failure = ExecutionFailure.judge(failedAt("n1", "FILE_PARSE_FAILED", "consumer", true));

        assertThat(failure).isEqualTo(new ExecutionFailure("n1", "FILE_PARSE_FAILED", ExecutionFault.CONSUMER, false,
                null, null));
    }

    @Test
    void 플랫폼_탓이면_공급자를_가리키지_않는다() {
        ExecutionFailure failure = ExecutionFailure.judge(failedAt("n2", "RATE_LIMITED", "platform", false));

        assertThat(failure.fault()).isEqualTo(ExecutionFault.PLATFORM);
        assertThat(failure.supplier()).isNull();
        assertThat(failure.isPlatformFault()).isTrue();
    }

    @Test
    void 책임이_비어_있거나_모르는_값이면_플랫폼_탓으로_본다() {
        assertThat(ExecutionFailure.judge(failedAt("n2", "MODEL_API_ERROR", null, true)).fault())
                .isEqualTo(ExecutionFault.PLATFORM);
        assertThat(ExecutionFailure.judge(failedAt("n2", "MODEL_API_ERROR", "nobody", true)).fault())
                .isEqualTo(ExecutionFault.PLATFORM);
    }

    @Test
    void 책임은_대소문자를_가리지_않고_읽는다() {
        assertThat(ExecutionFailure.judge(failedAt("n2", "MODEL_API_ERROR", "Supplier", true)).fault())
                .isEqualTo(ExecutionFault.SUPPLIER);
    }

    @Test
    void 실패했는데_까닭을_알리지_않으면_플랫폼_탓으로_본다() {
        RunStatus run = new RunStatus("run-1", "failed", null, List.of(), List.of(), List.of(), null, null);

        assertThat(ExecutionFailure.judge(run)).isEqualTo(new ExecutionFailure(
                null, ExecutionProgress.UNREPORTED, ExecutionFault.PLATFORM, false, null, null));
    }

    @Test
    void 엔진이_기한_안에_끝을_알리지_않아_끊은_실행은_플랫폼_탓이다() {
        assertThat(ExecutionFailure.judge(null)).isEqualTo(new ExecutionFailure(
                null, ExecutionProgress.STALLED, ExecutionFault.PLATFORM, false, null, null));
    }

    @Test
    void 엔진에_넘기지_못한_실행은_플랫폼_탓이다() {
        assertThat(ExecutionFailure.undispatched()).isEqualTo(new ExecutionFailure(
                null, ExecutionFailure.UNREACHABLE, ExecutionFault.PLATFORM, false, null, null));
    }

    @Test
    void 실패한_단계를_찾지_못하면_공급자_탓이어도_누구인지는_비워_둔다() {
        ExecutionFailure failure = ExecutionFailure.judge(failedAt("n9", "MODEL_API_ERROR", "supplier", true));

        assertThat(failure.fault()).isEqualTo(ExecutionFault.SUPPLIER);
        assertThat(failure.supplier()).isNull();
        assertThat(failure.agent()).isNull();
    }

    @Test
    void 돌려받은_주인이나_에이전트가_보낸_모양이_아니면_비워_둔다() {
        RunStatus run = new RunStatus("run-1", "failed", null,
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "failed", "not-a-uuid", "u_2001", null, null)),
                List.of(), List.of(), null, new RunStatus.Failure("n1", "MODEL_API_ERROR", "supplier", true));

        ExecutionFailure failure = ExecutionFailure.judge(run);

        assertThat(failure.supplier()).isNull();
        assertThat(failure.agent()).isNull();
    }

    // n1 은 내 노드, n2 는 7002 가 판 에이전트 안의 단계다.
    private static RunStatus failedAt(String stepId, String errorCode, String fault, Boolean affectsSuccessRate) {
        return new RunStatus("run-1", "failed", null,
                List.of(new RunStatus.Node("n1", "성적 분석", "llm", "succeeded", null, "7001", null, null),
                        new RunStatus.Node("n2", "가정통신문 작성", "llm", "failed", AGENT.toString(), "7002",
                                null, null)),
                List.of(), List.of(), null, new RunStatus.Failure(stepId, errorCode, fault, affectsSuccessRate));
    }
}
