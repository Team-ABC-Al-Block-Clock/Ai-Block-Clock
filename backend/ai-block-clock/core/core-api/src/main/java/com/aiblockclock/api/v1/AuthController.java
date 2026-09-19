package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.SessionTokenCookie;
import com.aiblockclock.api.v1.request.WalletLoginRequest;
import com.aiblockclock.api.v1.request.WalletMessageRequest;
import com.aiblockclock.api.v1.response.LoginSessionResponse;
import com.aiblockclock.api.v1.response.WalletMessageResponse;
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

@RestController
@RequestMapping(value = "/api/v1/auth", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final WalletAuthService walletAuthService;
    private final SessionTokenCookie sessionTokenCookie;

    @Override
    @PostMapping(value = "/wallet/message", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<WalletMessageResponse> issueMessage(@RequestBody WalletMessageRequest request) {
        return ApiResult.success(WalletMessageResponse.from(walletAuthService.issueMessage(request.walletAddress())));
    }

    @Override
    @PostMapping(value = "/wallet/login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ApiResult<LoginSessionResponse> login(
            @RequestBody WalletLoginRequest request, HttpServletResponse response
    ) {
        WalletLogin login = walletAuthService.login(request.walletAddress(), request.signature());
        response.addHeader(HttpHeaders.SET_COOKIE, sessionTokenCookie.create(login.sessionToken()).toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        return ApiResult.success(LoginSessionResponse.from(login.sessionToken()));
    }

    @Override
    @PostMapping("/logout")
    public ApiResult<Void> logout(
            @CookieValue(name = SessionTokenCookie.NAME, required = false) String sessionToken,
            HttpServletResponse response
    ) {
        walletAuthService.logout(sessionTokenCookie.requireValue(sessionToken));
        response.addHeader(HttpHeaders.SET_COOKIE, sessionTokenCookie.clear().toString());

        return ApiResult.success();
    }
}
