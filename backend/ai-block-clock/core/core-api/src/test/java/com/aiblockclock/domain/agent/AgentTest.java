package com.aiblockclock.domain.agent;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentTest {
    private static final OwnerId OWNER = OwnerId.of(1L);
    private static final OwnerId OTHER = OwnerId.of(2L);

    private static Agent agent() {
        AgentProfile profile = new AgentProfile("성적 분석 에이전트", "성적을 분석한다",
                "학생별 점수 표를 받아 이번 기간과 지난 기간을 비교한다",
                "성적표 엑셀을 올리면 분석 리포트가 나옵니다", "무엇을 분석할까요?", null);

        return Agent.draft(AgentId.newId(), OWNER, profile);
    }

    @Test
    @DisplayName("공개 버전은 뒤에 공개한 버전으로 옮겨간다")
    void publicVersionMovesForward() {
        Agent agent = agent();
        AgentVersionId first = AgentVersionId.newId();
        AgentVersionId second = AgentVersionId.newId();

        agent.markVersionPublished(OWNER, first, VersionNumber.of(1));
        agent.markVersionPublished(OWNER, second, VersionNumber.of(2));

        assertThat(agent.getPublicVersion()).isEqualTo(new PublicVersion(second, VersionNumber.of(2)));
    }

    @Test
    @DisplayName("이미 공개된 버전보다 이전 버전은 공개할 수 없다")
    void publicVersionNeverMovesBackward() {
        Agent agent = agent();
        agent.markVersionPublished(OWNER, AgentVersionId.newId(), VersionNumber.of(2));

        assertThatThrownBy(() -> agent.markVersionPublished(OWNER, AgentVersionId.newId(), VersionNumber.of(1)))
                .isInstanceOf(CoreException.class);
    }

    @Test
    @DisplayName("소유자가 아니면 프로필도 공개 버전도 바꿀 수 없다")
    void onlyOwnerCanModify() {
        Agent agent = agent();

        assertThatThrownBy(() -> agent.updateProfile(OTHER, agent.getProfile()))
                .isInstanceOf(CoreException.class);
        assertThatThrownBy(() -> agent.markVersionPublished(OTHER, AgentVersionId.newId(), VersionNumber.first()))
                .isInstanceOf(CoreException.class);
    }
}
