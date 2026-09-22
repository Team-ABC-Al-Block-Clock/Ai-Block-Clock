package com.aiblockclock.api.v1;

import com.aiblockclock.api.support.auth.SessionAuthenticationManager;
import com.aiblockclock.api.v1.request.WalletLoginRequest;
import com.aiblockclock.api.v1.request.WalletMessageRequest;
import com.aiblockclock.api.v1.response.CsrfTokenResponse;
import com.aiblockclock.api.v1.response.LoginSessionResponse;
import com.aiblockclock.api.v1.response.WalletMessageResponse;
import com.aiblockclock.domain.auth.WalletAuthService;
import com.aiblockclock.domain.user.User;
import com.aiblockclock.support.response.ApiResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.HttpHeaders;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController implements AuthApi {
    private final WalletAuthService walletAuthService;
    private final SessionAuthenticationManager sessionAuthenticationManager;

    @Override
    @GetMapping("/csrf")
    public ApiResult<CsrfTokenResponse> csrf(CsrfToken csrfToken) {
        return ApiResult.success(CsrfTokenResponse.from(csrfToken));
    }

    @Override
    @PostMapping("/wallet/message")
    public ApiResult<WalletMessageResponse> issueMessage(@RequestBody WalletMessageRequest request) {
        return ApiResult.success(WalletMessageResponse.from(walletAuthService.issueMessage(request.walletAddress())));
    }

    @Override
    @PostMapping("/wallet/login")
    public ApiResult<LoginSessionResponse> login(
            @RequestBody WalletLoginRequest request,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse
    ) {
        User user = walletAuthService.login(request.walletAddress(), request.signature());
        var expiresAt = sessionAuthenticationManager.login(user.id(), httpRequest, httpResponse);
        httpResponse.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");

        return ApiResult.success(LoginSessionResponse.from(expiresAt));
    }

    @Override
    @PostMapping("/logout")
    public ApiResult<Void> logout(HttpServletRequest request) {
        sessionAuthenticationManager.logout(request);

        return ApiResult.success();
    }
}
