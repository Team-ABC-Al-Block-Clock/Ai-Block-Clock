package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("소유자가 아니면 캔버스를 바꿀 수 없다")
    void onlyOwnerCanReplaceGraph() {
        assertThatThrownBy(() -> workflow.replaceGraph(
                OTHER, GraphFixtures.straightLine(), Revision.initial()))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("교체에 성공하면 회차가 오른다")
    void bumpsRevision() {
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());

        assertThat(workflow.getRevision()).isEqualTo(Revision.of(2));
    }

    @Test
    @DisplayName("들고 온 회차가 낡았으면 덮어쓰지 않는다")
    void rejectsStaleRevision() {
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());

        assertThatThrownBy(() -> workflow.replaceGraph(
                OWNER, GraphFixtures.chainOf(2), Revision.initial()))
                .isInstanceOf(CoreException.class);
        assertThat(workflow.getGraph().nodeCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("캔버스가 바뀌면 지난 검증 결과는 무효가 된다")
    void resetsValidationOnReplace() {
        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.initial());
        workflow.markValidated();

        workflow.replaceGraph(OWNER, GraphFixtures.straightLine(), Revision.of(2));

        assertThat(workflow.getValidationStatus()).isEqualTo(ValidationStatus.NOT_VALIDATED);
    }

    @Test
    @DisplayName("소유자가 아니면 이름도 바꿀 수 없다")
    void onlyOwnerCanRename() {
        assertThatThrownBy(() -> workflow.rename(OTHER, "다른 이름"))
                .isInstanceOf(CoreException.class);
    }
}
