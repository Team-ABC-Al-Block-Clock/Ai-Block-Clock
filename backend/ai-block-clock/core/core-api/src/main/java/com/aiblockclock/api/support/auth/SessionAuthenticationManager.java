package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.auth.LoginSessionProperties;

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
    public static final String ABSOLUTE_EXPIRES_AT_ATTRIBUTE =
            SessionAuthenticationManager.class.getName() + ".absoluteExpiresAt";

    private final FindByIndexNameSessionRepository<? extends Session> sessionRepository;
    private final SecurityContextRepository securityContextRepository;
    private final LoginSessionProperties properties;
    private final Clock clock;

    public Instant login(
            OwnerId owner,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        invalidateCurrentSession(request);
        invalidateExistingSessions(owner);

        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                owner, null, List.of()
        );
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);

        Instant expiresAt = clock.instant().plus(properties.ttl());
        HttpSession session = request.getSession();
        session.setMaxInactiveInterval(Math.toIntExact(properties.ttl().toSeconds()));
        session.setAttribute(ABSOLUTE_EXPIRES_AT_ATTRIBUTE, expiresAt);

        return expiresAt;
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

    private void invalidateExistingSessions(OwnerId owner) {
        sessionRepository.findByPrincipalName(owner.getName())
                .keySet()
                .forEach(sessionRepository::deleteById);
    }
}
