package com.aiblockclock.client.blockchain;

import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.regex.Pattern;

import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

// 에이전트 원본 기록 전용 토큰(등기부, ERC-1400)의 문서 칸에 쓰고 읽는다.
// 쓰기는 등기부 지갑의 개인키로 서명해 토큰증권 API 에 맡긴다. 읽기는 누구나 할 수 있어 서명이 없다.
@RequiredArgsConstructor
public class SsafyStoDocumentClient {
    // ERC-1400 의 문서 이름은 bytes32 다. 토큰증권 API 가 글자를 bytes32 로 옮길 때 넘치지 않도록 31자까지만 받는다
    private static final Pattern DOCUMENT_NAME_PATTERN = Pattern.compile("^[A-Za-z0-9_-]{1,31}$");
    // 서명한 본문과 보낸 본문이 한 글자도 달라지면 안 된다. JSON 이스케이프가 필요 없는 글자만 받아 본문을 그대로 이어 붙인다
    private static final Pattern DOCUMENT_URI_PATTERN = Pattern.compile("^[A-Za-z0-9:/?=&._-]{1,200}$");
    private static final Pattern DOCUMENT_HASH_PATTERN = Pattern.compile("^0x[0-9a-f]{64}$");
    private static final Pattern TRANSACTION_HASH_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{64}$");
    private static final int ERROR_BODY_LIMIT = 300;

    private final RestClient queryRestClient;
    private final RestClient writeRestClient;
    private final URI baseUrl;
    // 등기부를 설정하지 않은 서버에서는 null 이다. 쓰기 전에 requiredContractAddress 가 막는다
    private final SsafyAgentRegistry registry;

    // 설정된 등기부에 문서 하나를 적고, 블록에 들어가 성공한 것까지 확인한다.
    // 같은 이름에 같은 값을 다시 적으면 덮어쓸 뿐 달라지는 것이 없어서, 결과를 모를 때 다시 보내도 된다.
    public ChainDocumentReceipt setDocument(String name, String uri, String hash) {
        String contractAddress = requiredContractAddress();
        String message = signedPart(name, uri, hash);
        String body = message.substring(0, message.length() - 1) + ",\"signature\":\"" + sign(message) + "\"}";

        SsafyStoTransactionReceipt receipt = put(documentsPath(contractAddress), body);

        if (receipt == null || receipt.status() == null) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO document receipt has no status");
        }

        if (!receipt.status()) {
            throw new BlockchainClientException(BlockchainClientError.TRANSACTION_REVERTED,
                    "SSAFY STO document transaction was reverted: " + receipt.transactionHash());
        }

