package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.AgentProvenanceResponse;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.UUID;

@Tag(name = "마켓", description = "공개된 에이전트를 찾는다.")
public interface AgentProvenanceApi {
    @Operation(summary = "에이전트 원본 증명 조회",
            description = """
                    이 에이전트를 누가 언제 올렸는지를 **SSAFY 체인의 등기부 기록**으로 보여 준다 (MKT-006).

                    에이전트 버전을 공개하면 서버가 원작자 지갑과 메타데이터 해시를 등기부 토큰의 문서 칸에 적는다.
                    적는 일은 공개와 따로 돌아서, 공개 직후에는 `PENDING` 이고 블록에 적히면 `CONFIRMED` 가 된다.
                    확정 전에는 거래 · 블록 · 체인 쪽 값이 null 이고 대조는 모두 false 다.

                    확정된 기록은 **조회할 때마다 체인에서 다시 읽고, 지금 DB 의 내용으로 해시를 다시 만들어** 대조한다 (CHN-003).
                    공개한 버전은 바뀌지 않으므로 정상이면 늘 맞는다. 어긋나면 `verification.mismatchReason` 이 까닭을 말하고,
                    마지막 대조 결과는 서버의 원본 기록에 남는다.
                    노드 구성 · 프롬프트는 체인에 적지 않는다. 해시만 적는다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "지금 공개된 버전의 원본 증명"),
            @ApiResponse(responseCode = "404", description = "공개된 에이전트가 없다 (`MARKET_001`) 또는 "
                    + "에이전트는 있는데 원본 기록이 없다 (`BLOCKCHAIN_003`, 이 기능 전에 공개한 버전)",
                    content = @Content),
            @ApiResponse(responseCode = "503", description = "체인에서 기록을 읽지 못했다 (`BLOCKCHAIN_004`)",
                    content = @Content)
    })
    ApiResult<AgentProvenanceResponse> getProvenance(
            @Parameter(description = "에이전트 ID", required = true,
                    example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
            UUID agentId);
}
