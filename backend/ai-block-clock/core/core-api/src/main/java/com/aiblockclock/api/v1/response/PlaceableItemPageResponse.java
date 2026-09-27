package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.PlaceableItem;
import com.aiblockclock.enums.workflow.PlaceableScope;
import com.aiblockclock.enums.workflow.SourceType;
import com.aiblockclock.support.paging.CursorPage;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "캔버스에 놓을 수 있는 항목 목록")
public record PlaceableItemPageResponse(
        @Schema(description = "이번 페이지의 항목")
        List<PlaceableItemResponse> content,

        @Schema(description = "다음 페이지 커서. 마지막 페이지면 null", example = "MjA", nullable = true)
        String nextCursor,

        @Schema(description = "더 불러올 것이 있는지", example = "true")
        boolean hasNext,

        @Schema(description = "요청한 범위")
        PlaceableScope scope
) {
    public static PlaceableItemPageResponse from(CursorPage<PlaceableItem> page, PlaceableScope scope) {
        List<PlaceableItemResponse> content = page.content().stream()
                .map(PlaceableItemResponse::from)
                .toList();

        return new PlaceableItemPageResponse(content, page.nextCursor(), page.hasNext(), scope);
    }

    @Schema(description = "놓을 수 있는 항목 하나")
    public record PlaceableItemResponse(
            @Schema(description = "에이전트 ID. 저장할 때 `sourceId` 로 보낸다",
                    example = "9f1c3b7e-2a44-4d1e-9b77-0b2a6f4e5c13")
            String itemId,

            @Schema(description = "늘 `AGENT`")
            SourceType itemType,

            @Schema(description = "이름", example = "성적 분석")
            String name,

            @Schema(description = "늘 `AGENT`", example = "AGENT")
            String nodeType,

            @Schema(description = "아이콘 주소. 아직 업로드가 없어 늘 null", nullable = true)
            String iconUrl,

            @Schema(description = "내 것인지", example = "true")
            boolean mine,

            @Schema(description = "소유자")
            OwnerResponse owner,

            @Schema(description = "누적 실행 횟수. 성공과 공급자 탓 실패를 더한 값이다", example = "12")
            long executionCount,

            @Schema(description = "실행 1회당 단가. 소수점 오차를 막기 위해 문자열이다", example = "3.000000")
            String price,

            @Schema(description = "한 줄 소개", nullable = true)
            String summary,

            @Schema(description = "판매자가 적어 둔 입력 예시. 프롬프트 입력창의 예시로 쓴다. 적지 않았으면 null",
                    example = "성적표 엑셀을 올려서 학생별 리포트를 만들어 줘", nullable = true)
            String usageExample,

            @Schema(description = "실행을 시작할 때 띄울 인사말. 적지 않았으면 null",
                    example = "무엇을 분석할까요?", nullable = true)
            String onboardingMessage
    ) {
        static PlaceableItemResponse from(PlaceableItem item) {
            return new PlaceableItemResponse(
                    item.itemId().toString(),
                    SourceType.AGENT,
                    item.name(),
                    SourceType.AGENT.name(),
                    null,
                    item.mine(),
                    new OwnerResponse(item.ownerNickname()),
                    item.executionCount(),
                    item.price().value().toPlainString(),
                    item.summary(),
                    item.usageExample(),
                    item.onboardingMessage());
        }
    }

    @Schema(description = "소유자")
    public record OwnerResponse(
            @Schema(description = "닉네임", example = "학원장")
            String nickname
    ) {
    }
}
