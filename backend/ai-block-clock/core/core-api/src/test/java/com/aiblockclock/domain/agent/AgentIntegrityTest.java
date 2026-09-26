package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.agent.AgentIntegrityMismatchReason;
import com.aiblockclock.enums.agent.AgentIntegrityResult;
import com.aiblockclock.enums.agent.AgentProvenanceStatus;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentIntegrityTest {
    private static final AgentId AGENT = AgentId.of(UUID.fromString("12345678-9012-3456-7890-123456789012"));
    private static final AgentVersionId VERSION =
            AgentVersionId.of(UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab"));
    private static final String OWNER = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b";
    private static final Instant NOW = Instant.parse("2026-09-27T05:30:00Z");

    // 공개할 때의 내용이다. 체인과 기록 칸에는 이 내용의 해시가 적혀 있다
    private final AgentVersionMetadata published = metadata(OWNER, "요약해 줘");
    private final String publishedHash = published.hash();

    @Test
    void 지금_내용_기록_체인이_모두_같으면_검증된다() {
        AgentIntegrity integrity = AgentIntegrity.check(recorded(publishedHash), published,
                onChain(OWNER, publishedHash), NOW);

        assertThat(integrity.matched()).isTrue();
        assertThat(integrity.result()).isEqualTo(AgentIntegrityResult.MATCHED);
        assertThat(integrity.mismatchReason()).isNull();
        assertThat(integrity.currentMetadataHash()).isEqualTo(publishedHash);
        assertThat(integrity.checkedAt()).isEqualTo(NOW);
    }

    @Test
    void 공개한_뒤_프롬프트가_바뀌면_내용이_바뀐_것이다() {
        AgentIntegrity integrity = AgentIntegrity.check(recorded(publishedHash), metadata(OWNER, "요약해 줘."),
                onChain(OWNER, publishedHash), NOW);

        assertThat(integrity.result()).isEqualTo(AgentIntegrityResult.MISMATCHED);
        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.METADATA_CHANGED);
        assertThat(integrity.currentMetadataHash()).isNotEqualTo(publishedHash);
    }

    @Test
    void 내용을_고치고_기록_칸까지_맞춰_둬도_체인과_달라_잡힌다() {
        AgentVersionMetadata tampered = metadata(OWNER, "요약해 줘.");

        AgentIntegrity integrity = AgentIntegrity.check(recorded(tampered.hash()), tampered,
                onChain(OWNER, publishedHash), NOW);

        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.METADATA_CHANGED);
    }

    @Test
    void 내용은_맞는데_기록_칸의_해시만_다르면_기록이_어긋난_것이다() {
        AgentIntegrity integrity = AgentIntegrity.check(recorded("0x" + "00".repeat(32)), published,
                onChain(OWNER, publishedHash), NOW);

        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.RECORD_MISMATCH);
    }

    @Test
    void 원작자_지갑이_바뀌면_해시보다_지갑_불일치를_댄다() {
        // 지갑은 해시 재료라 지갑이 바뀌면 해시도 함께 어긋난다. 더 구체적인 쪽을 알려 준다
        AgentIntegrity integrity = AgentIntegrity.check(recorded(publishedHash),
                metadata("0x1111111111111111111111111111111111111111", "요약해 줘"),
                onChain(OWNER, publishedHash), NOW);

        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.OWNER_MISMATCH);
    }

    @Test
    void 체인에_기록이_없으면_체인에_없는_것이다() {
        AgentIntegrity integrity = AgentIntegrity.check(recorded(publishedHash), published, null, NOW);

        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.NOT_ON_CHAIN);
        assertThat(integrity.currentMetadataHash()).isEqualTo(publishedHash);
    }

    @Test
    void 지갑과_해시는_대소문자가_달라도_같은_값이다() {
        AgentIntegrity integrity = AgentIntegrity.check(recorded(publishedHash.toUpperCase().replace("0X", "0x")),
                published, onChain(OWNER.toUpperCase().replace("0X", "0x"), publishedHash), NOW);

        assertThat(integrity.matched()).isTrue();
    }

    @Test
    void 확정_전에는_대조하지_않아_해시와_시각이_비어_있다() {
        AgentIntegrity integrity = AgentIntegrity.notConfirmed();

        assertThat(integrity.matched()).isFalse();
        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.NOT_CONFIRMED);
        assertThat(integrity.onChain()).isNull();
        assertThat(integrity.currentMetadataHash()).isNull();
        assertThat(integrity.checkedAt()).isNull();
    }

    private static AgentProvenance recorded(String metadataHash) {
        return AgentProvenance.restore(VERSION, AGENT, VersionNumber.first(), "EjRWeJASNFZ4kBI0VniQEg-v1",
                AgentProvenanceStatus.CONFIRMED, NOW, OWNER, metadataHash, null, null, null, null, NOW);
    }

    private static OnChainAgentProvenance onChain(String owner, String metadataHash) {
        return new OnChainAgentProvenance(owner, metadataHash);
    }

    private static AgentVersionMetadata metadata(String owner, String prompt) {
        return new AgentVersionMetadata(AGENT, VERSION, VersionNumber.first(), owner,
                List.of(new VersionNode(NodeKey.of("n1"), UserId.of(1L),
                        new NodeSpec("요약 노드", NodeType.LLM, AiModel.defaultModel(), AbcAmount.of(new BigDecimal("3"))),
                        Prompt.of(prompt), List.of())));
    }
}
