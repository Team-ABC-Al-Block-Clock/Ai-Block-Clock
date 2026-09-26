package com.aiblockclock.domain.agent;

import com.aiblockclock.enums.agent.AgentProvenanceStatus;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;

class AgentProvenanceServiceTest {
    private static final AgentId AGENT = AgentId.of(UUID.fromString("12345678-9012-3456-7890-123456789012"));
    private static final AgentVersionId VERSION = AgentVersionId.of(UUID.randomUUID());
    private static final String DOCUMENT_NAME = "EjRWeJASNFZ4kBI0VniQEg-v1";

    private final AgentProvenanceReader reader = mock(AgentProvenanceReader.class);
    private final AgentVersionMetadataReader metadataReader = mock(AgentVersionMetadataReader.class);
    private final AgentProvenanceSender sender = mock(AgentProvenanceSender.class);
    private final AgentProvenanceWriter writer = mock(AgentProvenanceWriter.class);
    private final AgentProvenanceService service = new AgentProvenanceService(reader, metadataReader, sender, writer);

    private final AgentVersionMetadata metadata = new AgentVersionMetadata(AGENT, VERSION, VersionNumber.first(),
            "0x535abafa3b650ab9fd7b2cd98c7ad06b18d37a3b", List.of());

    @Test
    void 체인에_적히면_해시와_거래를_남기고_확정한다() {
        AgentProvenanceReceipt receipt = new AgentProvenanceReceipt(
                "0x41e2e64a79b19e9329f82d3d55be5e767a6f571d", "0x" + "cd".repeat(32), 1234L);
        given(reader.read(VERSION)).willReturn(pending());
        given(metadataReader.read(VERSION)).willReturn(metadata);
        given(sender.send(DOCUMENT_NAME, metadata.documentUri(), metadata.hash())).willReturn(Optional.of(receipt));

        assertThat(service.register(VERSION)).isTrue();

        then(writer).should().confirm(VERSION, metadata, metadata.hash(), receipt);
        then(writer).should(never()).markAttempted(any());
    }

    @Test
    void 체인이_받아_주지_않으면_확정하지_않고_다음_차례의_뒤로_보낸다() {
        given(reader.read(VERSION)).willReturn(pending());
        given(metadataReader.read(VERSION)).willReturn(metadata);
        given(sender.send(anyString(), anyString(), anyString())).willReturn(Optional.empty());

        assertThat(service.register(VERSION)).isFalse();

        then(writer).should().markAttempted(VERSION);
        then(writer).should(never()).confirm(any(), any(), anyString(), any());
    }

    @Test
    void 내용을_읽다_깨져도_뒤로_보내_나머지를_막지_않는다() {
        given(reader.read(VERSION)).willReturn(pending());
        given(metadataReader.read(VERSION)).willThrow(new IllegalStateException("broken row"));

        assertThatThrownBy(() -> service.register(VERSION)).isInstanceOf(IllegalStateException.class);

        then(writer).should().markAttempted(VERSION);
        then(sender).should(never()).send(anyString(), anyString(), anyString());
    }

    @Test
    void 이미_확정된_기록은_다시_보내지_않는다() {
        given(reader.read(VERSION)).willReturn(AgentProvenance.restore(VERSION, AGENT, VersionNumber.first(),
                DOCUMENT_NAME, AgentProvenanceStatus.CONFIRMED, Instant.now(), null, null, null, null, null, null,
                Instant.now()));

        assertThat(service.register(VERSION)).isTrue();

        then(sender).should(never()).send(anyString(), anyString(), anyString());
        then(writer).should(never()).markAttempted(eq(VERSION));
    }

    private static AgentProvenance pending() {
        return AgentProvenance.restore(VERSION, AGENT, VersionNumber.first(), DOCUMENT_NAME,
                AgentProvenanceStatus.PENDING, Instant.now(), null, null, null, null, null, null, null);
    }
}
