package com.aiblockclock.client.blockchain;

import java.math.BigInteger;

// 영수증 로그에 남은 토큰 전송 한 건이다. 주소는 소문자로, 수량은 체인에 적힌 정수(최소 단위) 그대로 둔다.
// 소수 자릿수로 나누는 것은 토큰을 아는 쪽(core-api)이 한다.
public record ChainTokenTransfer(
        String tokenContractAddress,
        String fromAddress,
        String toAddress,
        BigInteger rawAmount
) {
}
