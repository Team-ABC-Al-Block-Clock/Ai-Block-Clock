package com.aiblockclock.api.support.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;

@Component
@RequiredArgsConstructor
public class SessionExpirationFilter extends OncePerRequestFilter {
    private final Clock clock;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        HttpSession session = request.getSession(false);

        if (isExpired(session)) {
            session.invalidate();
            SecurityContextHolder.clearContext();
            request.setAttribute(
                    ApiAuthenticationEntryPoint.AUTHENTICATION_ERROR_ATTRIBUTE,
                    new CoreException(ErrorType.LOGIN_SESSION_EXPIRED)
            );
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExpired(HttpSession session) {
        if (session == null) {
            return false;
        }

        Object expiresAt = session.getAttribute(
                SessionAuthenticationManager.ABSOLUTE_EXPIRES_AT_ATTRIBUTE
        );

        return expiresAt instanceof Instant instant && !clock.instant().isBefore(instant);
    }
}
