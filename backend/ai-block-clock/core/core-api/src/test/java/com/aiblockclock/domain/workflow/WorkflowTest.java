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

}
