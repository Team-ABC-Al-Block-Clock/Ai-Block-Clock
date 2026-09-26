package com.aiblockclock.enums.payment;

// retryable 은 "같은 해시로 다시 요청하면 새로 확인할 가치가 있는가" 다.
// 요청이 거래와 어긋나 닫힌 것(누가 · 얼마를 · 언제)은 다시 볼 수 있고, 거래 자체의 사실(되돌려짐 · 받는 곳 · 토큰 · 체인)은
// 다시 봐도 같으니 닫힌 채 둔다. 다시 확인해도 ABC 는 체인에 찍힌 보낸 사람에게만 적립되므로 남이 가로챌 수 없다.
public enum ChargeStatusReason {
    ONCHAIN_TRANSACTION_FAILED(false),
    VERIFICATION_TIMEOUT(true),
    CHAIN_ID_MISMATCH(false),
    TRANSACTION_HASH_MISMATCH(false),
    SENDER_WALLET_MISMATCH(true),
    CUSTODY_ADDRESS_MISMATCH(false),
    DEPOSIT_TOKEN_CONTRACT_MISMATCH(false),
    AMOUNT_MISMATCH(true),
    TRANSACTION_ALREADY_USED(false),
    USER_CANCELED(false);

    private final boolean retryable;

    ChargeStatusReason(boolean retryable) {
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
