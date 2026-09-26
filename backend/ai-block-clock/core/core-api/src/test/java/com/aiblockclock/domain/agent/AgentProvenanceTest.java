package com.aiblockclock.domain.agent;

import com.aiblockclock.client.blockchain.ChainDocument;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentProvenanceTest {

    @Test
    void 문서_이름은_에이전트와_버전을_가리고_31자를_넘지_않는다() {
        AgentId agent = AgentId.of(UUID.fromString("12345678-9012-3456-7890-123456789012"));

        String name = AgentProvenance.documentNameOf(agent, VersionNumber.of(1));

        assertThat(name).isEqualTo("EjRWeJASNFZ4kBI0VniQEg-v1");
        assertThat(AgentProvenance.documentNameOf(agent, VersionNumber.of(9_999_999))).hasSizeLessThanOrEqualTo(31);
        assertThat(AgentProvenance.documentNameOf(agent, VersionNumber.of(2))).isNotEqualTo(name);
    }

    @Test
    void 체인_기록의_주소_칸에서_원작자를_꺼낸다() {
        OnChainAgentProvenance onChain = OnChainAgentProvenance.from(new ChainDocument(
                "abc://agents/12345678-9012-3456-7890-123456789012/versions/1"
                        + "?owner=0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b",
                "0x" + "AB".repeat(32)));

        assertThat(onChain.ownerWalletAddress()).isEqualTo("0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b");
        assertThat(onChain.metadataHash()).isEqualTo("0x" + "ab".repeat(32));
    }

    @Test
    void 주소_칸의_모양이_다르면_원작자를_모르는_것으로_둔다() {
        OnChainAgentProvenance onChain = OnChainAgentProvenance.from(new ChainDocument("ipfs://something", "0x00"));

        assertThat(onChain.ownerWalletAddress()).isNull();
    }
}
