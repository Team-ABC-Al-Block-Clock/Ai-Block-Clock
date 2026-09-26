package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.util.Locale;
import java.util.Optional;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Hash;
import org.web3j.crypto.RawTransaction;
import org.web3j.crypto.TransactionEncoder;
import org.web3j.utils.Numeric;

// 설정된 지갑으로 거래에 서명한다. 네트워크를 쓰지 않는다 — 보내는 일은 SsafyChainClient 가 한다.
// 키를 이 클래스 밖으로 내보내지 않고 주소와 서명 결과만 준다.
@Component
@RequiredArgsConstructor
public class SsafyChainSigner {
    // SSAFY 체인은 가스 가격이 0 이다(eth_gasPrice). 보관 지갑에 가스용 코인을 넣지 않아도 된다
    private static final BigInteger GAS_PRICE = BigInteger.ZERO;

    private final SsafyChainSignerProperties properties;

    // 키가 없으면 비어 있다. 지급을 켜는 쪽이 기동할 때 이 주소를 보관 지갑과 대조한다
    public Optional<String> address() {
        return credentials().map(signer -> signer.getAddress().toLowerCase(Locale.ROOT));
    }

    // abcToken 을 받는 주소로 보내는 레거시 거래(EIP-155)에 서명한다. 체인 ID 를 넣어 다른 체인에서 다시 쓰이지 못하게 한다.
    public SignedChainTransaction signTokenTransfer(
            long chainId,
            String tokenContractAddress,
            String toAddress,
            BigInteger rawAmount,
            BigInteger nonce,
            BigInteger gasLimit
    ) {
        Credentials credentials = credentials().orElseThrow(() -> new BlockchainClientException(
                BlockchainClientError.INVALID_CONFIGURATION, "ssafy.chain.signer.private-key is not configured"));

        SsafyBlockchainAddress.requireValid(tokenContractAddress, "tokenContractAddress");

        RawTransaction transaction = RawTransaction.createTransaction(nonce, GAS_PRICE, gasLimit, tokenContractAddress,
                BigInteger.ZERO, TokenTransferCall.encode(toAddress, rawAmount));
        byte[] signed = TransactionEncoder.signMessage(transaction, chainId, credentials);

        return new SignedChainTransaction(Numeric.toHexString(signed), Numeric.toHexString(Hash.sha3(signed)));
    }

    // 키를 필드로 쥐고 있지 않고 쓸 때마다 설정에서 만든다. 서명은 출금 한 건에 한 번이라 비용이 문제 되지 않는다
    private Optional<Credentials> credentials() {
        return properties.isConfigured() ? Optional.of(Credentials.create(properties.privateKey())) : Optional.empty();
    }
}
