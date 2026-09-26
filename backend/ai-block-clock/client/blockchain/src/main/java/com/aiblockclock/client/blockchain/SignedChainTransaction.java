package com.aiblockclock.client.blockchain;

// 서명을 마친 거래 한 건이다. 해시는 서명한 바이트에서 바로 나와, 보내기 전에 알 수 있다.
// 같은 rawTransaction 을 다시 보내면 체인은 같은 거래로 본다. 보냈는지 모를 때 다시 보내도 두 번 처리되지 않는다.
public record SignedChainTransaction(String rawTransaction, String transactionHash) {
}
