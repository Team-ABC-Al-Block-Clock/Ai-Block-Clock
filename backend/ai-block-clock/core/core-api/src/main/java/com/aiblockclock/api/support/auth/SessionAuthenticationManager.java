package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.user.UserId;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionAuthenticationManager {
    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;

    public Instant login(
            UserId user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of()
        );
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return idleExpiresAt(request.getSession());
    }

    public void logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }

        SecurityContextHolder.clearContext();
    }

    /** 더 이상 요청이 없을 때 세션이 만료되는 시각. 요청이 오면 그만큼 뒤로 밀린다. */
    private Instant idleExpiresAt(HttpSession session) {
        return clock.instant().plusSeconds(session.getMaxInactiveInterval());
    }
}