        return new ChainDocumentReceipt(contractAddress, requireTransactionHash(receipt.transactionHash()),
                requireBlockNumber(receipt.blockNumber()));
    }

    // 문서가 없으면(404) 빈 값이다. 그 밖의 오류는 체인에 무엇이 적혔는지 알 수 없는 상황이라 실패로 알린다.
    public Optional<ChainDocument> findDocument(String contractAddress, String name) {
        requireDocumentName(name);

        SsafyStoDocument document;

        try {
            document = queryRestClient.get()
                    .uri(documentsPath(contractAddress) + "/" + name)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(SsafyStoDocument.class);
        } catch (HttpClientErrorException.NotFound exception) {
            return Optional.empty();
        } catch (RestClientException exception) {
            throw new BlockchainClientException(BlockchainClientError.REQUEST_FAILED,
                    "SSAFY STO document request failed", exception);
        }

        if (document == null || document.docUri() == null || document.docHash() == null) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO document response is incomplete");
        }

        return Optional.of(new ChainDocument(document.docUri(), document.docHash()));
    }

    // 체인 탐색기가 없는 체인이라, 누구나 브라우저로 열어 체인에 적힌 값을 직접 확인하는 주소는 이것이다
    public URI documentUrl(String contractAddress, String name) {
        requireDocumentName(name);

        return UriComponentsBuilder.fromUri(baseUrl)
                .path(documentsPath(contractAddress) + "/" + name)
                .build()
                .toUri();
    }

    public String requiredContractAddress() {
        if (registry == null) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_CONFIGURATION,
                    "ssafy.sto.agent-registry is not configured");
        }

        return registry.contractAddress();
    }

    // 토큰증권 API 는 요청 본문에서 signature 를 뺀 JSON 을 서명 대상으로 본다. 칸 순서는 API 명세(AddDocumentDto) 순서다.
    // 발행 스크립트(scripts/mint-abc-token.mjs)와 같은 규칙이다
    private String signedPart(String name, String uri, String hash) {
        requireDocumentName(name);

        if (uri == null || !DOCUMENT_URI_PATTERN.matcher(uri).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_DOCUMENT,
                    "document uri must be 1-200 characters of letters, digits and :/?=&._-");
        }

        if (hash == null || !DOCUMENT_HASH_PATTERN.matcher(hash).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_DOCUMENT,
                    "document hash must be a 0x-prefixed lowercase 64-hex value");
        }

        return "{\"requestor\":\"" + Keys.toChecksumAddress(registry.signer().getAddress())
                + "\",\"name\":\"" + name
                + "\",\"docUri\":\"" + uri
                + "\",\"docHash\":\"" + hash + "\"}";
    }

    // MetaMask 의 personal_sign 과 같은 서명이다. 앞에 이더리움 서명 머리말을 붙여 거래 서명으로 쓰일 수 없게 한다
    private String sign(String message) {
        Sign.SignatureData signature =
                Sign.signPrefixedMessage(message.getBytes(StandardCharsets.UTF_8), registry.signer().getEcKeyPair());
        byte[] joined = new byte[65];

        System.arraycopy(signature.getR(), 0, joined, 0, 32);
        System.arraycopy(signature.getS(), 0, joined, 32, 32);
        joined[64] = signature.getV()[0];

        return Numeric.toHexString(joined);
    }

    // 응답 본문을 오류에 싣는다. 서명 · 형식이 틀렸을 때 토큰증권 API 가 무엇을 거절했는지 로그에서 읽을 수 있다
    private SsafyStoTransactionReceipt put(String path, String body) {
        try {
            return writeRestClient.put()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(SsafyStoTransactionReceipt.class);
        } catch (RestClientResponseException exception) {
            throw new BlockchainClientException(BlockchainClientError.REQUEST_FAILED,
                    "SSAFY STO document write failed with " + exception.getStatusCode().value()
                            + ": " + abbreviate(exception.getResponseBodyAsString()), exception);
        } catch (RestClientException exception) {
            throw new BlockchainClientException(BlockchainClientError.REQUEST_FAILED,
                    "SSAFY STO document write request failed", exception);
        }
    }

    private static String documentsPath(String contractAddress) {
        SsafyBlockchainAddress.requireValid(contractAddress, "contractAddress");

        return "/v1/tokens/" + contractAddress + "/documents";
    }

    private static void requireDocumentName(String name) {
        if (name == null || !DOCUMENT_NAME_PATTERN.matcher(name).matches()) {
            throw new BlockchainClientException(BlockchainClientError.INVALID_DOCUMENT,
                    "document name must be 1-31 characters of letters, digits, '_' and '-'");
        }
    }

    private static String requireTransactionHash(String transactionHash) {
        if (transactionHash == null || !TRANSACTION_HASH_PATTERN.matcher(transactionHash).matches()) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO document receipt has no valid transaction hash");
        }

        return transactionHash;
    }

    private static BigInteger requireBlockNumber(BigInteger blockNumber) {
        if (blockNumber == null || blockNumber.signum() <= 0) {
            throw new BlockchainClientException(BlockchainClientError.MALFORMED_RESPONSE,
                    "SSAFY STO document receipt has no block number");
        }

        return blockNumber;
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }

        return text.length() <= ERROR_BODY_LIMIT ? text : text.substring(0, ERROR_BODY_LIMIT) + "...";
    }
}
