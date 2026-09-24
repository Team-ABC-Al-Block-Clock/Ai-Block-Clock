package com.aiblockclock.storage.user;

import com.aiblockclock.storage.audit.BaseTimeEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Getter
@Entity
@Table(name = "app_user")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserEntity extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", nullable = false, length = 42)
    private String walletAddress;

    @Column(length = 20)
    private String nickname;

    @Column(length = 40)
    private String introduction;

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "recovery_notice_confirmed_at")
    private Instant recoveryNoticeConfirmedAt;

    @Column(name = "onboarding_completed_at")
    private Instant onboardingCompletedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    public static UserEntity of(String walletAddress) {
        UserEntity entity = new UserEntity();
        entity.walletAddress = walletAddress;
        return entity;
    }

    // 동의 두 건과 가입 완료는 지금은 한 요청에서 같은 시각에 일어나지만,
    // 약관이 개정되면 동의 시각만 다시 기록해야 하므로 칸을 나눠 둔다.
    public void completeOnboarding(String nickname, String introduction, Instant completedAt) {
        this.nickname = nickname;
        this.introduction = introduction;
        this.termsAgreedAt = completedAt;
        this.recoveryNoticeConfirmedAt = completedAt;
        this.onboardingCompletedAt = completedAt;
    }
}
