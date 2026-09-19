package com.aiblockclock.support.blockchain;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.boot.DefaultApplicationArguments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SsafyTokenMintRunnerTest {
    private static final String CONTRACT = "0x1111111111111111111111111111111111111111";
    private static final String REQUESTOR = "0x2222222222222222222222222222222222222222";
    private static final String RECIPIENT = "0x3333333333333333333333333333333333333333";
    private static final String SIGNATURE = "0x" + "a".repeat(130);

    @Test
    void verifiesTokenAndPermissionsBeforeMintThenChecksChainState() throws Exception {
        SsafyStoClient client = preparedClient();
        when(client.getBalance(CONTRACT, RECIPIENT)).thenReturn(BigInteger.ZERO, BigInteger.ONE);
        when(client.getTotalSupply(CONTRACT)).thenReturn(BigInteger.ZERO, BigInteger.ONE);
        when(client.mintToken(anyString(), any()))
                .thenReturn(new SsafyTokenMintReceipt(true, "test-mint-transaction"));

        runner(client).run(new DefaultApplicationArguments());

        ArgumentCaptor<SsafyTokenMintRequest> captor = ArgumentCaptor.forClass(SsafyTokenMintRequest.class);
        verify(client).mintToken(org.mockito.ArgumentMatchers.eq(CONTRACT), captor.capture());
        assertThat(captor.getValue().requestor()).isEqualTo(REQUESTOR);
        assertThat(captor.getValue().to()).isEqualTo(RECIPIENT);
        assertThat(captor.getValue().value()).isEqualTo(BigInteger.ONE);
        assertThat(captor.getValue().data()).isEqualTo("ABC 242 test mint");
        assertThat(captor.getValue().signature()).isEqualTo(SIGNATURE);

        InOrder order = inOrder(client);
        order.verify(client).getTokenName(CONTRACT);
        order.verify(client).getTokenSymbol(CONTRACT);
        order.verify(client).getGranularity(CONTRACT);
        order.verify(client).isMinter(CONTRACT, REQUESTOR);
        order.verify(client).isIssuable(CONTRACT);
        order.verify(client).getBalance(CONTRACT, RECIPIENT);
        order.verify(client).getTotalSupply(CONTRACT);
        order.verify(client).mintToken(org.mockito.ArgumentMatchers.eq(CONTRACT), any());
        order.verify(client).getBalance(CONTRACT, RECIPIENT);
        order.verify(client).getTotalSupply(CONTRACT);
    }

    @Test
    void stopsBeforeMintWhenContractIsNotAbcToken() {
        SsafyStoClient client = preparedClient();
        when(client.getTokenSymbol(CONTRACT)).thenReturn("OTHER");

        assertThatThrownBy(() -> runner(client).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not match");
        verify(client, never()).mintToken(anyString(), any());
    }

    @Test
    void stopsBeforeMintWhenRequestorIsNotMinter() {
        SsafyStoClient client = preparedClient();
        when(client.isMinter(CONTRACT, REQUESTOR)).thenReturn(false);

        assertThatThrownBy(() -> runner(client).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("minter role");
        verify(client, never()).mintToken(anyString(), any());
    }

    @Test
    void stopsBeforeMintWhenTestRecipientAlreadyHasToken() {
        SsafyStoClient client = preparedClient();
        when(client.getBalance(CONTRACT, RECIPIENT)).thenReturn(BigInteger.ONE);

        assertThatThrownBy(() -> runner(client).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("possibly repeated");
        verify(client, never()).mintToken(anyString(), any());
    }

    @Test
    void demandsManualVerificationWhenReceiptSucceedsButBalanceDoesNotChange() {
        SsafyStoClient client = preparedClient();
        when(client.getBalance(CONTRACT, RECIPIENT)).thenReturn(BigInteger.ZERO, BigInteger.ZERO);
        when(client.getTotalSupply(CONTRACT)).thenReturn(BigInteger.ZERO, BigInteger.ONE);
        when(client.mintToken(anyString(), any()))
                .thenReturn(new SsafyTokenMintReceipt(true, "test-mint-transaction"));

        assertThatThrownBy(() -> runner(client).run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("before any new mint attempt");
        verify(client).mintToken(anyString(), any());
    }

    private SsafyStoClient preparedClient() {
        SsafyStoClient client = mock(SsafyStoClient.class);
        when(client.getTokenName(CONTRACT)).thenReturn(SsafyAbcTokenSpec.NAME);
        when(client.getTokenSymbol(CONTRACT)).thenReturn(SsafyAbcTokenSpec.SYMBOL);
        when(client.getGranularity(CONTRACT)).thenReturn(SsafyAbcTokenSpec.GRANULARITY);
        when(client.isMinter(CONTRACT, REQUESTOR)).thenReturn(true);
        when(client.isIssuable(CONTRACT)).thenReturn(true);
        return client;
    }

    private SsafyTokenMintRunner runner(SsafyStoClient client) {
        return new SsafyTokenMintRunner(
                client,
                new SsafyTokenProperties(CONTRACT),
                new SsafyTokenMintProperties(
                        REQUESTOR,
                        RECIPIENT,
                        BigInteger.ONE,
                        "ABC 242 test mint",
                        SIGNATURE,
                        SsafyTokenMintProperties.REQUIRED_CONFIRMATION
                )
        );
    }
}
