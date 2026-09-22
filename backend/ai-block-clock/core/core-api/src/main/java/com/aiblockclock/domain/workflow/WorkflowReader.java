package com.aiblockclock.domain.workflow;

import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.storage.support.OffsetPageable;
import com.aiblockclock.storage.graph.GraphEdgeRepository;
import com.aiblockclock.storage.graph.GraphNodeRepository;
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
    private final GraphNodeRepository graphNodeRepository;
    private final GraphEdgeRepository graphEdgeRepository;

    public Workflow read(WorkflowId id) {
        WorkflowEntity entity = workflowRepository.findById(id.value())
                .orElseThrow(() -> new CoreException(ErrorType.WORKFLOW_NOT_FOUND));
        return withGraph(entity);
    }

    public CursorPage<WorkflowSummary> readMine(
            UserId owner, WorkflowSort sort, String keyword, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);

        // 다음 페이지가 있는지는 size + 1 번째 행이 있는지로 판정한다.
        // size 개가 꽉 찼다는 것만으로는 마지막 페이지와 구분되지 않는다.
        Pageable pageable = OffsetPageable.of(offset, size + 1);

        List<WorkflowEntity> found = sort == WorkflowSort.LATEST
                ? workflowRepository.findMineOrderByLatest(owner.value(), keyword, pageable)
                : workflowRepository.findMineOrderByLastExecuted(owner.value(), keyword, pageable);

        boolean hasNext = found.size() > size;
        List<WorkflowSummary> content = found.stream()
                .limit(size)
                .map(WorkflowEntityConverter::toSummary)
                .toList();
        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    private Workflow withGraph(WorkflowEntity entity) {
        return WorkflowEntityConverter.toDomain(entity, GraphConverter.toGraph(
                graphNodeRepository.findByGraphIdOrderByIdAsc(entity.getGraphId()),
                graphEdgeRepository.findByGraphIdOrderByIdAsc(entity.getGraphId())));
    }
}
