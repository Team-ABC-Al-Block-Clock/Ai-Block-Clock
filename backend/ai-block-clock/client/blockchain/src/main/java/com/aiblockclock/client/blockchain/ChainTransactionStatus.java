package com.aiblockclock.client.blockchain;

// 체인이 거래를 어디까지 처리했는지다. 노드에 닿지 못한 경우는 여기 없다 — 그때는 REQUEST_FAILED 로 던진다.
public enum ChainTransactionStatus {
    // 노드가 이 해시를 모른다. 아직 퍼지지 않았거나 애초에 보내지 않은 거래다
    NOT_FOUND,
    // 노드는 알지만 아직 블록에 적히지 않았다
    PENDING,
    // 블록에 적혔고 실행도 성공했다
    SUCCEEDED,
    // 블록에 적혔지만 실행이 되돌려졌다. 토큰은 움직이지 않았다
    FAILED
}
