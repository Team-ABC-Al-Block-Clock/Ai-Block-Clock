package com.aiblockclock.client.blockchain;

import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SsafyTokenQueryTest {
    private static final String CONTRACT_ADDRESS = "0x1111111111111111111111111111111111111111";
    private static final String HOLDER_ADDRESS = "0x2222222222222222222222222222222222222222";

    private MockRestServiceServer queryServer;
    private SsafyStoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder queryBuilder = RestClient.builder().baseUrl("https://sto.ssafy.io");
        queryServer = MockRestServiceServer.bindTo(queryBuilder).build();
        client = new SsafyStoClient(queryBuilder.build());
    }

    @Test
    void readsTokenIdentityAndGranularity() {
        expectQuery("/name", "ABC Payment Token");
        expectQuery("/symbol", "abcToken");
        expectQuery("/granularity", "1");

        assertThat(client.getTokenName(CONTRACT_ADDRESS)).isEqualTo("ABC Payment Token");
        assertThat(client.getTokenSymbol(CONTRACT_ADDRESS)).isEqualTo("abcToken");
        assertThat(client.getGranularity(CONTRACT_ADDRESS)).isEqualTo(BigInteger.ONE);
        verifyAllQueriesWereMade();
    }

    @Test
    void readsSupplyAndHolderBalanceAsArbitraryPrecisionIntegers() {
        String largeAmount = "123456789012345678901234567890";
        expectQuery("/total-supply", largeAmount);
        queryServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT_ADDRESS
                        + "/balance?holderAddress=" + HOLDER_ADDRESS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("0", MediaType.APPLICATION_JSON));

        assertThat(client.getTotalSupply(CONTRACT_ADDRESS)).isEqualTo(new BigInteger(largeAmount));
        assertThat(client.getBalance(CONTRACT_ADDRESS, HOLDER_ADDRESS)).isZero();
        verifyAllQueriesWereMade();
    }

    @Test
    void readsMinterRoleAndIssuanceStatusStrictly() {
        queryServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT_ADDRESS
                        + "/roles/minter?address=" + HOLDER_ADDRESS))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("true", MediaType.APPLICATION_JSON));
        expectQuery("/status/issuance", "false");

        assertThat(client.isMinter(CONTRACT_ADDRESS, HOLDER_ADDRESS)).isTrue();
        assertThat(client.isIssuable(CONTRACT_ADDRESS)).isFalse();
        verifyAllQueriesWereMade();
    }

    @Test
    void rejectsEmptyTextAndMalformedBooleanResponses() {
        expectQuery("/name", "  ");
        expectQuery("/status/issuance", "healthy");

        assertThatThrownBy(() -> client.getTokenName(CONTRACT_ADDRESS))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.MALFORMED_RESPONSE));
        assertThatThrownBy(() -> client.isIssuable(CONTRACT_ADDRESS))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.MALFORMED_RESPONSE));
        verifyAllQueriesWereMade();
    }

    @Test
    void rejectsNegativeOrNonNumericAmounts() {
        expectQuery("/total-supply", "-1");
        queryServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT_ADDRESS
                        + "/balance?holderAddress=" + HOLDER_ADDRESS))
                .andRespond(withSuccess("not-a-number", MediaType.TEXT_PLAIN));

        assertThatThrownBy(() -> client.getTotalSupply(CONTRACT_ADDRESS))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.MALFORMED_RESPONSE));
        assertThatThrownBy(() -> client.getBalance(CONTRACT_ADDRESS, HOLDER_ADDRESS))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.MALFORMED_RESPONSE));
        verifyAllQueriesWereMade();
    }

    @Test
    void rejectsInvalidAddressesBeforeSendingRequest() {
        assertThatThrownBy(() -> client.getBalance("invalid-contract", HOLDER_ADDRESS))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_ADDRESS));
        assertThatThrownBy(() -> client.isMinter(CONTRACT_ADDRESS, "invalid-holder"))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_ADDRESS));
        verifyAllQueriesWereMade();
    }

    private void expectQuery(String suffix, String response) {
        queryServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT_ADDRESS + suffix))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }

    private void verifyAllQueriesWereMade() {
        queryServer.verify();
    }
}
