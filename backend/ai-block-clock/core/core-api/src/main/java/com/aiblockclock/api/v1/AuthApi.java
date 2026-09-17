package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.WalletLoginRequest;
import com.aiblockclock.api.v1.request.WalletMessageRequest;
import com.aiblockclock.api.v1.response.AuthTokenResponse;
import com.aiblockclock.api.v1.response.WalletMessageResponse;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

@Tag(name = "인증", description = "지갑 로그인 및 토큰 관리. 허용된 Origin 또는 Referer가 필요합니다.")
public interface AuthApi {

    @Operation(summary = "서명할 지갑 인증 메시지 발급")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 및 nonce 발급"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 또는 허용되지 않은 출처"),
            @ApiResponse(responseCode = "409", description = "동시에 메시지를 변경함 (AUTH_007)")
    })
    ApiResult<WalletMessageResponse> issueMessage(@Valid WalletMessageRequest request);

    @Operation(summary = "지갑 서명으로 가입 또는 로그인",
            description = "Access Token은 응답 본문, Refresh Token은 HttpOnly 쿠키로 반환합니다.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 및 토큰 발급"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식 또는 허용되지 않은 출처"),
            @ApiResponse(responseCode = "401", description = "서명 또는 인증 메시지가 유효하지 않음 (AUTH_008~012)")
    })
    ApiResult<AuthTokenResponse> login(@Valid WalletLoginRequest request, HttpServletResponse response);

    @Operation(summary = "토큰 재발급", security = @SecurityRequirement(name = "refreshToken"),
            description = "본문 없이 Refresh Token 쿠키로 요청합니다. 최초 로그인부터 7일이라는 종료 시각은 유지합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Access Token 발급 및 Refresh Token 쿠키 교체"),
            @ApiResponse(responseCode = "400", description = "허용되지 않은 출처"),
            @ApiResponse(responseCode = "401", description = "세션 또는 Refresh Token이 유효하지 않음 (AUTH_013~018)")
    })
    ApiResult<AuthTokenResponse> reissue(@Parameter(hidden = true) String refreshToken, HttpServletResponse response);

    @Operation(summary = "로그아웃", security = @SecurityRequirement(name = "refreshToken"),
            description = "해당 로그인 세션을 종료하고 Refresh Token 쿠키를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 및 쿠키 삭제"),
            @ApiResponse(responseCode = "400", description = "허용되지 않은 출처"),
            @ApiResponse(responseCode = "401", description = "Refresh Token이 유효하지 않음 (AUTH_015~017)")
    })
    ApiResult<Void> logout(@Parameter(hidden = true) String refreshToken, HttpServletResponse response);
}
