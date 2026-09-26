package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.AgentIntegrityMismatchReason;
import com.aiblockclock.enums.agent.AgentProvenanceStatus;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class AgentIntegrityVerifierTest {
    private static final AgentId AGENT = AgentId.of(UUID.fromString("12345678-9012-3456-7890-123456789012"));
    private static final AgentVersionId VERSION = AgentVersionId.of(UUID.randomUUID());
    private static final String OWNER = "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b";
    private static final Instant NOW = Instant.parse("2026-09-27T05:30:00Z");

    private final AgentProvenanceChainReader chainReader = mock(AgentProvenanceChainReader.class);
    private final AgentVersionMetadataReader metadataReader = mock(AgentVersionMetadataReader.class);
    private final AgentProvenanceWriter writer = mock(AgentProvenanceWriter.class);
    private final AgentIntegrityVerifier verifier = new AgentIntegrityVerifier(chainReader, metadataReader, writer,
            Clock.fixed(NOW, ZoneOffset.UTC));

    private final AgentVersionMetadata metadata =
            new AgentVersionMetadata(AGENT, VERSION, VersionNumber.first(), OWNER, List.of());
    private final AgentProvenance confirmed = AgentProvenance.restore(VERSION, AGENT, VersionNumber.first(),
            "EjRWeJASNFZ4kBI0VniQEg-v1", AgentProvenanceStatus.CONFIRMED, NOW, OWNER, metadata.hash(), null,
            "0x41e2e64a79b19e9329f82d3d55be5e767a6f571d", null, null, NOW);

    @Test
    void 지금_내용을_다시_모아_체인과_대조하고_결과를_남긴다() {
        given(chainReader.find(confirmed)).willReturn(Optional.of(new OnChainAgentProvenance(OWNER, metadata.hash())));
        given(metadataReader.read(VERSION)).willReturn(metadata);

        AgentIntegrity integrity = verifier.verify(confirmed);

        assertThat(integrity.matched()).isTrue();
        assertThat(integrity.checkedAt()).isEqualTo(NOW);
        then(writer).should().recordIntegrity(VERSION, integrity);
    }

    @Test
    void 어긋난_결과도_까닭과_함께_남긴다() {
        given(chainReader.find(confirmed)).willReturn(Optional.empty());
        given(metadataReader.read(VERSION)).willReturn(metadata);

        AgentIntegrity integrity = verifier.verify(confirmed);

        assertThat(integrity.mismatchReason()).isEqualTo(AgentIntegrityMismatchReason.NOT_ON_CHAIN);
        then(writer).should().recordIntegrity(VERSION, integrity);
    }

    @Test
    void 체인에_닿지_못하면_아무것도_남기지_않는다() {
        given(chainReader.find(confirmed))
                .willThrow(new CoreException(ErrorType.AGENT_PROVENANCE_CHAIN_UNREACHABLE));

        assertThatThrownBy(() -> verifier.verify(confirmed))
                .isInstanceOfSatisfying(CoreException.class, exception ->
                        assertThat(exception.getErrorType()).isEqualTo(ErrorType.AGENT_PROVENANCE_CHAIN_UNREACHABLE));

        then(writer).should(never()).recordIntegrity(any(), any());
    }
}
