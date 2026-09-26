package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.util.List;

import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Bool;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.generated.Uint256;

// abcToken 의 transfer(받는 주소, 수량) 호출 본문이다. 가스를 잴 때와 서명할 때 같은 본문을 써야 잰 값이 맞는다.
final class TokenTransferCall {

    private TokenTransferCall() {
    }

    static String encode(String toAddress, BigInteger rawAmount) {
        SsafyBlockchainAddress.requireValid(toAddress, "toAddress");

        if (rawAmount == null || rawAmount.signum() <= 0) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_AMOUNT,
                    "rawAmount must be a positive integer");
        }

        return FunctionEncoder.encode(new Function("transfer",
                List.of(new Address(toAddress), new Uint256(rawAmount)),
                List.of(new TypeReference<Bool>() {
                })));
    }
}
