package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.NodeSaveRequest;
import com.aiblockclock.api.v1.response.NodeDeleteResponse;
import com.aiblockclock.api.v1.response.NodeDetailResponse;
import com.aiblockclock.api.v1.response.NodePageResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Tag(name = "작업 노드", description = """
        프롬프트 하나로 된 최소 실행 단위를 만들고 관리한다.
        노드는 팔리지 않는다. 캔버스에 놓고 에이전트로 굳혀야 팔린다.
        """)
public interface NodeApi {
    @Operation(summary = "노드 생성",
            description = "프롬프트를 저장한다. 원가는 저장할 때 산출해 함께 박는다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "생성됨"),
            @ApiResponse(responseCode = "400", description = "프롬프트가 비었거나 이름이 길다", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ApiResult<NodeDetailResponse> save(UserId owner, @Valid NodeSaveRequest request);

    @Operation(summary = "노드 상세", description = "프롬프트 원문까지 돌려준다. 소유자만 볼 수 있다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "403", description = "남의 노드", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "404", description = "없는 노드", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ApiResult<NodeDetailResponse> get(UserId owner, UUID nodeId);

    @Operation(summary = "노드 수정",
            description = "이름·프롬프트·모델을 한 번에 고친다. 원가는 다시 산출해 덮어쓴다.")
    ApiResult<NodeDetailResponse> update(UserId owner, UUID nodeId, @Valid NodeSaveRequest request);

    @Operation(summary = "노드 삭제",
            description = "워크플로우에 놓여 있으면 삭제할 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제됨"),
            @ApiResponse(responseCode = "409", description = "워크플로우에 배치되어 있다", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ApiResult<NodeDeleteResponse> delete(UserId owner, UUID nodeId);

    @Operation(summary = "내 노드 목록", description = "최근 만든 순. 프롬프트 원문은 담지 않는다.")
    ApiResult<NodePageResponse> findMine(
            UserId owner,
            @Parameter(description = "이름 부분 일치") String keyword,
            @Parameter(description = "이전 응답의 nextCursor") String cursor,
            @Parameter(description = "한 페이지 크기") @Positive @Max(50) int size);
}
