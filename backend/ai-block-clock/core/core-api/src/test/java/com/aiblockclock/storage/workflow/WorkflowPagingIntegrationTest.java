package com.aiblockclock.storage.workflow;

import com.aiblockclock.enums.workflow.ValidationStatus;
import com.aiblockclock.storage.support.OffsetPageable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
@SpringBootTest
@Transactional
class WorkflowPagingIntegrationTest {

    private static final Long OWNER_ID = 7L;

    @Autowired
    private WorkflowRepository workflowRepository;

    @BeforeEach
    void setUp() {
        for (int i = 0; i < 30; i++) {
            workflowRepository.save(new WorkflowEntity(
                    UUID.randomUUID(),
                    OWNER_ID,
                    "workflow-%02d".formatted(i),
                    null,
                    ValidationStatus.NOT_VALIDATED,
                    0L));
        }

        workflowRepository.flush();
    }

    @Test
    @DisplayName("페이징 도중 size 가 바뀌어도 offset 위치에서 이어서 읽는다")
    void resumesFromExactOffsetWhenSizeChanges() {
        List<WorkflowEntity> firstPage = findLatest(0, 10);
        assertThat(firstPage).hasSize(10);

        // 첫 페이지를 10개 읽은 뒤 size 를 20 으로 늘려 이어 읽는다.
        // offset(10) 이 size(20) 의 배수가 아닌 경우다.
        List<WorkflowEntity> secondPage = findLatest(10, 20);

        assertThat(secondPage).hasSize(20);
        assertThat(names(secondPage))
                .as("첫 페이지에서 읽은 행이 다시 나오면 안 된다")
                .doesNotContainAnyElementsOf(names(firstPage));

        assertThat(names(firstPage))
                .as("두 페이지를 합치면 누락 없이 전체가 된다")
                .hasSize(10);
        assertThat(concat(firstPage, secondPage)).hasSize(30).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("offset 이 size 의 배수일 때도 그대로 동작한다")
    void pagesNormallyWhenOffsetIsAMultipleOfSize() {
        List<String> first = names(findLatest(0, 10));
        List<String> second = names(findLatest(10, 10));
        List<String> third = names(findLatest(20, 10));

        assertThat(concat3(first, second, third)).hasSize(30).doesNotHaveDuplicates();
    }

    private List<WorkflowEntity> findLatest(int offset, int size) {
        return workflowRepository.findMineOrderByLatest(OWNER_ID, null, OffsetPageable.of(offset, size));
    }

    private static List<String> names(List<WorkflowEntity> entities) {
        return entities.stream().map(WorkflowEntity::getName).toList();
    }

    private static List<String> concat(List<WorkflowEntity> first, List<WorkflowEntity> second) {
        return java.util.stream.Stream.concat(names(first).stream(), names(second).stream()).toList();
    }

    private static List<String> concat3(List<String> a, List<String> b, List<String> c) {
        return java.util.stream.Stream.of(a, b, c).flatMap(List::stream).toList();
    }
}
