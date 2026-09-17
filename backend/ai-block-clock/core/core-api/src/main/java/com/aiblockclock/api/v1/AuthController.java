package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.RefreshTokenCookie;
import com.aiblockclock.api.v1.request.WalletLoginRequest;
import com.aiblockclock.api.v1.request.WalletMessageRequest;
import com.aiblockclock.api.v1.response.AuthTokenResponse;
import com.aiblockclock.api.v1.response.WalletMessageResponse;
import com.aiblockclock.domain.auth.AuthTokens;
import com.aiblockclock.domain.auth.WalletAuthService;
import com.aiblockclock.domain.auth.WalletLogin;
import com.aiblockclock.support.response.ApiResult;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final WalletAuthService walletAuthService;
    private final RefreshTokenCookie refreshTokenCookie;
    private final Clock clock;

    @Override
    @PostMapping(value = "/wallet/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<WalletMessageResponse> issueMessage(@RequestBody WalletMessageRequest request) {
        return ApiResult.success(WalletMessageResponse.from(walletAuthService.issueMessage(request.walletAddress())));
    }

    @Override
    @PostMapping(value = "/wallet/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<AuthTokenResponse> login(
            @RequestBody WalletLoginRequest request, HttpServletResponse response
    ) {
        WalletLogin login = walletAuthService.login(request.walletAddress(), request.signature());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(login.refreshToken()).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        return ApiResult.success(AuthTokenResponse.from(login.accessToken(), clock.instant()));
    }

    @Override
    @PostMapping("/reissue")
    public ApiResult<AuthTokenResponse> reissue(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        AuthTokens tokens = walletAuthService.reissue(refreshTokenCookie.requireValue(refreshToken));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.create(tokens.refreshToken()).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        return ApiResult.success(AuthTokenResponse.from(tokens.accessToken(), clock.instant()));
    }

    @Override
    @PostMapping("/logout")
    public ApiResult<Void> logout(
            @CookieValue(name = RefreshTokenCookie.NAME, required = false) String refreshToken,
            HttpServletResponse response
    ) {
        walletAuthService.logout(refreshTokenCookie.requireValue(refreshToken));
        response.addHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.clear().toString());

        return ApiResult.success();
    }
}
