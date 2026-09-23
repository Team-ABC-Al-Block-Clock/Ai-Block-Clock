package com.aiblockclock.api.v1.response;

import com.aiblockclock.domain.auth.WalletNetwork;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.domain.user.UserProfile;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.Instant;

@Schema(description = "로그인한 사용자의 프로필")
public record UserProfileResponse(
        @Schema(description = "사용자 ID", example = "12")
        String userId,

        @Schema(description = "이름. 가입을 마치기 전이면 null", example = "김싸피", nullable = true)
        String nickname,

        @Schema(description = "한 줄 소개. 없으면 null", example = "에이전트를 만듭니다", nullable = true)
        String introduction,

        @Schema(description = "프로필 이미지 주소. 아직 저장하지 않아 항상 null", nullable = true)
        String profileImageUrl,

        @Schema(description = "로그인한 지갑")
        WalletResponse wallet,

        @Schema(description = "가입을 마쳤는지. false 면 가입 화면으로 보낸다", example = "false")
        boolean onboardingCompleted,

        @Schema(description = "처음 로그인한 시각")
        Instant createdAt
) {
    // 지갑 로그인은 메타마스크 personal_sign 한 가지뿐이다.
    private static final String WALLET_PROVIDER = "METAMASK";

    public static UserProfileResponse from(UserProfile profile) {
        User user = profile.user();

        return new UserProfileResponse(
                Long.toString(user.id().value()),
                user.nickname(),
                user.introduction(),
                null,
                WalletResponse.of(user.walletAddress(), profile.network()),
                user.onboardingCompleted(),
                user.createdAt()
        );
    }

    @Schema(description = "로그인한 지갑")
    public record WalletResponse(
            @Schema(description = "지갑 종류", example = "METAMASK")
            String provider,

            @Schema(description = "지갑 주소 (소문자)", example = "0xfb6916095ca1df60bb79ce92ce3ea74c37c5d359")
            String address,

            @Schema(description = "네트워크", example = "POLYGON_AMOY")
            WalletNetwork network,

            @Schema(description = "체인 ID", example = "80002")
            long chainId
    ) {
        static WalletResponse of(String address, WalletNetwork network) {
            return new WalletResponse(WALLET_PROVIDER, address, network, network.chainId());
        }
    }
}
