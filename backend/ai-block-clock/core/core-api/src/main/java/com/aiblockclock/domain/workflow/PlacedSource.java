package com.aiblockclock.domain.workflow;

/**
 * 배치된 자리가 가리키는 원본을, 캔버스가 그릴 만큼만 푼 것.
 *
 * <p>{@code mine} 은 "이 자리가 판매 등록을 막지 않는가" 에 대한 서버의 판정이다. 지금 규칙은
 * 원본의 주인이 요청자와 같은가 하나뿐이라 이름 그대로지만, 판정 주체는 어디까지나 서버다.
 * 화면은 이 값을 접어 "판매 등록 가능 / 불가" 를 띄우므로
 * {@link com.aiblockclock.domain.agent.AgentVersionPolicy} 가 막는 것과 답이 같아야 한다.
 */
public record PlacedSource(String name, boolean mine) {
    /** 원본 행을 찾지 못한 자리. 이름을 모르고, 판매 판정은 막는 쪽으로 둔다. */
    public static PlacedSource unknown() {
        return new PlacedSource(null, false);
    }
}
