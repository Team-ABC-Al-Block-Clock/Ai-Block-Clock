package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.security.SignatureException;

import org.junit.jupiter.api.Test;
import org.web3j.crypto.Hash;
import org.web3j.crypto.SignedRawTransaction;
import org.web3j.crypto.TransactionDecoder;
import org.web3j.utils.Numeric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsafyChainSignerTest {
    // Hardhat 이 모든 개발자에게 똑같이 주는 공개 테스트 계정이다. 어느 체인에도 자산이 없다
    private static final String TEST_PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String TEST_ADDRESS = "0xf39fd6e51aad88f6f4ce6ab8827279cfffb92266";
    private static final String TOKEN = "0x56dc097c14836473f4ad4bd675a3d01be5e4c63c";
    private static final String RECEIVER = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b";
    private static final long CHAIN_ID = 31221L;

    private final SsafyChainSigner signer = new SsafyChainSigner(new SsafyChainSignerProperties(TEST_PRIVATE_KEY));

    @Test
    void 설정한_키의_지갑_주소를_소문자로_준다() {
        assertThat(signer.address()).contains(TEST_ADDRESS);
    }

    @Test
    void 키가_없으면_주소가_비어_있고_서명하지_않는다() {
        SsafyChainSigner unconfigured = new SsafyChainSigner(new SsafyChainSignerProperties(""));

        assertThat(unconfigured.address()).isEmpty();
        assertThatThrownBy(() -> unconfigured.signTokenTransfer(
                CHAIN_ID, TOKEN, RECEIVER, BigInteger.TEN, BigInteger.ZERO, BigInteger.valueOf(60_000)))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_CONFIGURATION));
    }

    @Test
    void 토큰_컨트랙트로_가는_transfer_를_가스_가격_0_과_체인_ID_를_넣어_서명한다() throws SignatureException {
        BigInteger rawAmount = BigInteger.TEN.pow(19);

        SignedChainTransaction signed = signer.signTokenTransfer(
                CHAIN_ID, TOKEN, RECEIVER, rawAmount, BigInteger.valueOf(3), BigInteger.valueOf(60_000));
        SignedRawTransaction decoded = (SignedRawTransaction) TransactionDecoder.decode(signed.rawTransaction());

        assertThat(decoded.getFrom()).isEqualTo(TEST_ADDRESS);
        assertThat(decoded.getChainId()).isEqualTo(CHAIN_ID);
        assertThat(decoded.getTo()).isEqualTo(TOKEN);
        assertThat(decoded.getNonce()).isEqualTo(BigInteger.valueOf(3));
        assertThat(decoded.getGasPrice()).isZero();
        assertThat(decoded.getGasLimit()).isEqualTo(BigInteger.valueOf(60_000));
        assertThat(decoded.getValue()).isZero();
        assertThat(Numeric.prependHexPrefix(decoded.getData()))
                .isEqualTo(TokenTransferCall.encode(RECEIVER, rawAmount));
    }

    @Test
    void 해시는_서명한_바이트에서_나와_보내기_전에_안다() {
        SignedChainTransaction signed = signer.signTokenTransfer(
                CHAIN_ID, TOKEN, RECEIVER, BigInteger.TEN, BigInteger.ZERO, BigInteger.valueOf(60_000));

        assertThat(signed.transactionHash())
                .isEqualTo(Numeric.toHexString(Hash.sha3(Numeric.hexStringToByteArray(signed.rawTransaction()))))
                .matches("^0x[0-9a-f]{64}$");
    }

    @Test
    void 같은_내용을_다시_서명하면_같은_거래가_나온다() {
        SignedChainTransaction first = signer.signTokenTransfer(
                CHAIN_ID, TOKEN, RECEIVER, BigInteger.TEN, BigInteger.ZERO, BigInteger.valueOf(60_000));
        SignedChainTransaction again = signer.signTokenTransfer(
                CHAIN_ID, TOKEN, RECEIVER, BigInteger.TEN, BigInteger.ZERO, BigInteger.valueOf(60_000));

        assertThat(again).isEqualTo(first);
    }

    @Test
    void 영_이하의_수량은_서명하지_않는다() {
        assertThatThrownBy(() -> signer.signTokenTransfer(
                CHAIN_ID, TOKEN, RECEIVER, BigInteger.ZERO, BigInteger.ZERO, BigInteger.valueOf(60_000)))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_AMOUNT));
    }

    @Test
    void 받는_주소가_틀리면_서명하지_않는다() {
        assertThatThrownBy(() -> signer.signTokenTransfer(
                CHAIN_ID, TOKEN, "0x1234", BigInteger.TEN, BigInteger.ZERO, BigInteger.valueOf(60_000)))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_ADDRESS));
    }
}
