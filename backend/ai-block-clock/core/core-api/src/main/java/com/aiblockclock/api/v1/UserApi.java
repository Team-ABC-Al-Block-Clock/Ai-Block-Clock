package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.response.UserProfileResponse;
import com.aiblockclock.domain.user.UserId;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "사용자", description = """
        로그인한 사용자 본인의 정보를 다룬다.
        세션 쿠키는 HttpOnly 라 화면이 읽을 수 없으므로, 로그인 상태는 이 API 로 확인한다.
        """)
public interface UserApi {
    @Operation(summary = "내 정보",
            description = "onboardingCompleted 가 false 면 아직 가입을 마치지 않은 지갑이다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회됨"),
            @ApiResponse(responseCode = "401", description = "로그인하지 않음(AUTH_005) 또는 세션의 사용자가 없음(AUTH_006)",
                    content = @Content)
    })
    ApiResult<UserProfileResponse> getMe(UserId userId);
}
