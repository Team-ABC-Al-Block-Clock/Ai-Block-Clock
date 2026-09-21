package com.aiblockclock.api.support.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

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

        if (origin == null || !properties.csrfAllowedOrigins().contains(origin)) {
            throw new CoreException(ErrorType.INVALID_REQUEST);
        }

        return true;
    }

    private static boolean isStateChanging(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method);
    }
}
