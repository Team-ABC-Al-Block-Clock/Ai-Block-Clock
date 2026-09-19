package com.aiblockclock.support.blockchain;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SsafyTokenMintPropertiesTest {
    private static final String REQUESTOR = "0x1111111111111111111111111111111111111111";
    private static final String RECIPIENT = "0x2222222222222222222222222222222222222222";
    private static final String SIGNATURE = "0x" + "a".repeat(130);

    @Test
    void acceptsCompleteOneTimeMintSettings() {
        SsafyTokenMintProperties properties = properties(BigInteger.ONE, SIGNATURE);

        assertThat(properties.requestorAddress()).isEqualTo(REQUESTOR);
        assertThat(properties.recipientAddress()).isEqualTo(RECIPIENT);
        assertThat(properties.value()).isEqualTo(BigInteger.ONE);
    }

    @Test
    void rejectsInvalidAddressesAndNonPositiveValue() {
        assertThatThrownBy(() -> new SsafyTokenMintProperties(
                "invalid-address", RECIPIENT, BigInteger.ONE, "ABC 242 test mint", SIGNATURE,
                SsafyTokenMintProperties.REQUIRED_CONFIRMATION
        )).isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> properties(BigInteger.ZERO, SIGNATURE))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingSignatureAndIncorrectConfirmation() {
        assertThatThrownBy(() -> properties(BigInteger.ONE, " "))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> new SsafyTokenMintProperties(
                REQUESTOR, RECIPIENT, BigInteger.ONE, "ABC 242 test mint", SIGNATURE, "wrong-confirmation"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requestRejectsMalformedEthereumSignature() {
        assertThatThrownBy(() -> new SsafyTokenMintRequest(
                REQUESTOR, RECIPIENT, BigInteger.ONE, "ABC 242 test mint", "not-a-signature"
        )).isInstanceOf(IllegalArgumentException.class);
    }

    private SsafyTokenMintProperties properties(BigInteger value, String signature) {
        return new SsafyTokenMintProperties(
                REQUESTOR,
                RECIPIENT,
                value,
                "ABC 242 test mint",
                signature,
                SsafyTokenMintProperties.REQUIRED_CONFIRMATION
        );
    }
}
