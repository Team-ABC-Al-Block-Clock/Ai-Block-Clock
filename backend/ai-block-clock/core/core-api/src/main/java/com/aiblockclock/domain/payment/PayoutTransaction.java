package com.aiblockclock.domain.payment;

// 출금 한 건을 보내는 서명된 거래다. 해시는 보내기 전에 서명에서 나온다.
// 같은 rawTransaction 을 다시 보내도 체인은 같은 거래로 보므로, 보냈는지 모를 때 그대로 다시 보낸다.
public record PayoutTransaction(String transactionHash, String rawTransaction) {
}
