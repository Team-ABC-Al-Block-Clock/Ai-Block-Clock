package com.aiblockclock.client.ai;

// 부르는 쪽이 "결과가 없다" 와 "결과를 못 받았다" 를 가르는 기준이다.
// REQUEST_FAILED 만 상대가 무엇을 했는지 알 수 없는 상황이고, 나머지는 실패가 확인된 상황이다.
public enum AiClientError {
    INVALID_CONFIGURATION,
    MALFORMED_RESPONSE,
    REQUEST_FAILED
}
