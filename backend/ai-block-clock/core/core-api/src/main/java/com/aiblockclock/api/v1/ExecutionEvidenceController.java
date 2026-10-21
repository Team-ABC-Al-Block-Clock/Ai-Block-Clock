package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.ExecutionEvidenceResponse;
import com.aiblockclock.domain.execution.ExecutionEvidenceService;
import com.aiblockclock.domain.execution.ExecutionId;
import com.aiblockclock.support.response.ApiResult;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

// 관리자 권한은 SecurityConfig 가 /api/v1/admin/** 에서 먼저 본다
@RestController
@RequestMapping("/api/v1/admin/executions")
@RequiredArgsConstructor
public class ExecutionEvidenceController implements ExecutionEvidenceApi {
    private final ExecutionEvidenceService executionEvidenceService;

    @Override
    @GetMapping("/{executionId}/evidence")
    public ApiResult<ExecutionEvidenceResponse> getEvidence(@PathVariable UUID executionId) {
        return ApiResult.success(ExecutionEvidenceResponse.from(
                executionEvidenceService.read(ExecutionId.of(executionId))));
    }
}
