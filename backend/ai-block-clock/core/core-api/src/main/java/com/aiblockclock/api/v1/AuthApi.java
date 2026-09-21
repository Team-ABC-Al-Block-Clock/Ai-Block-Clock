package com.aiblockclock.api.v1;

import com.aiblockclock.api.v1.request.WalletLoginRequest;
import com.aiblockclock.api.v1.request.WalletMessageRequest;
import com.aiblockclock.api.v1.response.CsrfTokenResponse;
import com.aiblockclock.api.v1.response.LoginSessionResponse;
import com.aiblockclock.api.v1.response.WalletMessageResponse;
import com.aiblockclock.support.response.ApiResult;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import org.springframework.security.web.csrf.CsrfToken;

@Tag(name = "인증", description = "지갑 로그인, 세션 관리 및 CSRF 토큰 발급")
public interface AuthApi {

    @Operation(summary = "CSRF 토큰 발급",
            description = "변경 요청의 X-CSRF-TOKEN 헤더에 넣을 토큰을 반환합니다.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "CSRF 토큰 발급")
    })
    ApiResult<CsrfTokenResponse> csrf(CsrfToken csrfToken);

    @Operation(summary = "서명할 지갑 인증 메시지 발급")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메시지 및 nonce 발급"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "409", description = "동시에 메시지를 변경함 (AUTH_007)")
    })
    ApiResult<WalletMessageResponse> issueMessage(@Valid WalletMessageRequest request);

    @Operation(summary = "지갑 서명으로 가입 또는 로그인",
            description = "Spring Session ID는 HttpOnly 쿠키로, 세션 만료 시각은 응답 본문으로 반환합니다.")
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그인 및 Spring Session 쿠키 발급"),
            @ApiResponse(responseCode = "400", description = "잘못된 요청 형식"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "401", description = "서명 또는 인증 메시지가 유효하지 않음 (AUTH_008~012)")
    })
    ApiResult<LoginSessionResponse> login(
            @Valid WalletLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    );

    @Operation(summary = "로그아웃",
            description = "현재 HttpSession을 무효화하고 세션 쿠키를 삭제합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "로그아웃 및 쿠키 삭제"),
            @ApiResponse(responseCode = "403", description = "CSRF 토큰이 없거나 유효하지 않음"),
            @ApiResponse(responseCode = "401", description = "로그인 세션이 없거나 만료됨 (AUTH_005, AUTH_013)")
    })
    ApiResult<Void> logout(HttpServletRequest request);
}
