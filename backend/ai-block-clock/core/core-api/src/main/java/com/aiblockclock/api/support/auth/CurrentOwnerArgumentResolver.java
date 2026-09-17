package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.auth.LoginSession;
import com.aiblockclock.domain.auth.WalletAuthService;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import lombok.RequiredArgsConstructor;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentOwnerArgumentResolver implements HandlerMethodArgumentResolver {
    private static final String BEARER_PREFIX = "Bearer ";

    private final WalletAuthService walletAuthService;

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOwner.class)
                && OwnerId.class.equals(parameter.getParameterType());
    }

    @Override
    public OwnerId resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binderFactory
    ) {
        LoginSession session = walletAuthService.authenticate(readBearerToken(request));

        return OwnerId.of(session.userId().value());
    }

    private String readBearerToken(NativeWebRequest request) {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || header.isBlank()) {
            throw new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED);
        }

        if (!header.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }

        String rawAccessToken = header.substring(BEARER_PREFIX.length()).trim();

        if (rawAccessToken.isEmpty()) {
            throw new CoreException(ErrorType.ACCESS_TOKEN_INVALID);
        }

        return rawAccessToken;
    }
}
