package com.aiblockclock.client.ai;

// 부르는 쪽이 "추천이 없다" 와 "추천을 못 받았다" 를 가르는 기준이다.
public enum RecommendClientError {
    INVALID_CONFIGURATION,
    MALFORMED_RESPONSE,
    REQUEST_FAILED
}
