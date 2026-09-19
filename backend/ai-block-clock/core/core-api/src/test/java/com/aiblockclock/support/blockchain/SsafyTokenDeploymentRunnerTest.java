package com.aiblockclock.support.blockchain;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsafyTokenDeploymentRunnerTest {
    private static final String OWNER_ADDRESS = "0x1111111111111111111111111111111111111111";

    @Test
    void createsFixedAbcTokenDeploymentRequest() throws Exception {
        SsafyStoClient client = mock(SsafyStoClient.class);
        SsafyTokenDeploymentProperties properties = properties();
        when(client.deployToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SsafyTokenDeploymentReceipt(true, "test-transaction", "test-contract"));

        new SsafyTokenDeploymentRunner(client, properties).run(new DefaultApplicationArguments());

        ArgumentCaptor<SsafyTokenDeploymentRequest> captor = ArgumentCaptor.forClass(SsafyTokenDeploymentRequest.class);
        verify(client).deployToken(captor.capture());
        SsafyTokenDeploymentRequest request = captor.getValue();
        assertThat(request.requestor()).isEqualTo(OWNER_ADDRESS);
        assertThat(request.name()).isEqualTo("ABC Payment Token");
        assertThat(request.symbol()).isEqualTo("abcToken");
        assertThat(request.granularity()).isEqualTo(BigInteger.ONE);
        assertThat(request.controllers()).containsExactly(OWNER_ADDRESS);
        assertThat(request.partitions()).containsExactly("payment");
    }

    @Test
    void stopsWhenDeploymentReceiptReportsFailure() {
        SsafyStoClient client = mock(SsafyStoClient.class);
        when(client.deployToken(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new SsafyTokenDeploymentReceipt(false, "failed-transaction", null));

        SsafyTokenDeploymentRunner runner = new SsafyTokenDeploymentRunner(client, properties());

        assertThatThrownBy(() -> runner.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("failed-transaction");
    }

    @Test
    void rejectsInvalidOwnerAddressBeforeRunnerCanStart() {
        assertThatThrownBy(() -> new SsafyTokenDeploymentProperties(
                "not-a-wallet", SsafyTokenDeploymentProperties.REQUIRED_CONFIRMATION))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsIncorrectConfirmationBeforeRunnerCanStart() {
        assertThatThrownBy(() -> new SsafyTokenDeploymentProperties(OWNER_ADDRESS, "wrong-confirmation"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private SsafyTokenDeploymentProperties properties() {
        return new SsafyTokenDeploymentProperties(
                OWNER_ADDRESS,
                SsafyTokenDeploymentProperties.REQUIRED_CONFIRMATION
        );
    }
}
