package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.ExecutionEvidenceResponse;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "관리자 · 실행 근거", description = """
        정산을 두고 사용자나 판매자가 이의를 걸 때 실행 하나의 근거를 꺼내 본다. 관리자 지갑으로 로그인한 사람만 부른다.
        돈의 기록(선차감 · 환불 · 정산 · 실패 판정)은 실행을 닫을 때, 근거(단계 표 · 엔진 원본)는 닫힌 직후 따로 적는다.
        닫힌 지 얼마 안 된 실행은 `runRecord` 가 아직 비어 있을 수 있다. 빠진 근거는 백필이 5분마다 채운다.
        """)
public interface ExecutionEvidenceApi {
    @Operation(summary = "실행 근거",
            description = """
                    - `execution` 접수 · 선차감 요약
                    - `chargeRates` 접수할 때 굳힌 단계별 배율 · 단가
                    - `steps` 단계마다 엔진이 알린 사실(상태 · 건너뛴 이유 · 토큰 · 달러 원가 · 재시도 · 고쳐 쓰기)과 단가 · 적립액
                    - `failure` 실패 책임 판정(누구 탓 · 실패 단계 · 오류 코드)
                    - `settlements` · `ledger` 실제로 옮긴 돈
                    - `runRecord` 닫을 때 엔진이 준 원본(노드 출력 포함, 프롬프트 없음)과 그 SHA-256, 무엇이 닫았는지(`closedBy`)
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음", content = @Content),
            @ApiResponse(responseCode = "403", description = "관리자가 아니다", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 실행이다 (`EXECUTION_001`)", content = @Content)
    })
    ApiResult<ExecutionEvidenceResponse> getEvidence(UUID executionId);
}
