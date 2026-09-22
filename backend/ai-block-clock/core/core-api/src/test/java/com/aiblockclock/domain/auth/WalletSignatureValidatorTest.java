package com.aiblockclock.domain.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WalletSignatureValidatorTest {
    // ethers.js 공식 문서의 signMessage("Hello World") 결과: Web3j로 자체 생성하지 않은 호환성 벡터.
    // https://docs.ethers.org/v6/single-page/ (Signature Formats)
    private static final String ETHERS_ADDRESS = "0x0A489345F9E9bc5254E18dd14fA7ECfDB2cE5f21";
    private static final String ETHERS_SIGNATURE = "0xa617d0558818c7a479d5063987981b59d6e619332ef52249be8243572ef10868"
            + "07e381afe644d9bb56b213f6e08374c893db308ac1a5ae2bf8b33bcddcb0f76a1b";
    private static final ECKeyPair TEST_KEY_PAIR = ECKeyPair.create(BigInteger.ONE);

    private final WalletSignatureValidator validator = new WalletSignatureValidator();

    @Test
    void JavaScript_라이브러리의_공식_서명을_검증한다() {
        assertThatCode(() -> validator.check(message(ETHERS_ADDRESS, "Hello World"), ETHERS_SIGNATURE))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_복원_번호를_사용한_공식_서명도_검증한다() {
        // https://docs.ethers.org/v5/api/signer/ 의 Wallet.signMessage 예제 (v = 28).
        String signature = "0x14280e5885a19f60e536de50097e96e3738c7acae4e9e62d67272d794b8127d31c"
                + "03d9cd59781d4ee31fb4e1b893bd9b020ec67dfa65cfb51e2bdadbb1de26d91c";

        assertThatCode(() -> validator.check(
                message("0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1", "Hello World"), signature))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {"lower", "upper"})
    void 지갑_주소_대소문자와_관계없이_검증한다(String addressCase) {
        String address = addressCase.equals("lower")
                ? ETHERS_ADDRESS.toLowerCase(Locale.ROOT) : ETHERS_ADDRESS.toUpperCase(Locale.ROOT);

        assertThatCode(() -> validator.check(message(address, "Hello World"), ETHERS_SIGNATURE))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void 복원_번호가_0이나_1인_서명도_검증한다(int recoveryId) {
        String address = recoveryId == 0 ? ETHERS_ADDRESS : "0x71CB05EE1b1F506fF321Da3dac38f25c0c9ce6E1";
        String signature = recoveryId == 0 ? ETHERS_SIGNATURE
                : "0x14280e5885a19f60e536de50097e96e3738c7acae4e9e62d67272d794b8127d31c"
                + "03d9cd59781d4ee31fb4e1b893bd9b020ec67dfa65cfb51e2bdadbb1de26d91c";
        String normalized = signature.substring(0, signature.length() - 2) + "0" + recoveryId;

        assertThatCode(() -> validator.check(message(address, "Hello World"), normalized))
                .doesNotThrowAnyException();
    }

    @Test
    void 다른_지갑의_서명은_거절한다() {
        assertInvalid(message("0x1111111111111111111111111111111111111111", "Hello World"), ETHERS_SIGNATURE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Hello world", "Hello World\n", "Hello World "})
    void 원문의_글자나_공백이나_줄바꿈이_변경되면_거절한다(String changedMessage) {
        assertInvalid(message(ETHERS_ADDRESS, changedMessage), ETHERS_SIGNATURE);
    }

    @Test
    void 한글과_줄바꿈이_포함된_원문을_UTF8로_검증한다() {
        String original = "ABC에 로그인합니다.\nNonce: " + "a".repeat(64);
        String address = "0x" + Keys.getAddress(TEST_KEY_PAIR.getPublicKey());
        String signature = encode(Sign.signPrefixedMessage(original.getBytes(StandardCharsets.UTF_8), TEST_KEY_PAIR));

        assertThatCode(() -> validator.check(message(address, original), signature)).doesNotThrowAnyException();
        assertInvalid(message(address, original.replace("a".repeat(64), "b".repeat(64))), signature);
    }

    @Test
    void Ethereum_메시지_접두부가_없는_서명은_거절한다() {
        String original = "Hello World";
        String address = "0x" + Keys.getAddress(TEST_KEY_PAIR.getPublicKey());
        String signature = encode(Sign.signMessage(original.getBytes(StandardCharsets.UTF_8), TEST_KEY_PAIR));

        assertInvalid(message(address, original), signature);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "0x", "0x123", "0xgg", "not-a-signature"})
    void 해석할_수_없는_서명은_인증_오류로_처리한다(String signature) {
        assertInvalid(message(ETHERS_ADDRESS, "Hello World"), signature);
    }

    @Test
    void 정상_길이여도_잘못된_hex_문자는_거절한다() {
        assertInvalid(message(ETHERS_ADDRESS, "Hello World"), "0xg" + ETHERS_SIGNATURE.substring(3));
    }

    @Test
    void 지원하지_않는_64바이트_서명은_거절한다() {
        assertInvalid(message(ETHERS_ADDRESS, "Hello World"), ETHERS_SIGNATURE.substring(0, 130));
    }

    @ParameterizedTest
    @ValueSource(strings = {"02", "1d", "23", "ff"})
    void 지원하지_않는_복원_번호는_거절한다(String v) {
        assertInvalid(message(ETHERS_ADDRESS, "Hello World"), ETHERS_SIGNATURE.substring(0, 130) + v);
    }

    @ParameterizedTest
    @ValueSource(strings = {"r", "s"})
    void 서명_구성_값이_0이거나_곡선_범위를_벗어나면_거절한다(String component) {
        String zero = "0".repeat(64);
        String order = Numeric.toHexStringNoPrefixZeroPadded(Sign.CURVE_PARAMS.getN(), 64);
        for (String invalid : new String[]{zero, order, "f".repeat(64)}) {
            String changed = component.equals("r")
                    ? "0x" + invalid + ETHERS_SIGNATURE.substring(66)
                    : ETHERS_SIGNATURE.substring(0, 66) + invalid + ETHERS_SIGNATURE.substring(130);
            assertInvalid(message(ETHERS_ADDRESS, "Hello World"), changed);
        }
    }

    private void assertInvalid(WalletAuthMessage authMessage, String signature) {
        assertThatThrownBy(() -> validator.check(authMessage, signature))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        org.assertj.core.api.Assertions.assertThat(exception.getErrorType())
                                .isEqualTo(ErrorType.WALLET_SIGNATURE_INVALID));
    }

    private String encode(Sign.SignatureData signatureData) {
        HexFormat hex = HexFormat.of();

        return "0x" + hex.formatHex(signatureData.getR()) + hex.formatHex(signatureData.getS())
                + hex.formatHex(signatureData.getV());
    }

    private WalletAuthMessage message(String address, String original) {
        Instant issuedAt = Instant.parse("2026-09-17T00:00:00Z");

        return new WalletAuthMessage(address, "a".repeat(64), original, issuedAt, issuedAt.plusSeconds(300), null);
    }
}
