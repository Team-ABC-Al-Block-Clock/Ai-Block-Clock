package com.aiblockclock.enums.agent;

// 원본 기록이 검증되지 않은 까닭이다. 여럿이 겹치면 위에 있는 것 하나만 댄다 — 위일수록 더 구체적이다.
// NOT_CONFIRMED 는 대조하지 않았다는 뜻이라 조회 응답에만 나오고 DB 에는 남지 않는다.
public enum AgentIntegrityMismatchReason {
    // 아직 체인에 적히지 않았다
    NOT_CONFIRMED,
    // 확정했다는데 체인에 그 이름의 기록이 없다
    NOT_ON_CHAIN,
    // DB 의 원작자 지갑이 체인에 적힌 지갑과 다르다
    OWNER_MISMATCH,
    // 지금 내용으로 다시 만든 해시가 체인의 해시와 다르다 — 공개한 뒤 DB 의 내용이 바뀌었다
    METADATA_CHANGED,
    // 내용은 체인과 맞는데, 적을 때 DB 에 남겨 둔 해시가 체인과 다르다 — 기록 칸이 바뀌었다
    RECORD_MISMATCH
}
