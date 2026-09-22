package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.Optional;
import java.util.UUID;

class WorkflowValidatorTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);
    private static final WorkflowId WORKFLOW_ID = WorkflowId.of(UUID.randomUUID());

    private final WorkflowRepository workflowRepository = mock(WorkflowRepository.class);
    private final WorkflowValidator validator =
            new WorkflowValidator(workflowRepository, new WorkflowUsageReader());

    private void locked(long revision) {
        given(workflowRepository.findByIdForUpdate(WORKFLOW_ID.value()))
                .willReturn(Optional.of(new WorkflowEntity(WORKFLOW_ID.value(), OWNER.value(),
                        "성적 분석", null, ValidationStatus.VALID, revision)));
    }

    @Test
    @DisplayName("소유자만 통과시킨다")
    void onlyOwnerPasses() {
        assertThatCode(() -> WorkflowValidator.ensureOwner(OWNER, OWNER)).doesNotThrowAnyException();
        assertThatThrownBy(() -> WorkflowValidator.ensureOwner(OWNER, OTHER))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_FORBIDDEN);
    }

    @Test
    @DisplayName("교체하면 회차가 오르고 지난 검증 결과는 무효가 된다")
    void bumpsRevisionAndResetsValidation() {
        locked(1L);

        GraphRevision revised = validator.ensureReplaceable(WORKFLOW_ID, OWNER, Revision.initial());

        assertThat(revised.revision()).isEqualTo(Revision.of(2));
        assertThat(revised.validationStatus()).isEqualTo(ValidationStatus.NOT_VALIDATED);
    }

    @Test
    @DisplayName("들고 온 회차가 낡았으면 덮어쓰지 않는다")
    void rejectsStaleRevision() {
        locked(2L);

        assertThatThrownBy(() -> validator.ensureReplaceable(WORKFLOW_ID, OWNER, Revision.initial()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_REVISION_CONFLICT);
    }

    @Test
    @DisplayName("소유자가 아니면 회차를 보기 전에 막는다")
    void rejectsNonOwner() {
        locked(1L);

        assertThatThrownBy(() -> validator.ensureReplaceable(WORKFLOW_ID, OTHER, Revision.initial()))
                .isInstanceOf(CoreException.class)
                .extracting(e -> ((CoreException) e).getErrorType())
                .isEqualTo(ErrorType.WORKFLOW_FORBIDDEN);
    }
}
