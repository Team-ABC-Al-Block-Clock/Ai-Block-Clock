package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.WorkflowReplaceRequest;
import com.aiblockclock.api.v1.request.WorkflowSaveRequest;
import com.aiblockclock.api.v1.response.WorkflowDeleteResponse;
import com.aiblockclock.api.v1.response.WorkflowDetailResponse;
import com.aiblockclock.api.v1.response.WorkflowPageResponse;
import com.aiblockclock.api.v1.response.WorkflowSaveResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.enums.workflow.WorkflowSort;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

@Tag(name = "워크플로우", description = "캔버스에서 만든 워크플로우를 저장하고 다시 불러온다.")
public interface WorkflowApi {
    String SAVE_REQUEST_EXAMPLE = """
            {
              "name": "성적 분석 리포트",
              "description": "학생별 성적을 모아 리포트로 만든다",
              "nodes": [
                { "nodeKey": "n1", "sourceType": "NODE", "nodeType": "LLM",
                  "sourceId": "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13" },
                { "nodeKey": "n2", "sourceType": "NODE", "nodeType": "LLM",
                  "name": "가정통신문 작성", "prompt": "분석 결과로 가정통신문을 쓴다" }
              ],
              "edges": [
                { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
              ]
            }""";

    String REPLACE_REQUEST_EXAMPLE = """
            {
              "name": "성적 분석 리포트 v2",
              "revision": 1,
              "nodes": [
                { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13" },
                { "nodeKey": "n2", "sourceType": "NODE", "sourceId": "c0a8012e-5d31-4f0a-8b6d-77a1e9f3b210" },
                { "nodeKey": "n3", "sourceType": "NODE", "sourceId": "1b9d6bcd-bbfd-4b2d-9b5d-ab8dfbbd4bed" }
              ],
              "edges": [
                { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" },
                { "edgeKey": "e2", "sourceNodeKey": "n2", "targetNodeKey": "n3" }
              ]
            }""";

    String DETAIL_RESPONSE_EXAMPLE = """
            {
              "status": "SUCCESS",
              "data": {
                "workflowId": "33567ab2-e08b-461b-a0f2-51a857df5ec7",
                "name": "성적 분석 리포트",
                "description": "학생별 성적을 모아 리포트로 만든다",
                "revision": 1,
                "nodes": [
                  { "nodeKey": "n1", "sourceType": "NODE", "sourceId": "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13",
                    "versionId": null, "inputSource": "PREV",
                    "name": "성적 분석", "mine": true },
                  { "nodeKey": "n2", "sourceType": "AGENT", "sourceId": "b7e2d4c8-1f65-4a09-93de-5c0a8b2f7e41",
                    "versionId": "6f3a1c92-77b4-4a8e-9d61-2c5e8a0b4f17", "inputSource": "PREV",
                    "name": "가정통신문 작성 에이전트", "mine": false }
                ],
                "edges": [
                  { "edgeKey": "e1", "sourceNodeKey": "n1", "targetNodeKey": "n2" }
                ],
                "lastExecutedAt": null,
                "updatedAt": "2026-09-11T09:31:15.482Z"
              },
              "error": null
            }""";

    String NOT_FOUND_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_002", "message": "워크플로우를 찾을 수 없습니다.", "data": null } }""";

    String FORBIDDEN_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_003", "message": "워크플로우에 대한 권한이 없습니다.", "data": null } }""";

    String GRAPH_EMPTY_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_014", "message": "노드를 하나 이상 놓아야 저장할 수 있습니다.", "data": null } }""";

    String CYCLE_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_005", "message": "노드 연결에 순환 구조가 존재합니다.", "data": null } }""";

    String EXECUTING_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_010", "message": "실행이 진행 중인 워크플로우는 삭제할 수 없습니다.", "data": null } }""";

