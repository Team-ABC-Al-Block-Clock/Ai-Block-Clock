package com.aiblockclock.domain.execution;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;

// 관리자가 정산 다툼에 답할 때 실행 하나의 근거를 꺼내 본다. 읽기만 한다.
@Service
@RequiredArgsConstructor
public class ExecutionEvidenceService {
    private final ExecutionEvidenceReader executionEvidenceReader;

    public ExecutionEvidence read(ExecutionId id) {
        return executionEvidenceReader.read(id);
    }
}
