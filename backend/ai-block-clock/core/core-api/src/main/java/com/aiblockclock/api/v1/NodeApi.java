package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.NodeSaveRequest;
import com.aiblockclock.api.v1.request.NodeTestRunRequest;
import com.aiblockclock.api.v1.request.RefFileAttachRequest;
import com.aiblockclock.api.v1.response.NodeDeleteResponse;
import com.aiblockclock.api.v1.response.NodeDetailResponse;
import com.aiblockclock.api.v1.response.NodePageResponse;
import com.aiblockclock.api.v1.response.NodeTestRunResponse;
import com.aiblockclock.api.v1.response.RefFileResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
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
            description = "워크플로우나 에이전트 버전에 놓여 있으면 삭제할 수 없다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제됨"),
            @ApiResponse(responseCode = "409", description = "워크플로우나 에이전트 버전에 배치되어 있다", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    ApiResult<NodeDeleteResponse> delete(UserId owner, UUID nodeId);

    @Operation(summary = "참고 자료 붙이기",
            description = """
                    노드가 기대는 자료를 붙여 둔다. 채점 기준표 · 양식처럼 프롬프트가 참조하는 것들이다.

                    **실행할 때마다 이 노드와 함께 실행 엔진으로 나간다.** 수요자가 실행할 때 올리는
                    파일과는 다르다.

                    PDF · 텍스트 · 마크다운 · CSV · 이미지만 받고, **한 노드에 붙인 자료의 합계가 10MB**
                    를 넘을 수 없다. 이미 공개한 에이전트 버전은 공개 시점의 자료를 계속 본다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "붙었다"),
            @ApiResponse(responseCode = "400", description = "빈 파일이거나 받지 않는 형식이다 (`NODE_006`)",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 노드다 (`NODE_002`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 노드다 (`NODE_001`)", content = @Content),
            @ApiResponse(responseCode = "413", description = """
                    파일 하나가 10MB 를 넘는다 (`FILE_002`).
                    이 노드에 붙인 자료의 합계가 10MB 를 넘는다 (`NODE_007`)""", content = @Content)
    })
    ApiResult<RefFileResponse> attachRefFile(
            UserId owner,
            @Parameter(description = "노드 ID", required = true) UUID nodeId,
            @Valid RefFileAttachRequest request);

    @Operation(summary = "참고 자료 떼기",
            description = """
                    노드에서 자료를 뗀다. **이미 공개한 에이전트 버전은 영향을 받지 않는다** —
                    그 버전은 공개 시점의 자료를 따로 들고 있다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "뗐다"),
            @ApiResponse(responseCode = "403", description = "남의 노드다 (`NODE_002`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 노드이거나 없는 자료다 (`NODE_001` · `NODE_005`)",
                    content = @Content)
    })
    ApiResult<Void> detachRefFile(
            UserId owner,
            @Parameter(description = "노드 ID", required = true) UUID nodeId,
            @Parameter(description = "자료 ID", required = true) UUID refFileId);

    @Operation(summary = "노드 테스트 실행",
            description = """
                    내 노드 하나를 실행 엔진에 **실제로 한 번 돌린다.** 붙여 둔 참고 자료도 함께 나간다.
                    결과물을 확인하고, 엔진이 잰 원가로 **노드 원가를 새로 정한다** — 최근 5번의 중앙값 × 1.5.

                    - 무료다. ABC 가 움직이지 않는다
                    - 한 노드에 **하루(UTC) 5번**까지다. 엔진에 넘기지 못한 테스트는 횟수에 넣지 않는다
                    - 곧바로 202 와 `RUNNING` 이 온다. 끝은 `GET .../test-runs/{testRunId}` 를 2초쯤 간격으로 조회해 안다
                    - `mock` 이면 모델을 부르지 않아 결과물이 가짜이고 원가를 재지 않는다
                    - 이미 공개한 에이전트 버전의 가격은 바뀌지 않는다. 새 원가는 다음 버전부터 쓴다
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "엔진에 넘겼다"),
            @ApiResponse(responseCode = "400", description = "comment 가 500자를 넘는다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "403", description = "남의 노드다 (`NODE_002`)", content = @Content),
            @ApiResponse(responseCode = "404", description = "없는 노드다 (`NODE_001`)", content = @Content),
            @ApiResponse(responseCode = "429", description = "오늘 이 노드의 테스트를 모두 썼다 (`NODE_009`)",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "실행 엔진이 받지 않았다. 횟수는 쓰지 않았다 (`EXECUTION_002`)",
                    content = @Content)
    })
    ApiResult<NodeTestRunResponse> startTestRun(
            UserId owner,
            @Parameter(description = "노드 ID", required = true) UUID nodeId,
            @Valid NodeTestRunRequest request);

    @Operation(summary = "노드 테스트 실행 조회",
            description = """
                    아직 도는 중이면 엔진에 물어 끝났는지 본다. 끝났으면 결과물(`result`)과 새로 정한 원가(`baseCost`)가 함께 온다.
                    엔진이 잠시 답하지 못하면 `RUNNING` 그대로 온다. 다시 조회하면 된다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "404", description = "없는 테스트이거나, 남의 테스트이거나, 이 노드의 테스트가 아니다 (`NODE_008`)",
                    content = @Content)
    })
    ApiResult<NodeTestRunResponse> getTestRun(
            UserId owner,
            @Parameter(description = "노드 ID", required = true) UUID nodeId,
            @Parameter(description = "테스트 실행 ID", required = true) UUID testRunId);

    @Operation(summary = "내 노드 목록", description = "최근 만든 순. 프롬프트 원문은 담지 않는다.")
    ApiResult<NodePageResponse> findMine(
            UserId owner,
            @Parameter(description = "이름 부분 일치") String keyword,
            @Parameter(description = "이전 응답의 nextCursor") String cursor,
            @Parameter(description = "한 페이지 크기") @Positive @Max(50) int size);
}
