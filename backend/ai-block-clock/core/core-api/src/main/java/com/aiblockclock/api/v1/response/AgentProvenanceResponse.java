package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.agent.AgentIntegrity;
import com.aiblockclock.domain.agent.AgentProvenance;
import com.aiblockclock.domain.agent.AgentProvenanceView;
import com.aiblockclock.domain.agent.OnChainAgentProvenance;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "에이전트의 원본 증명. 누가 언제 올렸는지를 SSAFY 체인의 등기부 기록으로 보여 준다")
public record AgentProvenanceResponse(
        @Schema(description = "에이전트 ID", example = "33567ab2-e08b-461b-a0f2-51a857df5ec7")
        String agentId,

        OwnerResponse owner,

        BlockchainResponse blockchain,

        VerificationResponse verification
) {
    public static AgentProvenanceResponse from(AgentProvenanceView view) {
        AgentProvenance current = view.current();
        AgentIntegrity integrity = view.integrity();
        OnChainAgentProvenance onChain = view.onChain();

        return new AgentProvenanceResponse(
                view.agentId().value().toString(),
                new OwnerResponse(view.ownerNickname(), view.offChainOwnerAddress()),
                new BlockchainResponse(
                        view.networkName(),
                        view.chainId(),
                        current.getContractAddress(),
                        current.getTransactionHash(),
                        current.getStatus().name(),
                        current.getBlockNumber(),
                        view.registeredAt(),
                        view.explorerUrl()),
                new VerificationResponse(
                        view.offChainOwnerAddress(),
                        onChain == null ? null : onChain.ownerWalletAddress(),
                        integrity.currentMetadataHash(),
                        onChain == null ? null : onChain.metadataHash(),
                        view.ownerMatched(),
                        view.metadataMatched(),
                        view.verified(),
                        integrity.mismatchReason() == null ? null : integrity.mismatchReason().name(),
                        integrity.checkedAt()));
    }

    @Schema(description = "원작자. 우리 DB 가 아는 값이다")
    public record OwnerResponse(
            @Schema(description = "닉네임", example = "김싸피")
            String nickname,

            @Schema(description = "원작자 지갑 주소", example = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b")
            String walletAddress
    ) {
    }

    @Schema(description = "지금 공개된 버전의 체인 기록. 확정 전(PENDING)에는 계약 · 거래 · 블록 · 조회 주소가 null 이다")
    public record BlockchainResponse(
            @Schema(description = "네트워크 이름", example = "SSAFY")
            String network,

            @Schema(description = "체인 ID", example = "31221")
            long chainId,

            @Schema(description = "기록이 적힌 등기부 토큰 주소", example = "0x41e2e64a79b19e9329f82d3d55be5e767a6f571d",
                    nullable = true)
            String contractAddress,

            @Schema(description = "기록을 적은 거래 해시", nullable = true,
                    example = "0x9c2b0f1e3a4d5c6b7a8f9e0d1c2b3a4f5e6d7c8b9a0f1e2d3c4b5a6f7e8d9c0b")
            String transactionHash,

            @Schema(description = "PENDING(체인에 적히길 기다림) 또는 CONFIRMED(블록에 적힘)", example = "CONFIRMED")
            String transactionStatus,

            @Schema(description = "기록이 적힌 블록 번호", example = "15230417", nullable = true)
            Long blockNumber,

            @Schema(description = "이 에이전트를 처음 공개한 시각", example = "2026-09-26T05:30:00Z")
            Instant registeredAt,

            @Schema(description = "체인에 적힌 값을 누구나 직접 열어 볼 수 있는 토큰증권 API 주소. SSAFY 체인은 탐색기가 없다",
                    nullable = true,
                    example = "https://sto.ssafy.io/v1/tokens/0x41e2e64a79b19e9329f82d3d55be5e767a6f571d/documents/EjRWeJASNFZ4kBI0VniQEg-v1")
            String explorerUrl
    ) {
    }

    @Schema(description = "우리 DB 값(offChain)과 체인에서 방금 읽은 값(onChain)의 대조 (CHN-003). "
            + "확정 전에는 offChain 해시 · onChain 이 null 이고 모두 false 다")
    public record VerificationResponse(
            @Schema(description = "DB 의 원작자 지갑", example = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b")
            String offChainOwnerAddress,

            @Schema(description = "체인 기록의 원작자 지갑", nullable = true,
                    example = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b")
            String onChainOwnerAddress,

            @Schema(description = "지금 DB 의 내용으로 방금 다시 만든 메타데이터 해시. 공개 뒤 내용이 바뀌면 체인 값과 달라진다",
                    nullable = true,
                    example = "0x3f1c0b7e9a2d4c6b8a0f1e2d3c4b5a6f7e8d9c0b1a2f3e4d5c6b7a8f9e0d1c2b")
            String offChainMetadataHash,

            @Schema(description = "체인 기록의 메타데이터 해시", nullable = true,
                    example = "0x3f1c0b7e9a2d4c6b8a0f1e2d3c4b5a6f7e8d9c0b1a2f3e4d5c6b7a8f9e0d1c2b")
            String onChainMetadataHash,

            @Schema(description = "원작자 지갑이 같은가")
            boolean ownerMatched,

            @Schema(description = "메타데이터 해시가 같은가")
            boolean metadataMatched,

            @Schema(description = "확정됐고 원작자 · 지금 내용 · 적을 때 남긴 해시가 모두 체인과 맞는가")
            boolean verified,

            @Schema(description = """
                    검증되지 않은 까닭. verified 가 true 면 null 이다.
                    NOT_CONFIRMED(아직 체인에 적히지 않음) · NOT_ON_CHAIN(체인에 기록이 없음) ·
                    OWNER_MISMATCH(원작자 지갑이 다름) · METADATA_CHANGED(공개 뒤 내용이 바뀜) ·
                    RECORD_MISMATCH(적을 때 남긴 해시가 체인과 다름)
                    """, example = "METADATA_CHANGED", nullable = true)
            String mismatchReason,

            @Schema(description = "이번에 대조한 시각. 확정 전에는 대조하지 않아 null 이다",
                    example = "2026-09-27T05:30:00Z", nullable = true)
            Instant verifiedAt
    ) {
    }
}
