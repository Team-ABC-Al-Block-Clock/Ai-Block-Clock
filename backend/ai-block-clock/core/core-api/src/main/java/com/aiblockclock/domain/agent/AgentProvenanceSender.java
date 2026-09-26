package com.aiblockclock.domain.agent;

import com.aiblockclock.client.blockchain.BlockchainClientError;
import com.aiblockclock.client.blockchain.BlockchainClientException;
import com.aiblockclock.client.blockchain.ChainDocumentReceipt;
import com.aiblockclock.client.blockchain.SsafyStoDocumentClient;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Component;

import java.util.Optional;

// 원본 기록 하나를 등기부 토큰의 문서 칸에 적는다. 성공을 확인한 거래만 돌려주고, 나머지는 모두 "아직" 이다.
// 같은 이름에 같은 값을 다시 적어도 달라지는 것이 없어, 결과를 모르거나 실패한 기록은 다음 차례에 그대로 다시 보낸다.
@Slf4j
@Component
@RequiredArgsConstructor
public class AgentProvenanceSender {
    private final SsafyStoDocumentClient ssafyStoDocumentClient;

    // 등기부 주소 · 키가 없으면 설정 오류로 알린다. 보내기를 켠 서버가 기동할 때 확인한다
    public void ensureReady() {
        ssafyStoDocumentClient.requiredContractAddress();
    }

    public Optional<AgentProvenanceReceipt> send(String documentName, String documentUri, String metadataHash) {
        ChainDocumentReceipt receipt;

        try {
            receipt = ssafyStoDocumentClient.setDocument(documentName, documentUri, metadataHash);
        } catch (BlockchainClientException exception) {
            logFailure(documentName, exception);

            return Optional.empty();
        }

        return Optional.of(new AgentProvenanceReceipt(
                receipt.contractAddress(), receipt.transactionHash(), receipt.blockNumber().longValueExact()));
    }

    // 닿지 못한 것은 기다리면 풀린다. 되돌려졌거나 거절된 것은 설정(등기부 주인 · 키)을 사람이 고쳐야 풀린다
    private static void logFailure(String documentName, BlockchainClientException exception) {
        if (exception.getError() == BlockchainClientError.REQUEST_FAILED) {
            log.warn("원본 기록을 체인에 적지 못했다. 다음 차례에 다시 보낸다: documentName={}", documentName, exception);

            return;
        }

        log.error("원본 기록이 체인에서 거절됐다. 설정을 확인해야 한다: documentName={}", documentName, exception);
    }
}
