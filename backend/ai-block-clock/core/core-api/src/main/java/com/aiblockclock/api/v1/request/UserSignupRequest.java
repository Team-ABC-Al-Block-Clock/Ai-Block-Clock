package com.aiblockclock.api.v1.request;

import com.aiblockclock.domain.user.Introduction;
import com.aiblockclock.domain.user.Nickname;
import com.aiblockclock.domain.user.UserSignup;

import io.swagger.v3.oas.annotations.media.Schema;

// 길이·동의는 Bean Validation 으로 걸면 COMMON_001 로 나간다. 화면과 약속한 USER_002·USER_003 을
// 주려고 검사는 값 객체에 맡긴다. profileImageFile 파트는 아직 저장하지 않으므로 받지 않는다.
@Schema(description = "가입 요청 (multipart/form-data)")
public record UserSignupRequest(
        @Schema(description = "이름. 앞뒤 공백을 지우고 2~20자", example = "김싸피")
        String nickname,

        @Schema(description = "한 줄 소개. 40자 이하. 비어 있으면 파트를 보내지 않는다",
                example = "에이전트를 만듭니다", nullable = true)
        String introduction,

        @Schema(description = "이용약관 및 개인정보처리방침 동의", example = "true")
        Boolean termsAgreed,

        @Schema(description = "지갑 분실 시 계정 복구 불가 고지 확인", example = "true")
        Boolean recoveryNoticeConfirmed
) {
    public UserSignup toSignup() {
        return new UserSignup(
                Nickname.of(nickname),
                Introduction.of(introduction),
                Boolean.TRUE.equals(termsAgreed),
                Boolean.TRUE.equals(recoveryNoticeConfirmed)
        );
    }
}
