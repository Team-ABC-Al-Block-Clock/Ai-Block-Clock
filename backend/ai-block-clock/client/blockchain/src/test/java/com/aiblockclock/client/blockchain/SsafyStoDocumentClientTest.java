package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class SsafyStoDocumentClientTest {
    // 널리 알려진 테스트 전용 키다(Hardhat 0번 계정). 실제 지갑이 아니다
    private static final String TEST_PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final String TEST_SIGNER = "0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266";
    private static final String REGISTRY = "0x1111111111111111111111111111111111111111";
    private static final String DOCUMENTS_URL = "https://sto.ssafy.io/v1/tokens/" + REGISTRY + "/documents";
    private static final String NAME = "EjRWeJASNFZ4kBI0VniQEg-v1";
    private static final String URI_VALUE = "abc://agents/12345678-9012-3456-7890-123456789012/versions/1"
            + "?owner=0x2222222222222222222222222222222222222222";
    private static final String HASH = "0x" + "ab".repeat(32);
    private static final String TRANSACTION_HASH = "0x" + "cd".repeat(32);

    private MockRestServiceServer server;
    private SsafyStoDocumentClient client;

    @BeforeEach
    void setUp() {
        client = clientFor(SsafyAgentRegistry.from(
                new SsafyAgentRegistryProperties(REGISTRY, TEST_PRIVATE_KEY, Duration.ofSeconds(30))));
    }

    @Test
    void 문서를_쓰면_서명을_붙여_보내고_성공한_영수증을_돌려준다() throws Exception {
        AtomicReference<String> sentBody = new AtomicReference<>();
        server.expect(requestTo(DOCUMENTS_URL))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(request -> sentBody.set(((MockClientHttpRequest) request).getBodyAsString()))
                .andRespond(withSuccess(receipt(true), MediaType.APPLICATION_JSON));

        ChainDocumentReceipt receipt = client.setDocument(NAME, URI_VALUE, HASH);

        assertThat(receipt.contractAddress()).isEqualTo(REGISTRY);
        assertThat(receipt.transactionHash()).isEqualTo(TRANSACTION_HASH);
        assertThat(receipt.blockNumber()).isEqualTo(BigInteger.valueOf(1234));

        String signedPart = "{\"requestor\":\"" + TEST_SIGNER + "\",\"name\":\"" + NAME
                + "\",\"docUri\":\"" + URI_VALUE + "\",\"docHash\":\"" + HASH + "\"}";
        String signature = sentBody.get().replaceAll(".*\"signature\":\"(0x[0-9a-f]{130})\"}$", "$1");

        assertThat(sentBody.get()).startsWith(signedPart.substring(0, signedPart.length() - 1) + ",\"signature\":\"");
        assertThat(signerOf(signedPart, signature)).isEqualToIgnoringCase(TEST_SIGNER);
        server.verify();
    }

    @Test
    void 되돌려진_거래는_영수증이_아니라_실패로_알린다() {
        server.expect(requestTo(DOCUMENTS_URL))
                .andRespond(withSuccess(receipt(false), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.setDocument(NAME, URI_VALUE, HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.TRANSACTION_REVERTED));
    }

    @Test
    void 토큰증권_API_가_거절하면_응답_본문을_실어_알린다() {
        server.expect(requestTo(DOCUMENTS_URL))
                .andRespond(withStatus(HttpStatus.BAD_REQUEST)
                        .body("{\"message\":\"invalid signature\"}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.setDocument(NAME, URI_VALUE, HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception -> {
                    assertThat(exception.getError()).isEqualTo(BlockchainClientError.REQUEST_FAILED);
                    assertThat(exception.getMessage()).contains("400").contains("invalid signature");
                });
    }

    @Test
    void 영수증에_블록_번호가_없으면_깨진_응답으로_본다() {
        server.expect(requestTo(DOCUMENTS_URL))
                .andRespond(withSuccess("{\"status\":true,\"transactionHash\":\"" + TRANSACTION_HASH + "\"}",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.setDocument(NAME, URI_VALUE, HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.MALFORMED_RESPONSE));
    }

    @Test
    void 등기부가_설정되지_않은_서버는_보내지_않는다() {
        SsafyStoDocumentClient unconfigured = clientFor(null);

        assertThatThrownBy(() -> unconfigured.setDocument(NAME, URI_VALUE, HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_CONFIGURATION));
    }

    @Test
    void 서른한_자를_넘는_문서_이름은_보내지_않는다() {
        assertThatThrownBy(() -> client.setDocument("a".repeat(32), URI_VALUE, HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_DOCUMENT));
    }

    @Test
    void 따옴표처럼_본문을_바꾸는_글자가_든_주소는_보내지_않는다() {
        assertThatThrownBy(() -> client.setDocument(NAME, "abc://\"owner", HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_DOCUMENT));
    }

    @Test
    void 적힌_문서를_읽는다() {
        server.expect(requestTo(DOCUMENTS_URL + "/" + NAME))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"docUri\":\"" + URI_VALUE + "\",\"docHash\":\"" + HASH
                        + "\",\"timestamp\":\"1758873600\"}", MediaType.APPLICATION_JSON));

        assertThat(client.findDocument(REGISTRY, NAME))
                .hasValueSatisfying(document -> {
                    assertThat(document.uri()).isEqualTo(URI_VALUE);
                    assertThat(document.hash()).isEqualTo(HASH);
                });
    }

    @Test
    void 없는_문서는_빈_값이다() {
        server.expect(requestTo(DOCUMENTS_URL + "/" + NAME))
                .andRespond(withStatus(HttpStatus.NOT_FOUND)
                        .body("{\"message\":\"Not Found\",\"statusCode\":404}")
                        .contentType(MediaType.APPLICATION_JSON));

        assertThat(client.findDocument(REGISTRY, NAME)).isEmpty();
    }

    @Test
    void 문서를_읽지_못하면_없다고_하지_않고_결과_불명으로_알린다() {
        server.expect(requestTo(DOCUMENTS_URL + "/" + NAME))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> client.findDocument(REGISTRY, NAME))
                .isInstanceOfSatisfying(BlockchainClientException.class, exception ->
                        assertThat(exception.getError()).isEqualTo(BlockchainClientError.REQUEST_FAILED));
    }

    @Test
    void 누구나_열어_볼_수_있는_문서_조회_주소를_만든다() {
        assertThat(client.documentUrl(REGISTRY, NAME)).hasToString(DOCUMENTS_URL + "/" + NAME);
    }

    private SsafyStoDocumentClient clientFor(SsafyAgentRegistry registry) {
        RestClient.Builder builder = RestClient.builder().baseUrl("https://sto.ssafy.io");
        server = MockRestServiceServer.bindTo(builder).build();

        return new SsafyStoDocumentClient(builder.build(), builder.build(), URI.create("https://sto.ssafy.io"),
                registry);
    }

    private static String receipt(boolean status) {
        return "{\"status\":" + status + ",\"transactionHash\":\"" + TRANSACTION_HASH
                + "\",\"blockNumber\":1234,\"from\":\"0xAF42f0fa6B322312719fd17e5BEA817616751172\",\"logs\":[]}";
    }

    private static String signerOf(String message, String signatureHex) throws Exception {
        byte[] signature = Numeric.hexStringToByteArray(signatureHex);
        Sign.SignatureData data = new Sign.SignatureData(
                signature[64], Arrays.copyOfRange(signature, 0, 32), Arrays.copyOfRange(signature, 32, 64));
        BigInteger publicKey = Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), data);

        return "0x" + Keys.getAddress(publicKey);
    }
}
