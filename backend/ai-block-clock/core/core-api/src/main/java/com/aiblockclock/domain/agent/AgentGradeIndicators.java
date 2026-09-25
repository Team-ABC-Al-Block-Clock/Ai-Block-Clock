package com.aiblockclock.domain.agent;

// 등급 재료에서 계산한 지표다. 종합 점수(PAY-019)와 등급 허들(PAY-020)이 읽는다.
//   adjustedRating  보정 평점. 1 ~ 5. 평가가 적을수록 전체 평균 쪽으로 당겨진다 (PAY-016)
//   ratingScore     보정 평점을 0 ~ 100 으로 옮긴 것. 사용량과 같은 눈금에서 더하려는 것이다
//   usageScore      누적 성공 실행 수를 로그 눈금으로 0 ~ 100 에 놓은 것 (PAY-017)
//   successRate     성공 / (성공 + 공급자 탓 실패). 0 ~ 1. 센 실행이 없으면 1 (PAY-018)
//   successPenalty  successRate 의 거듭제곱. 0 ~ 1. 종합 점수에 곱한다 (PAY-018)
public record AgentGradeIndicators(
        double adjustedRating,
        double ratingScore,
        double usageScore,
        double successRate,
        double successPenalty
) {
}
