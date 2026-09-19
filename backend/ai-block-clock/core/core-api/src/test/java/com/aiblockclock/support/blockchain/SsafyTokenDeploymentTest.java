package com.aiblockclock.support.blockchain;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SsafyTokenDeploymentTest {
    private static final String TEST_OWNER = "0x1111111111111111111111111111111111111111";
    private static final String TEST_CONTRACT = "0x2222222222222222222222222222222222222222";
    private MockRestServiceServer queryServer;
    private MockRestServiceServer transactionServer;
    private SsafyStoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder queryBuilder = RestClient.builder().baseUrl("https://sto.ssafy.io");
        RestClient.Builder transactionBuilder = RestClient.builder().baseUrl("https://sto.ssafy.io");
        queryServer = MockRestServiceServer.bindTo(queryBuilder).build();
        transactionServer = MockRestServiceServer.bindTo(transactionBuilder).build();
        client = new SsafyStoClient(queryBuilder.build(), transactionBuilder.build());
    }

    private SsafyTokenDeploymentRequest request() {
        return new SsafyTokenDeploymentRequest(TEST_OWNER, "ABC Payment Token", "abcToken",
                BigInteger.ONE, List.of(TEST_OWNER), List.of("payment"));
    }

    @Test
    void sendsDeploymentSettingsAndReadsReceipt() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "requestor": "0x1111111111111111111111111111111111111111",
                          "name": "ABC Payment Token",
                          "symbol": "abcToken",
                          "granularity": 1,
                          "controllers": ["0x1111111111111111111111111111111111111111"],
                          "partitions": ["payment"]
                        }
                        """))
                .andRespond(withSuccess("""
                        {"status":true,"transactionHash":"test-transaction","contractAddress":"0x2222222222222222222222222222222222222222","blockNumber":123,"logs":[]}
                        """, MediaType.APPLICATION_JSON));
        SsafyTokenDeploymentReceipt receipt = client.deployToken(request());
        assertThat(receipt.status()).isTrue();
        assertThat(receipt.contractAddress()).isEqualTo(TEST_CONTRACT);
        assertThat(receipt.transactionHash()).isEqualTo("test-transaction");
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void rejectsSuccessReceiptWithoutContractAddress() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andRespond(withSuccess("{\"status\":true,\"transactionHash\":\"test-transaction\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.deployToken(request())).isInstanceOf(RestClientException.class);
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void preservesFailedTransactionReceipt() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andRespond(withSuccess("{\"status\":false,\"transactionHash\":\"test-transaction\",\"contractAddress\":null}", MediaType.APPLICATION_JSON));
        assertThat(client.deployToken(request()).status()).isFalse();
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void propagatesApiErrorWithoutAutomaticResubmission() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.deployToken(request())).isInstanceOf(RestClientException.class);
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void readsContractsPreviouslyDeployedByOwner() {
        queryServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + TEST_OWNER))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("""
                        [{
                          "name": "ABC Payment Token",
                          "symbol": "abcToken",
                          "contractAddress": "0x2222222222222222222222222222222222222222",
                          "txId": "test-transaction",
                          "deployedAt": "2026-09-19T00:00:00Z"
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<SsafyDeployedToken> tokens = client.getDeployedTokens(TEST_OWNER);

        assertThat(tokens).hasSize(1);
        assertThat(tokens.getFirst().contractAddress()).isEqualTo(TEST_CONTRACT);
        assertThat(tokens.getFirst().deployedAt()).isEqualTo(java.time.Instant.parse("2026-09-19T00:00:00Z"));
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void rejectsEmptyDeployedTokenResponse() {
        queryServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + TEST_OWNER))
                .andRespond(withSuccess());

        assertThatThrownBy(() -> client.getDeployedTokens(TEST_OWNER))
                .isInstanceOf(RestClientException.class);
        queryServer.verify();
        transactionServer.verify();
    }
}
