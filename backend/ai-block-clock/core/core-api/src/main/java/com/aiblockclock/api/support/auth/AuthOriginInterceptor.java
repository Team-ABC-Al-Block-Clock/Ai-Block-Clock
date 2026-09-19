package com.aiblockclock.api.support.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class AuthOriginInterceptor implements HandlerInterceptor {
    private final AuthWebProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!isStateChanging(request.getMethod())) {
            return true;
        }

        String origin = request.getHeader("Origin");
        if (origin == null) {
            origin = originOf(request.getHeader("Referer"));
        }

        if (origin == null || !properties.allowedOrigins().contains(origin)) {
            throw new CoreException(ErrorType.INVALID_REQUEST);
        }

        return true;
    }

    private static boolean isStateChanging(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }

    private static String originOf(String referer) {
        if (referer == null) {
            return null;
        }

        try {
            URI uri = URI.create(referer);
            if (uri.getHost() == null || uri.getRawUserInfo() != null) {
                return null;
            }

            return uri.getScheme() + "://" + uri.getRawAuthority();
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
