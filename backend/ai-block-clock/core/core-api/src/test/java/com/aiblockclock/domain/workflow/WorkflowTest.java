package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WorkflowTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);

    private Workflow workflow;

    @BeforeEach
    void setUp() {
        workflow = Workflow.create(
                WorkflowId.newId(), OWNER, "성적 분석", null, GraphFixtures.straightLine());
    }

    @Test
    @DisplayName("새 워크플로우는 첫 회차, 검증 전 상태로 시작한다")
    void startsAtFirstRevision() {
        assertThat(workflow.getGraph().nodeCount()).isEqualTo(3);
        assertThat(workflow.getRevision()).isEqualTo(Revision.initial());
        assertThat(workflow.getValidationStatus()).isEqualTo(ValidationStatus.NOT_VALIDATED);
    }

    @Test
    @DisplayName("소유자만 통과시킨다")
    void onlyOwnerPasses() {
        assertThatCode(() -> workflow.ensureOwner(OWNER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> workflow.ensureOwner(OTHER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_FORBIDDEN);
    }

    @Test
    @DisplayName("교체하면 회차가 오르고 지난 검증 결과는 무효가 된다")
    void bumpsRevisionAndResetsValidation() {
        Workflow validated = WorkflowFixtures.validated(OWNER, GraphFixtures.straightLine());

        GraphRevision revised = validated.nextRevisionFor(Revision.initial());

        assertThat(revised.revision()).isEqualTo(Revision.of(2));
        assertThat(revised.validationStatus()).isEqualTo(ValidationStatus.NOT_VALIDATED);
    }

    @Test
    @DisplayName("들고 온 회차가 낡았으면 덮어쓰지 않는다")
    void rejectsStaleRevision() {
        assertThatThrownBy(() -> workflow.nextRevisionFor(Revision.of(2)))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_REVISION_CONFLICT);
    }
}
