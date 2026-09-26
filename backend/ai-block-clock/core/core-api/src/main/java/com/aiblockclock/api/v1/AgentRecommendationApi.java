package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.AgentRecommendationResponse;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

@Tag(name = "마켓", description = "공개된 에이전트를 찾는다.")
public interface AgentRecommendationApi {
    @Operation(summary = "자연어로 에이전트 찾기",
            description = """
                    하고 싶은 일을 자기 말로 적으면 알맞은 에이전트를 찾아 준다.
                    이름 검색과 다르다 — **단어가 겹치지 않아도 찾는다.**
                    "부모님께 보낼 안내문" 으로 `가정통신문 작성기` 가 나온다.

                    찾는 일은 추천 엔진이 하고 우리는 중계한다. 그 엔진이 들고 있는 목록은 우리 DB 에서
                    파생된 사본이라 낡아 있을 수 있어, 돌려받은 것 중 **지금도 공개된 것만** 내려준다.
                    그래서 결과가 요청한 개수보다 적을 수 있고, 아직 아무것도 색인되지 않았으면 빈 목록이다.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "찾은 순서대로. 없으면 빈 목록"),
            @ApiResponse(responseCode = "400", description = "검색어가 비었거나 200자를 넘었거나, 개수가 범위를 벗어났다 (`COMMON_001`)",
                    content = @Content),
            @ApiResponse(responseCode = "502", description = "추천 엔진에 닿지 못했다 (`RECOMMEND_001`)",
                    content = @Content)
    })
    ApiResult<AgentRecommendationResponse> recommend(
            @Parameter(description = "하고 싶은 일을 적은 문장. 최대 200자", required = true,
                    example = "학원 성적을 분석해서 학부모에게 보낼 리포트를 만들고 싶어")
            @NotBlank @Size(max = 200) String query,

            @Parameter(description = "받을 개수. 최대 20", example = "8")
            @Positive @Max(20) int size);
}
