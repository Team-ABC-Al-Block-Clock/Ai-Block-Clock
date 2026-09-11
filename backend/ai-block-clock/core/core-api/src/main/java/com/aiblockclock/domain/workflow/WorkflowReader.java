package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.storage.workflow.WorkflowEntity;
import com.aiblockclock.storage.workflow.WorkflowRepository;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.paging.CursorPage;
import com.aiblockclock.support.paging.OffsetCursor;

import lombok.RequiredArgsConstructor;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)

public class WorkflowReader {
    private final WorkflowRepository workflowRepository;

    public Workflow read(WorkflowId id) {
        WorkflowEntity entity = workflowRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NOT_FOUND));
        return WorkflowEntityConverter.toDomain(entity);
    }

    public CursorPage<WorkflowSummary> readMine(
            OwnerId owner, WorkflowSort sort, String keyword, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);
        Pageable pageable = OffsetPageable.of(offset, size);

        List<WorkflowEntity> found = sort == WorkflowSort.LATEST
                ? workflowRepository.findMineOrderByLatest(owner.value(), keyword, pageable)
                : workflowRepository.findMineOrderByLastExecuted(owner.value(), keyword, pageable);

        boolean hasNext = found.size() == size;
        List<WorkflowSummary> content = found.stream()
                .map(WorkflowEntityConverter::toSummary)
                .toList();
        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }
}
