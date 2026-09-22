package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.paging.CursorPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class WorkflowReaderIntegrationTest {
    private static final UserId OWNER = UserId.of(9101L);

    @Autowired
    private WorkflowReader workflowReader;

    @Autowired
    private WorkflowRepository workflowRepository;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 3; i++) {
            workflowRepository.save(new WorkflowEntity(
                    UUID.randomUUID(),
                    OWNER.value(),
                    "workflow-%d".formatted(i),
                    null,
                    ValidationStatus.NOT_VALIDATED,
                    0L));
        }

        workflowRepository.flush();
    }

    @Test
    @DisplayName("전체 개수가 size 와 정확히 같으면 다음 페이지가 없다")
    void reportsNoNextPageWhenTotalEqualsSize() {
        CursorPage<WorkflowSummary> page = read(null, 3);

        assertThat(page.content()).hasSize(3);
        assertThat(page.hasNext()).isFalse();
        assertThat(page.nextCursor()).isNull();
    }

    @Test
    @DisplayName("다음 행이 남아 있으면 다음 페이지를 알리고, 마지막 페이지에서 멈춘다")
    void reportsNextPageOnlyWhileRowsRemain() {
        CursorPage<WorkflowSummary> firstPage = read(null, 2);

        assertThat(firstPage.content()).hasSize(2);
        assertThat(firstPage.hasNext()).isTrue();
        assertThat(firstPage.nextCursor()).isNotNull();

        CursorPage<WorkflowSummary> lastPage = read(firstPage.nextCursor(), 2);

        assertThat(lastPage.content()).hasSize(1);
        assertThat(lastPage.hasNext()).isFalse();
        assertThat(lastPage.nextCursor()).isNull();
    }

    private CursorPage<WorkflowSummary> read(String cursor, int size) {
        return workflowReader.readMine(OWNER, WorkflowSort.LATEST, null, cursor, size);
    }
}
