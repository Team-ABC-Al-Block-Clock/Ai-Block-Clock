package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.AgentIntegrityMismatchReason;
import com.aiblockclock.enums.agent.AgentIntegrityResult;

import java.time.Instant;

// 원본 기록 하나를 지금 내용 · 체인과 대조한 결과다 (CHN-003).
// 해시 셋을 본다 — 지금 내용으로 다시 만든 해시, 적을 때 DB 에 남긴 해시, 체인에 적힌 해시. 여기에 원작자 지갑을 더한다.
// 공개한 버전은 바뀌지 않으므로 정상이면 셋이 늘 같다. 다르면 누군가 DB 를 고쳤거나 데이터가 망가진 것이다.
// 확정 전에는 대조할 체인 값이 없어 onChain · currentMetadataHash · checkedAt 이 비어 있다.
public record AgentIntegrity(
        OnChainAgentProvenance onChain,
        String currentMetadataHash,
        AgentIntegrityMismatchReason mismatchReason,
        Instant checkedAt
) {
    public static AgentIntegrity notConfirmed() {
        return new AgentIntegrity(null, null, AgentIntegrityMismatchReason.NOT_CONFIRMED, null);
    }

    public static AgentIntegrity check(AgentProvenance confirmed, AgentVersionMetadata current,
                                       OnChainAgentProvenance onChain, Instant checkedAt) {
        String currentMetadataHash = current.hash();

        return new AgentIntegrity(onChain, currentMetadataHash,
                mismatchOf(confirmed, current.ownerWalletAddress(), currentMetadataHash, onChain), checkedAt);
    }

    public boolean matched() {
        return mismatchReason == null;
    }

    public AgentIntegrityResult result() {
        return matched() ? AgentIntegrityResult.MATCHED : AgentIntegrityResult.MISMATCHED;
    }

    // 원작자 지갑은 해시 재료이기도 해서 지갑이 바뀌면 해시도 함께 어긋난다. 그때는 더 구체적인 지갑 불일치를 댄다.
    // 지금 해시가 어긋나면 기록 해시와 상관없이 내용이 바뀐 것으로 본다. 내용을 고치고 기록 칸까지 맞춰 둔 경우도 여기서 잡힌다
    private static AgentIntegrityMismatchReason mismatchOf(AgentProvenance confirmed, String ownerWalletAddress,
                                                          String currentMetadataHash, OnChainAgentProvenance onChain) {
        if (onChain == null) {
            return AgentIntegrityMismatchReason.NOT_ON_CHAIN;
        }

        if (!same(ownerWalletAddress, onChain.ownerWalletAddress())) {
            return AgentIntegrityMismatchReason.OWNER_MISMATCH;
        }

        if (!same(currentMetadataHash, onChain.metadataHash())) {
            return AgentIntegrityMismatchReason.METADATA_CHANGED;
        }

        if (!same(confirmed.getMetadataHash(), onChain.metadataHash())) {
            return AgentIntegrityMismatchReason.RECORD_MISMATCH;
        }

        return null;
    }

    // 지갑 주소 · 해시는 16진수라 대소문자만 다른 것은 같은 값이다
    private static boolean same(String left, String right) {
        return left != null && left.equalsIgnoreCase(right);
    }
}
