package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.auth.AdminProperties;
import com.aiblockclock.domain.user.User;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
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
    /** 관리자 API(`/api/v1/admin/**`)에 들어갈 수 있는 역할. Spring Security 가 앞에 `ROLE_` 를 붙여 권한으로 쓴다. */
    public static final String ADMIN_ROLE = "ADMIN";

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;
    private final SecurityContextRepository securityContextRepository;
    private final AdminProperties adminProperties;
    private final Clock clock;

    // 권한은 세션에 함께 저장된다. 관리자 목록이 바뀌면 그 지갑이 다시 로그인해야 반영된다.
    public Instant login(
            User user,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                user.id(), null, authoritiesOf(user)
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

    private List<GrantedAuthority> authoritiesOf(User user) {
        if (adminProperties.isAdmin(user.walletAddress())) {
            return AuthorityUtils.createAuthorityList("ROLE_" + ADMIN_ROLE);
        }

        return List.of();
    }

    /** 더 이상 요청이 없을 때 세션이 만료되는 시각. 요청이 오면 그만큼 뒤로 밀린다. */
    private Instant idleExpiresAt(HttpSession session) {
        return clock.instant().plusSeconds(session.getMaxInactiveInterval());
    }
}
