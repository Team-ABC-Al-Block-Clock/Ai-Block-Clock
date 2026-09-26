package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.AbcAmount;
import com.aiblockclock.domain.node.NodeSpec;
import com.aiblockclock.domain.node.Prompt;
import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.domain.node.RefFileId;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.domain.workflow.NodeKey;
import com.aiblockclock.enums.node.AiModel;
import com.aiblockclock.enums.node.NodeType;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AgentVersionMetadataTest {
    private static final AgentId AGENT = AgentId.of(UUID.fromString("12345678-9012-3456-7890-123456789012"));
    private static final AgentVersionId VERSION =
            AgentVersionId.of(UUID.fromString("abcdefab-cdef-abcd-efab-cdefabcdefab"));
    private static final String OWNER = "0x535ABAFa3B650AB9fD7b2cD98C7Ad06b18d37a3b";

    @Test
    void 같은_내용이면_언제_계산해도_같은_해시다() {
        assertThat(metadata(List.of(node("n1", "요약해 줘"), node("n2", "번역해 줘"))).hash())
                .isEqualTo(metadata(List.of(node("n1", "요약해 줘"), node("n2", "번역해 줘"))).hash())
                .matches("^0x[0-9a-f]{64}$");
    }

    @Test
    void 노드를_읽어_온_순서가_달라도_같은_해시다() {
        assertThat(metadata(List.of(node("n2", "번역해 줘"), node("n1", "요약해 줘"))).hash())
                .isEqualTo(metadata(List.of(node("n1", "요약해 줘"), node("n2", "번역해 줘"))).hash());
    }

    @Test
    void 프롬프트가_한_글자만_달라도_해시가_달라진다() {
        assertThat(metadata(List.of(node("n1", "요약해 줘."))).hash())
                .isNotEqualTo(metadata(List.of(node("n1", "요약해 줘"))).hash());
    }

    @Test
    void 원작자가_다르면_해시가_달라진다() {
        AgentVersionMetadata other = new AgentVersionMetadata(AGENT, VERSION, VersionNumber.first(),
                "0x1111111111111111111111111111111111111111", List.of(node("n1", "요약해 줘")));

        assertThat(other.hash()).isNotEqualTo(metadata(List.of(node("n1", "요약해 줘"))).hash());
    }

    @Test
    void 값의_경계를_옮겨도_같은_글자열이_되지_않는다() {
        // 길이를 앞에 적지 않고 이어 붙이면 "ab" + "c" 와 "a" + "bc" 가 같은 글자열이 된다
        AgentVersionMetadata first = metadata(List.of(new VersionNode(NodeKey.of("n1"), UserId.of(1L),
                new NodeSpec("ab", NodeType.LLM, AiModel.defaultModel(), AbcAmount.of(new BigDecimal("3"))), Prompt.of("c"),
                List.of())));
        AgentVersionMetadata second = metadata(List.of(new VersionNode(NodeKey.of("n1"), UserId.of(1L),
                new NodeSpec("a", NodeType.LLM, AiModel.defaultModel(), AbcAmount.of(new BigDecimal("3"))), Prompt.of("bc"),
                List.of())));

        assertThat(first.hash()).isNotEqualTo(second.hash());
    }

    @Test
    void 참고_파일이_바뀌면_해시가_달라진다() {
        VersionNode withFile = new VersionNode(NodeKey.of("n1"), UserId.of(1L), spec(), Prompt.of("요약해 줘"),
                List.of(new RefFile(RefFileId.of(UUID.fromString("00000000-0000-0000-0000-000000000001")),
                        "files/1", "양식.pdf", 1024)));

        assertThat(metadata(List.of(withFile)).hash()).isNotEqualTo(metadata(List.of(node("n1", "요약해 줘"))).hash());
    }

    @Test
    void 원작자_지갑은_대소문자와_무관하게_소문자로_적는다() {
        assertThat(metadata(List.of()).ownerWalletAddress()).isEqualTo(OWNER.toLowerCase());
        assertThat(metadata(List.of()).documentUri()).isEqualTo(
                "abc://agents/12345678-9012-3456-7890-123456789012/versions/1?owner=" + OWNER.toLowerCase());
    }

    private static AgentVersionMetadata metadata(List<VersionNode> nodes) {
        return new AgentVersionMetadata(AGENT, VERSION, VersionNumber.first(), OWNER, nodes);
    }

    private static VersionNode node(String key, String prompt) {
        return new VersionNode(NodeKey.of(key), UserId.of(1L), spec(), Prompt.of(prompt), List.of());
    }

    private static NodeSpec spec() {
        return new NodeSpec("요약 노드", NodeType.LLM, AiModel.defaultModel(), AbcAmount.of(new BigDecimal("3")));
    }
}
