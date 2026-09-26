package com.aiblockclock.domain.execution;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.agent.AgentId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.storage.execution.ExecutionSettlementEntity;
import com.aiblockclock.storage.execution.ExecutionSettlementRepository;
import com.aiblockclock.storage.execution.ExecutionSettlementRow;
import com.aiblockclock.storage.support.OffsetPageable;
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
public class ExecutionSettlementReader {
    private final ExecutionSettlementRepository executionSettlementRepository;

    public CursorPage<SettlementRecord> readPage(
            UserId supplier, SettlementSearchCondition condition, String cursor, int size) {
        int offset = OffsetCursor.decode(cursor);

        // size + 1 개를 읽어 하나 더 나오면 다음 페이지가 있다고 본다. 전체 개수를 세지 않기 위함이다.
        List<SettlementRecord> found = search(supplier, condition, OffsetPageable.of(offset, size + 1));
        boolean hasNext = found.size() > size;
        List<SettlementRecord> content = found.stream()
                .limit(size)
                .toList();

        return new CursorPage<>(content, hasNext ? OffsetCursor.encode(offset + size) : null, hasNext);
    }

    // 통계는 기간 안의 줄을 모두 모아 묶는다. 날짜 묶기를 DB 함수로 하면 H2 와 MySQL 이 갈린다.
    public List<SettlementRecord> readAll(UserId supplier, SettlementSearchCondition condition) {
        return search(supplier, condition, Pageable.unpaged());
    }

    private List<SettlementRecord> search(UserId supplier, SettlementSearchCondition condition, Pageable pageable) {
        return executionSettlementRepository.searchBySupplier(
                        supplier.value(),
                        condition.agent() == null ? null : condition.agent().value(),
                        condition.period().startAtInclusive(),
                        condition.period().endAtExclusive(),
                        pageable).stream()
                .map(ExecutionSettlementReader::toRecord)
                .toList();
    }

    private static SettlementRecord toRecord(ExecutionSettlementRow row) {
        ExecutionSettlementEntity entity = row.settlement();
        ExecutionSettlement settlement = new ExecutionSettlement(
                entity.getStepId(),
                entity.getNodeName(),
                UserId.of(entity.getSupplierId()),
                entity.getAgentId() == null ? null : AgentId.of(entity.getAgentId()),
                AbcAmount.of(entity.getAmountAbc()));

        return new SettlementRecord(ExecutionId.of(entity.getExecutionId()), settlement,
                entity.getSupplierId().equals(row.requesterId()), entity.getCreatedAt());
    }
}
