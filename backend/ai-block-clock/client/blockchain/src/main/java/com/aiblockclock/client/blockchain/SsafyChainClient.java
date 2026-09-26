package com.aiblockclock.client.blockchain;

import java.io.IOException;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.web3j.abi.EventEncoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.Event;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.Request;
import org.web3j.protocol.core.Response;
import org.web3j.protocol.core.methods.response.Log;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

// SSAFY 체인 노드에 JSON-RPC 로 묻는다. 거래를 읽고, 이미 서명된 거래(SsafyChainSigner)를 보낸다.
// 서명은 하지 않는다 — 키를 쥐는 곳을 한 군데로 둔다.
@Component
@RequiredArgsConstructor
public class SsafyChainClient {
    // ERC-20 호환 전송 이벤트. abcToken(ERC-1400)은 transfer · transferByPartition 모두 이 이벤트를 함께 남긴다.
    private static final String TRANSFER_EVENT_TOPIC = EventEncoder.encode(new Event("Transfer", List.of(
            new TypeReference<Address>(true) {
            },
            new TypeReference<Address>(true) {
            },
            new TypeReference<Uint256>() {
            })));

    // 이벤트 서명 + 보낸 주소 + 받은 주소. 수량은 인덱싱되지 않아 data 에 있다
    private static final int TRANSFER_TOPIC_COUNT = 3;
    private static final Pattern TRANSACTION_HASH_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{64}$");
    private static final Pattern RAW_TRANSACTION_PATTERN = Pattern.compile("^0x([0-9a-fA-F]{2})+$");

    private final Web3j ssafyChainWeb3j;

    public ChainTransaction readTransaction(String transactionHash) {
        if (transactionHash == null || !TRANSACTION_HASH_PATTERN.matcher(transactionHash).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_TRANSACTION_HASH,
                    "transactionHash must be a 0x-prefixed 64-hex transaction hash");
        }

        Optional<Transaction> transaction =
                send(ssafyChainWeb3j.ethGetTransactionByHash(transactionHash), "transaction").getTransaction();

        if (transaction.isEmpty()) {
            return ChainTransaction.notFound();
        }

        Long chainId = transaction.get().getChainId();
        Optional<TransactionReceipt> receipt =
                send(ssafyChainWeb3j.ethGetTransactionReceipt(transactionHash), "transaction receipt")
                        .getTransactionReceipt();

        if (receipt.isEmpty()) {
            return ChainTransaction.pending(chainId);
        }

        BigInteger latestBlockNumber = send(ssafyChainWeb3j.ethBlockNumber(), "block number").getBlockNumber();

        return new ChainTransaction(
                receipt.get().isStatusOK() ? ChainTransactionStatus.SUCCEEDED : ChainTransactionStatus.FAILED,
                chainId,
                receipt.get().getBlockNumber(),
                latestBlockNumber,
                tokenTransfersOf(receipt.get())
        );
    }

    // 아직 블록에 적히지 않은 거래까지 센 다음 거래 번호다. 보낸 거래가 블록에 오르기 전에 다음 것을 서명해도 번호가 겹치지 않는다
    public BigInteger readPendingNonce(String address) {
        SsafyBlockchainAddress.requireValid(address, "address");

        return send(ssafyChainWeb3j.ethGetTransactionCount(address, DefaultBlockParameterName.PENDING), "nonce")
                .getTransactionCount();
    }

    // 보낼 transfer 가 쓸 가스를 노드가 지금 상태로 실행해 잰다. 보내는 쪽 잔액이 모자라면 되돌려져 오류로 온다
    public BigInteger estimateTokenTransferGas(
            String fromAddress,
            String tokenContractAddress,
            String toAddress,
            BigInteger rawAmount
    ) {
        SsafyBlockchainAddress.requireValid(fromAddress, "fromAddress");
        SsafyBlockchainAddress.requireValid(tokenContractAddress, "tokenContractAddress");

        // 응답 쪽 Transaction 과 이름이 같아 요청 쪽은 전체 이름으로 쓴다
        var call = org.web3j.protocol.core.methods.request.Transaction.createEthCallTransaction(
                fromAddress, tokenContractAddress, TokenTransferCall.encode(toAddress, rawAmount));

        return send(ssafyChainWeb3j.ethEstimateGas(call), "gas estimate").getAmountUsed();
    }

    // 서명된 거래를 노드에 넘긴다. 답을 받지 못해도(REQUEST_FAILED) 거래가 퍼졌을 수 있어, 부르는 쪽은 해시로 결과를 다시 본다.
    // 이미 받은 거래를 다시 넘기면 노드가 오류로 답하지만 같은 거래라 두 번 처리되지 않는다
    public void submitRawTransaction(String rawTransaction) {
        if (rawTransaction == null || !RAW_TRANSACTION_PATTERN.matcher(rawTransaction).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_TRANSACTION,
                    "rawTransaction must be 0x-prefixed hex");
        }

        send(ssafyChainWeb3j.ethSendRawTransaction(rawTransaction), "raw transaction");
    }

    // 노드에 닿지 못했거나 노드가 오류로 답한 것은 체인 결과를 알 수 없는 상황이다. 실패로 단정하지 않는다.
    private <T extends Response<?>> T send(Request<?, T> request, String responseName) {
        T response;

        try {
            response = request.send();
        } catch (IOException | RuntimeException exception) {
            throw new BlockchainClientException(BlockchainClientError.REQUEST_FAILED,
                    "SSAFY chain " + responseName + " request failed", exception);
        }

        if (response == null) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY chain " + responseName + " response is empty");
        }

        if (response.hasError()) {
            throw new BlockchainClientException(BlockchainClientError.REQUEST_FAILED,
                    "SSAFY chain " + responseName + " request returned error " + response.getError().getCode());
        }

        return response;
    }

    private List<ChainTokenTransfer> tokenTransfersOf(TransactionReceipt receipt) {
        return receipt.getLogs().stream()
                .filter(SsafyChainClient::isTransferLog)
                .map(SsafyChainClient::toTokenTransfer)
                .toList();
    }

    // 같은 이름의 ERC-721 Transfer 는 토큰 번호까지 인덱싱해 토픽이 넷이다. 수량이 없으니 걸러낸다
    private static boolean isTransferLog(Log log) {
        List<String> topics = log.getTopics();

        return topics != null
                && topics.size() == TRANSFER_TOPIC_COUNT
                && TRANSFER_EVENT_TOPIC.equalsIgnoreCase(topics.get(0))
                && log.getData() != null
                && Numeric.cleanHexPrefix(log.getData()).length() == 64;
    }

    private static ChainTokenTransfer toTokenTransfer(Log log) {
        return new ChainTokenTransfer(
                log.getAddress().toLowerCase(Locale.ROOT),
                addressOf(log.getTopics().get(1)),
                addressOf(log.getTopics().get(2)),
                Numeric.toBigInt(log.getData())
        );
    }

    // 인덱싱된 주소는 32바이트 토픽의 뒤 20바이트다
    private static String addressOf(String topic) {
        String hex = Numeric.cleanHexPrefix(topic);

        return "0x" + hex.substring(hex.length() - 40).toLowerCase(Locale.ROOT);
    }
}
