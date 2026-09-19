package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.auth.LoginSession;
import com.aiblockclock.domain.auth.WalletAuthService;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
@RequiredArgsConstructor
public class CurrentOwnerArgumentResolver implements HandlerMethodArgumentResolver {
    private final WalletAuthService walletAuthService;
    private final SessionTokenCookie sessionTokenCookie;

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
        HttpServletRequest servletRequest = request.getNativeRequest(HttpServletRequest.class);
        if (servletRequest == null) {
            throw new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED);
        }
        LoginSession session = walletAuthService.authenticate(sessionTokenCookie.readRequired(servletRequest));

        return OwnerId.of(session.userId().value());
    }
}
