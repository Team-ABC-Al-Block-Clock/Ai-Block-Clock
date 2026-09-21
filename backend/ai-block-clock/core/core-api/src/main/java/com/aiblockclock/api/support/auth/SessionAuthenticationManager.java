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
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SessionAuthenticationManager {
    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SecurityContextRepository securityContextRepository;
    private final Clock clock;

    public Instant login(
            UserId user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        invalidateCurrentSession(request);
        invalidateExistingSessions(user);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user, null, List.of()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        return idleExpiresAt(request.getSession());
    }

    public void logout(HttpServletRequest request) {
        invalidateCurrentSession(request);
        SecurityContextHolder.clearContext();
    }

    private void invalidateCurrentSession(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (session != null) {
            session.invalidate();
        }
    }

    private void invalidateExistingSessions(UserId user) {
        sessionRepository.findByPrincipalName(user.getName())
                .keySet()
                .forEach(sessionRepository::deleteById);
    }

    /** 더 이상 요청이 없을 때 세션이 만료되는 시각. 요청이 오면 그만큼 뒤로 밀린다. */
    private Instant idleExpiresAt(HttpSession session) {
        return clock.instant().plusSeconds(session.getMaxInactiveInterval());
    }
}
