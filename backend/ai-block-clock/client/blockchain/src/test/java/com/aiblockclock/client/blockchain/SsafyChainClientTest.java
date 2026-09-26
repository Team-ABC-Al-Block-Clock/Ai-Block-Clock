package com.aiblockclock.client.blockchain;

import java.io.IOException;
import java.math.BigInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.web3j.protocol.ObjectMapperFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.Web3jService;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.EthBlockNumber;
import org.web3j.protocol.core.methods.response.EthEstimateGas;
import org.web3j.protocol.core.methods.response.EthGetTransactionCount;
import org.web3j.protocol.core.methods.response.EthSendTransaction;
import org.web3j.protocol.core.methods.response.EthGetTransactionReceipt;
import org.web3j.protocol.core.methods.response.EthTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SsafyChainClientTest {
    private static final String HASH = "0x" + "a".repeat(64);
    private static final String TOKEN = "0x56dc097c14836473f4ad4bd675a3d01be5e4c63c";
    private static final String USER = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b";
    private static final String CUSTODY = "0x64e72a170a7a36ce09806190ed02085fe23cb6b6";
    private static final String TRANSFER_TOPIC = "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";
    private static final String TEN_TOKENS = "0x" + String.format("%064x", BigInteger.TEN.pow(19));

    private Web3jService service;
    private SsafyChainClient client;

    @BeforeEach
    void setUp() {
        service = mock(Web3jService.class);
        client = new SsafyChainClient(Web3j.build(service));
    }

    @Test
    void 노드가_모르는_해시면_아직_퍼지지_않은_거래로_본다() throws IOException {
        answer("eth_getTransactionByHash", EthTransaction.class, "null");

        ChainTransaction transaction = client.readTransaction(HASH);

        assertThat(transaction.status()).isEqualTo(ChainTransactionStatus.NOT_FOUND);
        assertThat(transaction.tokenTransfers()).isEmpty();
        verify(service, never()).send(argThat(isMethod("eth_getTransactionReceipt")), any());
    }

    @Test
    void 영수증이_없으면_블록에_적히기_전이고_체인_ID는_거래에서_읽는다() throws IOException {
        answer("eth_getTransactionByHash", EthTransaction.class, transactionJson());
        answer("eth_getTransactionReceipt", EthGetTransactionReceipt.class, "null");

        ChainTransaction transaction = client.readTransaction(HASH);

        assertThat(transaction.status()).isEqualTo(ChainTransactionStatus.PENDING);
        assertThat(transaction.chainId()).isEqualTo(31221L);
        assertThat(transaction.blockNumber()).isNull();
    }

    @Test
    void 성공한_거래는_Transfer_로그만_골라_주소와_수량을_읽는다() throws IOException {
        answer("eth_getTransactionByHash", EthTransaction.class, transactionJson());
        answer("eth_getTransactionReceipt", EthGetTransactionReceipt.class, receiptJson("0x1", """
                [
                  %s,
                  {"address": "%s", "topics": ["%s"], "data": "0x"},
                  {"address": "%s", "topics": ["%s", "%s", "%s", "%s"], "data": "0x"}
                ]""".formatted(transferLog(), TOKEN, "0x" + "b".repeat(64), TOKEN, TRANSFER_TOPIC,
                topicOf(USER), topicOf(CUSTODY), "0x" + "0".repeat(63) + "1")));
        answer("eth_blockNumber", EthBlockNumber.class, "\"0x65\"");

        ChainTransaction transaction = client.readTransaction(HASH);

        assertThat(transaction.status()).isEqualTo(ChainTransactionStatus.SUCCEEDED);
        assertThat(transaction.blockNumber()).isEqualTo(BigInteger.valueOf(100));
        assertThat(transaction.latestBlockNumber()).isEqualTo(BigInteger.valueOf(101));
        assertThat(transaction.tokenTransfers()).containsExactly(
                new ChainTokenTransfer(TOKEN, USER, CUSTODY, BigInteger.TEN.pow(19)));
    }

    @Test
    void 실행이_되돌려진_거래는_실패로_읽는다() throws IOException {
        answer("eth_getTransactionByHash", EthTransaction.class, transactionJson());
        answer("eth_getTransactionReceipt", EthGetTransactionReceipt.class, receiptJson("0x0", "[]"));
        answer("eth_blockNumber", EthBlockNumber.class, "\"0x64\"");

        ChainTransaction transaction = client.readTransaction(HASH);

        assertThat(transaction.status()).isEqualTo(ChainTransactionStatus.FAILED);
        assertThat(transaction.tokenTransfers()).isEmpty();
    }

    @Test
    void 노드에_닿지_못하면_결과_불명으로_던진다() throws IOException {
        given(service.send(argThat(isMethod("eth_getTransactionByHash")), eq(EthTransaction.class)))
                .willThrow(new IOException("connection refused"));

        assertThatThrownBy(() -> client.readTransaction(HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.REQUEST_FAILED));
    }

    @Test
    void 노드가_오류로_답하면_결과_불명으로_던진다() throws IOException {
        EthTransaction response = new EthTransaction();
        response.setError(new Response.Error(-32000, "busy"));
        given(service.send(argThat(isMethod("eth_getTransactionByHash")), eq(EthTransaction.class)))
                .willReturn(response);

        assertThatThrownBy(() -> client.readTransaction(HASH))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.REQUEST_FAILED));
    }

    @Test
    void 해시_형식이_틀리면_노드에_묻지_않고_거절한다() throws IOException {
        assertThatThrownBy(() -> client.readTransaction("0x1234"))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError())
                                .isEqualTo(BlockchainClientError.INVALID_TRANSACTION_HASH));

        verify(service, never()).send(any(), any());
    }

    @Test
    void 다음_거래_번호는_블록에_오르기_전_거래까지_센_값이다() throws IOException {
        answer("eth_getTransactionCount", EthGetTransactionCount.class, "\"0x5\"");

        assertThat(client.readPendingNonce(CUSTODY)).isEqualTo(BigInteger.valueOf(5));
        verify(service).send(argThat(request -> "eth_getTransactionCount".equals(request.getMethod())
                && "pending".equals(request.getParams().get(1))), eq(EthGetTransactionCount.class));
    }

    @Test
    void 가스는_보낼_transfer_본문을_보관_지갑에서_실행해_잰다() throws IOException {
        answer("eth_estimateGas", EthEstimateGas.class, "\"0xea60\"");

        BigInteger gas = client.estimateTokenTransferGas(CUSTODY, TOKEN, USER, BigInteger.TEN);

        assertThat(gas).isEqualTo(BigInteger.valueOf(60_000));
        verify(service).send(argThat(request -> {
            if (!"eth_estimateGas".equals(request.getMethod())) {
                return false;
            }

            var call = (org.web3j.protocol.core.methods.request.Transaction) request.getParams().get(0);

            return CUSTODY.equals(call.getFrom()) && TOKEN.equals(call.getTo())
                    && TokenTransferCall.encode(USER, BigInteger.TEN).equals(call.getData());
        }), eq(EthEstimateGas.class));
    }

    @Test
    void 서명된_거래를_그대로_노드에_넘긴다() throws IOException {
        answer("eth_sendRawTransaction", EthSendTransaction.class, "\"" + HASH + "\"");

        client.submitRawTransaction("0xf86c01");

        verify(service).send(argThat(request -> "eth_sendRawTransaction".equals(request.getMethod())
                && "0xf86c01".equals(request.getParams().get(0))), eq(EthSendTransaction.class));
    }

    @Test
    void 거래를_넘기다_노드가_오류로_답하면_결과_불명으로_던진다() throws IOException {
        EthSendTransaction response = new EthSendTransaction();
        response.setError(new Response.Error(-32000, "already known"));
        given(service.send(argThat(isMethod("eth_sendRawTransaction")), eq(EthSendTransaction.class)))
                .willReturn(response);

        assertThatThrownBy(() -> client.submitRawTransaction("0xf86c01"))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.REQUEST_FAILED));
    }

    @Test
    void 바이트로_나뉘지_않는_거래는_노드에_넘기지_않는다() throws IOException {
        assertThatThrownBy(() -> client.submitRawTransaction("0xf86"))
                .isInstanceOfSatisfying(BlockchainClientException.class,
                        exception -> assertThat(exception.getError()).isEqualTo(BlockchainClientError.INVALID_TRANSACTION));

        verify(service, never()).send(any(), any());
    }

    private <T extends Response<?>> void answer(String method, Class<T> type, String resultJson) throws IOException {
        String body = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":" + resultJson + "}";
        T response = ObjectMapperFactory.getObjectMapper().readValue(body, type);

        given(service.send(argThat(isMethod(method)), eq(type))).willReturn(response);
    }

    private static org.mockito.ArgumentMatcher<Request<?, ?>> isMethod(String method) {
        return request -> request != null && method.equals(request.getMethod());
    }

    private static String transactionJson() {
        return """
                {"hash": "%s", "from": "%s", "to": "%s", "chainId": "0x79f5", "nonce": "0x0",
                 "value": "0x0", "gas": "0x29254", "gasPrice": "0x0", "input": "0x", "v": "0xf40e",
                 "r": "0x1", "s": "0x1", "type": "0x0"}""".formatted(HASH, USER, TOKEN);
    }

    private static String receiptJson(String status, String logs) {
        return """
                {"transactionHash": "%s", "blockNumber": "0x64", "status": "%s", "from": "%s", "to": "%s",
                 "gasUsed": "0x0", "cumulativeGasUsed": "0x0", "logs": %s}""".formatted(HASH, status, USER, TOKEN, logs);
    }

    private static String transferLog() {
        return """
                {"address": "%s", "topics": ["%s", "%s", "%s"], "data": "%s"}"""
                .formatted(TOKEN.toUpperCase().replace("0X", "0x"), TRANSFER_TOPIC, topicOf(USER), topicOf(CUSTODY),
                        TEN_TOKENS);
    }

    private static String topicOf(String address) {
        return "0x" + "0".repeat(24) + address.substring(2);
    }
}
