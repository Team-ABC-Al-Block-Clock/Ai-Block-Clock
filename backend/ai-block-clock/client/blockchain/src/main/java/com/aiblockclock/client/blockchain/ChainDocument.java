package com.aiblockclock.client.blockchain;

// 등기부 토큰의 문서 칸 하나에 적힌 값이다. 무엇을 뜻하는지(원작자 · 메타데이터 해시)는 적은 쪽(core-api)이 푼다.
public record ChainDocument(
        String uri,
        String hash
) {
}
