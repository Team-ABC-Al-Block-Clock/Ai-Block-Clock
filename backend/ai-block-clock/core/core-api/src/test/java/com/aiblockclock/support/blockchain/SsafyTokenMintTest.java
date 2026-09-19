package com.aiblockclock.support.blockchain;

import java.math.BigInteger;

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

class SsafyTokenMintTest {
    private static final String CONTRACT = "0x1111111111111111111111111111111111111111";
    private static final String REQUESTOR = "0x2222222222222222222222222222222222222222";
    private static final String RECIPIENT = "0x3333333333333333333333333333333333333333";
    private static final String SIGNATURE = "0x" + "a".repeat(130);

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

    @Test
    void sendsSignedMintRequestAndReadsTransactionReceipt() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {
                          "requestor": "0x2222222222222222222222222222222222222222",
                          "to": "0x3333333333333333333333333333333333333333",
                          "value": 1,
                          "data": "ABC 242 test mint",
                          "signature": "%s"
                        }
                        """.formatted(SIGNATURE)))
                .andRespond(withSuccess("""
                        {
                          "status": true,
                          "transactionHash": "test-mint-transaction",
                          "blockNumber": 123,
                          "events": {}
                        }
                        """, MediaType.APPLICATION_JSON));

        SsafyTokenMintReceipt receipt = client.mintToken(CONTRACT, request());

        assertThat(receipt.status()).isTrue();
        assertThat(receipt.transactionHash()).isEqualTo("test-mint-transaction");
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void rejectsIncompleteReceiptBecauseResubmissionCouldDuplicateMint() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT))
                .andRespond(withSuccess("{\"status\":true}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.mintToken(CONTRACT, request()))
                .isInstanceOf(RestClientException.class)
                .hasMessageContaining("verify chain outcome before resubmitting");
        queryServer.verify();
        transactionServer.verify();
    }

    @Test
    void propagatesApiErrorWithoutAutomaticallyRepeatingMint() {
        transactionServer.expect(requestTo("https://sto.ssafy.io/v1/tokens/" + CONTRACT))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST));

        assertThatThrownBy(() -> client.mintToken(CONTRACT, request()))
                .isInstanceOf(RestClientException.class);
        queryServer.verify();
        transactionServer.verify();
    }

    private SsafyTokenMintRequest request() {
        return new SsafyTokenMintRequest(
                REQUESTOR, RECIPIENT, BigInteger.ONE, "ABC 242 test mint", SIGNATURE
        );
    }
}
