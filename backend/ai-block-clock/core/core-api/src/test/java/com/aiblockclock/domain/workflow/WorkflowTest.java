package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.ValidationStatus;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class WorkflowTest {
    private static final UserId OWNER = UserId.of(1L);

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
