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
    private MockRestServiceServer server;
    private SsafyStoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sto.ssafy.io");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new SsafyStoClient(builder.build());
    }

    private SsafyTokenDeploymentRequest request() {
        return new SsafyTokenDeploymentRequest(TEST_OWNER, "ABC Payment Token", "abcToken",
                BigInteger.ONE, List.of(TEST_OWNER), List.of("payment"));
    }

    @Test
    void sendsDeploymentSettingsAndReadsReceipt() {
        server.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
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
        server.verify();
    }

    @Test
    void rejectsSuccessReceiptWithoutContractAddress() {
        server.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andRespond(withSuccess("{\"status\":true,\"transactionHash\":\"test-transaction\"}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.deployToken(request())).isInstanceOf(RestClientException.class);
        server.verify();
    }

    @Test
    void preservesFailedTransactionReceipt() {
        server.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andRespond(withSuccess("{\"status\":false,\"transactionHash\":\"test-transaction\",\"contractAddress\":null}", MediaType.APPLICATION_JSON));
        assertThat(client.deployToken(request()).status()).isFalse();
        server.verify();
    }

    @Test
    void propagatesApiErrorWithoutAutomaticResubmission() {
        server.expect(requestTo("https://sto.ssafy.io/v1/tokens"))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));
        assertThatThrownBy(() -> client.deployToken(request())).isInstanceOf(RestClientException.class);
        server.verify();
    }
}