    String NOT_TREE_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_013", "message": "분기 노드가 아니면 갈라질 수 없고, 시작점은 하나여야 합니다.",
                         "data": { "nodeKey": "n1" } } }""";

    String BRANCH_EDGES_INVALID_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_013", "message": "분기 노드는 참 · 거짓 갈래를 하나씩 내고, 갈래 표시는 분기 노드에서 나가는 선에만 붙습니다.",
                         "data": { "nodeKey": "br1" } } }""";

    String BRANCH_CONDITION_INVALID_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_013", "message": "분기 조건은 이 분기까지 어느 갈래로 와도 거치는 작업 노드나 에이전트의 결과를 `<nodeKey>.<결과 안의 경로>` 로 가리켜야 합니다.",
                         "data": { "nodeKey": "br1", "left": "n9.x" } } }""";

    String DANGLING_EDGE_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_013", "message": "연결선이 존재하지 않는 노드를 가리킵니다.",
                         "data": { "edgeKey": "e1", "nodeKey": "n9" } } }""";

    String DEPTH_LIMIT_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_007", "message": "연결 깊이가 허용 범위를 넘었습니다.",
                         "data": { "limit": 10, "actual": 12 } } }""";

    String SOURCE_NOT_FOUND_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_015", "message": "배치할 노드나 공개된 에이전트를 찾을 수 없습니다.",
                         "data": { "nodeKey": "n2", "sourceId": "b7e2d4c8-1f65-4a09-93de-5c0a8b2f7e41" } } }""";

    String NODE_TYPE_MISMATCH_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_017", "message": "배치한 노드가 요청한 종류가 아닙니다.",
                         "data": { "nodeKey": "n1", "sourceId": "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13",
                                   "nodeType": "TOOL" } } }""";

    String REVISION_CONFLICT_EXAMPLE = """
            { "status": "FAIL", "data": null,
              "error": { "code": "WORKFLOW_016", "message": "워크플로우가 다른 요청에 의해 수정되었습니다.",
                         "data": { "expectedRevision": 1, "currentRevision": 3 } } }""";

    @Operation(
            summary = "워크플로우 저장",
            description = """
                    캔버스 구성을 새 워크플로우로 저장한다. `revision` 은 1 로 시작한다.

                    배치는 종류(`nodeType`)에 따라 보내는 것이 다르다.

                    | `nodeType` | 보내는 것 | 서버가 하는 일 |
                    | --- | --- | --- |
                    | `LLM` | `sourceId` 또는 `name` · `prompt` (· `model`) | 가리키면 놓고, 실어 보내면 만들어 놓는다 |
                    | `TRIGGER` · `TOOL` | `sourceId` | 미리 정의된 그 종류의 노드인지 보고 놓는다 |
                    | 없음 (`sourceType: AGENT`) | `sourceId` | 공개된 에이전트를 참조한다 |
                    | 없음 (`sourceType: IO`) | `ioType` 과 그 종류가 드는 값 | 원본 없이 그 자리에 둔다 |

                    `sourceId` 로 가리킨 노드가 밝힌 종류와 다르면 `WORKFLOW_017` 이다.
                    `nodeType` 을 생략하고 `sourceId` 만 보내면 종류를 대조하지 않고 그 행을 그대로 놓는다.

                    프롬프트를 그 자리에서 쓴 단계는 저장 시점에 아직 행이 아니라 본문을 실어 보낸다.
                    같은 트랜잭션에서 만들므로 저장이 실패하면 노드도 남지 않는다.
                    만들어진 `sourceId` 는 응답의 `nodes` 로 돌려준다. **다음 수정 요청에는 그 ID 를 보낸다.**

                    `TRIGGER` · `TOOL` 은 아직 미리 정의된 것이 하나도 없어 `WORKFLOW_015` · `WORKFLOW_017` 로 거부된다 —
                    AI 런타임 주문서에 노드 종류 칸이 없어 실행 경로가 없다.
                    `AGENT` 도 아직 원본을 조회할 수 없어 `WORKFLOW_015` 로 거부한다.

                    `sourceType: IO` 는 캔버스 양 끝에 서는 자리다. 시작 노드(`ioType: START`)는 실행할 때 사용자가
                    채울 `fields` 를 들고, 비우면 실행 화면이 자유 입력란 하나를 그린다. 끝 노드(`ioType: END`)는
                    결과를 어떤 구조로 낼지(`resultShape`)와 어떤 형식으로 받을지(`resultFormat`)를 든다.
                    **아직 실행에 싣지 않는다** — 놓으면 실행과 견적이 `EXECUTION_005` 로 막힌다.

                    구조 검사는 다섯 가지다. 노드 키·연결선 키가 겹치지 않을 것, 연결선이 실재하는 노드를 가리킬 것,
                    시작점이 하나이고 분기 노드가 아니면 갈라지지 않을 것, 분기 노드(`sourceType: CONTROL`)는 참 · 거짓 갈래를
                    하나씩 내고 조건이 그 분기까지 어느 갈래로 와도 거치는 작업 노드나 에이전트를 가리킬 것, 시작 노드와 끝 노드는
                    각각 하나뿐이고 시작으로는 선이 들어오지 않으며 끝에서는 선이 나가지 않을 것.
                    어기면 `WORKFLOW_013`, 순환은 `WORKFLOW_005` 다.
                    분기의 두 갈래는 한 노드로 다시 모을 수 있다(합류). 끝 노드로 모아도 된다.
                    여러 노드를 함께 돌려 결과를 묶는 병합은 아직 받지 않는다.""",
            requestBody = @RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "노드 두 개를 한 줄로", value = SAVE_REQUEST_EXAMPLE))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "저장 완료"),
            @ApiResponse(responseCode = "400", description = "요청 본문이나 그래프 구조가 올바르지 않다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = {
                                    @ExampleObject(name = "WORKFLOW_014 · 노드가 없다", value = GRAPH_EMPTY_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_013 · 갈라지거나 시작점이 여럿이다", value = NOT_TREE_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_013 · 분기 갈래가 맞지 않는다",
                                            value = BRANCH_EDGES_INVALID_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_013 · 분기 조건이 앞 노드를 가리키지 않는다",
                                            value = BRANCH_CONDITION_INVALID_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_013 · 연결선이 빈 곳을 가리킨다",
                                            value = DANGLING_EDGE_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_005 · 순환", value = CYCLE_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_007 · 깊이 초과", value = DEPTH_LIMIT_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_017 · 노드 종류가 다르다",
                                            value = NODE_TYPE_MISMATCH_EXAMPLE)})),
            @ApiResponse(responseCode = "404", description = "배치할 원본 노드나 공개 에이전트가 없다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = SOURCE_NOT_FOUND_EXAMPLE)))})
    ApiResult<WorkflowSaveResponse> save(UserId owner, @Valid WorkflowSaveRequest request);

    @Operation(
            summary = "워크플로우 상세 조회",
            description = """
                    저장된 노드와 연결선을 돌려준다. 캔버스는 이 값으로 복원한다.

                    배치 좌표는 저장하지 않는다. `edges` 의 연결 순서만으로 화면이 배치를 계산한다.
                    `nodes` 와 `edges` 는 그 연결을 따라간 순서로 나간다. 보낸 배열 순서가 달랐어도 같다.
                    소유자만 조회할 수 있다.

                    노드마다 `name` 과 `mine` 이 함께 나간다. 이름은 카드에 그대로 박으면 되고,
                    `mine` 이 전부 `true` 일 때만 이 워크플로우를 에이전트로 등록할 수 있다.
                    등록을 막는 기준은 서버가 쥐고 있으므로 화면이 소유자를 따로 대조할 필요가 없다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료", useReturnTypeSchema = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = DETAIL_RESPONSE_EXAMPLE))),
            @ApiResponse(responseCode = "403", description = "소유자가 아니다 (`WORKFLOW_003`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "워크플로우가 없다 (`WORKFLOW_002`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE)))})
    ApiResult<WorkflowDetailResponse> get(
            UserId owner,
            @Parameter(description = "조회할 워크플로우 ID", required = true,
                    example = "33567ab2-e08b-461b-a0f2-51a857df5ec7") UUID workflowId);

    @Operation(
            summary = "워크플로우 수정",
            description = """
                    이름과 그래프를 통째로 바꾼다. 부분 수정은 없다. 보낸 구성이 그대로 저장된 구성이 된다.

                    `revision` 에는 **수정 전에 읽은 값**을 넣는다. 그 사이 다른 요청이 먼저 수정했으면 `WORKFLOW_016` 으로 거부하고,
                    성공하면 `revision` 이 1 오른다. 응답의 `revision` 을 다음 수정에 다시 넣으면 된다.

                    저장할 때와 같은 구조 검사를 거치고, 새 단계를 본문으로 실어 보내는 것도 같다.
                    이미 만들어진 노드에는 `sourceId` 를 보낸다. 본문을 다시 보내면 노드가 하나 더 만들어진다.""",
            requestBody = @RequestBody(required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(name = "노드 하나를 뒤에 붙인다", value = REPLACE_REQUEST_EXAMPLE))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "수정 완료. `revision` 이 1 오른다"),
            @ApiResponse(responseCode = "400", description = "요청 본문이나 그래프 구조가 올바르지 않다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = {
                                    @ExampleObject(name = "WORKFLOW_013 · 갈라지거나 시작점이 여럿이다", value = NOT_TREE_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_013 · 분기 갈래가 맞지 않는다",
                                            value = BRANCH_EDGES_INVALID_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_013 · 분기 조건이 앞 노드를 가리키지 않는다",
                                            value = BRANCH_CONDITION_INVALID_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_005 · 순환", value = CYCLE_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_017 · 노드 종류가 다르다",
                                            value = NODE_TYPE_MISMATCH_EXAMPLE)})),
            @ApiResponse(responseCode = "403", description = "소유자가 아니다 (`WORKFLOW_003`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "워크플로우나 배치할 원본이 없다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = {
                                    @ExampleObject(name = "WORKFLOW_002 · 워크플로우 없음", value = NOT_FOUND_EXAMPLE),
                                    @ExampleObject(name = "WORKFLOW_015 · 원본 없음", value = SOURCE_NOT_FOUND_EXAMPLE)})),
            @ApiResponse(responseCode = "409", description = "그 사이 다른 요청이 먼저 수정했다",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = REVISION_CONFLICT_EXAMPLE)))})
    ApiResult<WorkflowSaveResponse> replace(
            UserId owner,
            @Parameter(description = "수정할 워크플로우 ID", required = true,
                    example = "33567ab2-e08b-461b-a0f2-51a857df5ec7") UUID workflowId,
            @Valid WorkflowReplaceRequest request);

    @Operation(
            summary = "워크플로우 삭제",
            description = """
                    워크플로우와 그 노드·연결선을 지운다. 배치의 원본이 된 노드와 에이전트는 지우지 않는다.

                    에이전트 버전은 그래프를 복사해 갖고 있으므로 출처 워크플로우를 지워도 그대로 실행된다.
                    실행 중인 워크플로우(`WORKFLOW_010`)만 삭제할 수 없다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "삭제 완료"),
            @ApiResponse(responseCode = "403", description = "소유자가 아니다 (`WORKFLOW_003`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = FORBIDDEN_EXAMPLE))),
            @ApiResponse(responseCode = "404", description = "워크플로우가 없다 (`WORKFLOW_002`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = NOT_FOUND_EXAMPLE))),
            @ApiResponse(responseCode = "409", description = "실행이 진행 중이다 (`WORKFLOW_010`)",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ApiResult.class),
                            examples = @ExampleObject(value = EXECUTING_EXAMPLE)))})
    ApiResult<WorkflowDeleteResponse> delete(
            UserId owner,
            @Parameter(description = "삭제할 워크플로우 ID", required = true,
                    example = "33567ab2-e08b-461b-a0f2-51a857df5ec7") UUID workflowId);

    @Operation(
            summary = "내 워크플로우 목록 조회",
            description = """
                    요청자가 만든 워크플로우를 커서 페이지로 돌려준다. 그래프는 담지 않는다.

                    `nextCursor` 를 다음 요청의 `cursor` 에 그대로 넣는다. `hasNext` 가 `false` 면 `nextCursor` 는 `null` 이다.
                    지금 커서는 오프셋을 감싼 값이라, 페이지를 넘기는 사이에 행이 추가·삭제되면 창이 밀릴 수 있다.""")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 완료")})
    ApiResult<WorkflowPageResponse> findMine(
            UserId owner,
            @Parameter(description = "정렬 기준. 마지막 실행 시각(`LAST_EXECUTED`) 또는 생성 시각(`LATEST`)",
                    example = "LAST_EXECUTED") WorkflowSort sort,
            @Parameter(description = "이름 부분 검색어. 비우면 전체", example = "성적") String keyword,
            @Parameter(description = "이전 응답의 `nextCursor`. 첫 페이지는 비운다", example = "b2Zmc2V0PTEw") String cursor,
            @Parameter(description = "한 번에 가져올 개수. 1 이상 100 이하", example = "10") @Positive @Max(100) int size);
}
