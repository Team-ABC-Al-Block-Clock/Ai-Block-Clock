package com.aiblockclock.client.blockchain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// 토큰증권 API 의 문서 조회 응답(GetDocumentResponseDto)이다. timestamp 는 쓰지 않아 읽지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
record SsafyStoDocument(
        String docUri,
        String docHash
) {
}
