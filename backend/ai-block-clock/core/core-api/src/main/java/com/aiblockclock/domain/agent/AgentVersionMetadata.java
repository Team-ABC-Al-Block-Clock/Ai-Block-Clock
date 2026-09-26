package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.node.RefFile;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

// 공개된 에이전트 버전 하나를 체인에 적을 때의 내용이다. 체인에는 이 내용의 해시와, 원작자를 알아볼 주소만 적는다.
// 해시 재료는 공개한 뒤 바뀌지 않는 것만 쓴다. 이름 · 설명(프로필)은 버전 없이 고칠 수 있고 등급은 매일 다시 매기므로 넣지 않는다.
// 같은 내용이면 언제 누가 계산해도 같은 해시가 나와야 한다 — 322 무결성 검증이 이 계산을 다시 돌려 체인 값과 대조한다.
public record AgentVersionMetadata(
        AgentId agentId,
        AgentVersionId versionId,
        VersionNumber versionNumber,
        String ownerWalletAddress,
        List<VersionNode> nodes
) {
    // 계산 규칙을 바꾸면 이 값을 올린다. 예전 기록은 예전 규칙으로 다시 계산해야 대조가 맞는다
    private static final String FORMAT = "abc-agent-metadata/1";
    private static final String DOCUMENT_URI_PREFIX = "abc://agents/";

    public AgentVersionMetadata {
        ownerWalletAddress = ownerWalletAddress.toLowerCase(Locale.ROOT);
        nodes = nodes.stream()
                .sorted(Comparator.comparing(node -> node.key().value()))
                .toList();
    }

    // 칸마다 "이름=바이트 길이:값" 으로 이어 붙인다. 길이를 먼저 적어서 값 안에 어떤 글자(줄바꿈 · 구분자)가 있어도
    // 두 다른 내용이 같은 글자열이 되지 않는다. 노드는 키 순서로 늘어놓아 읽어 온 순서와 무관하게 같은 값이 된다.
    public String hash() {
        ByteArrayOutputStream canonical = new ByteArrayOutputStream();

        field(canonical, "format", FORMAT);
        field(canonical, "agentId", agentId.value().toString());
        field(canonical, "versionId", versionId.value().toString());
        field(canonical, "versionNumber", Integer.toString(versionNumber.value()));
        field(canonical, "owner", ownerWalletAddress);
        field(canonical, "nodeCount", Integer.toString(nodes.size()));

        for (VersionNode node : nodes) {
            field(canonical, "node.key", node.key().value());
            field(canonical, "node.name", node.spec().name());
            field(canonical, "node.type", node.spec().nodeType().name());
            field(canonical, "node.model", node.spec().model().name());
            field(canonical, "node.prompt", node.prompt().value());
            field(canonical, "node.refFileCount", Integer.toString(node.refFiles().size()));

            for (RefFile refFile : node.refFiles()) {
                field(canonical, "refFile.id", refFile.id().value().toString());
                field(canonical, "refFile.filename", refFile.filename());
                field(canonical, "refFile.size", Long.toString(refFile.size()));
            }
        }

        return "0x" + HexFormat.of().formatHex(sha256(canonical.toByteArray()));
    }

    // 체인의 문서 주소 칸이다. 누구의 것인지를 해시 밖에서도 읽을 수 있게 원작자 지갑을 싣는다
    public String documentUri() {
        return DOCUMENT_URI_PREFIX + agentId.value() + "/versions/" + versionNumber.value()
                + "?owner=" + ownerWalletAddress;
    }

    private static void field(ByteArrayOutputStream out, String name, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);

        out.writeBytes((name + "=" + bytes.length + ":").getBytes(StandardCharsets.UTF_8));
        out.writeBytes(bytes);
        out.write('\n');
    }

    private static byte[] sha256(byte[] input) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(input);
        } catch (NoSuchAlgorithmException exception) {
            // 모든 JVM 이 SHA-256 을 갖추도록 정해져 있어(MessageDigest 명세) 여기 오면 실행 환경이 깨진 것이다
            throw new CoreException(ErrorType.INTERNAL_ERROR);
        }
    }
}
