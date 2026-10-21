package com.aiblockclock.client.ai;

// 부르는 쪽이 "결과가 없다" 와 "결과를 못 받았다" 를 가르는 기준이다.
// REQUEST_FAILED 만 상대가 무엇을 했는지 알 수 없는 상황이고, 나머지는 실패가 확인된 상황이다.
public enum AiClientError {
    INVALID_CONFIGURATION,
    MALFORMED_RESPONSE,
    REQUEST_FAILED,
    // 엔진이 그 실행을 모른다(404 RUN_NOT_FOUND). 아직 받지 않았거나(큐에서 기다리는 중 · 다시 배달되기 전)
    // 재시작 · 재배포로 메모리에서 사라졌다. 어느 쪽인지는 부르는 쪽이 넘긴 시각으로 가른다.
    RUN_NOT_FOUND
}
