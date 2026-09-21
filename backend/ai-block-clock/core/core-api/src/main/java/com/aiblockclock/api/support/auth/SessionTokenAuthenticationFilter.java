package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.domain.auth.LoginSession;
import com.aiblockclock.domain.auth.WalletAuthService;
import com.aiblockclock.support.error.CoreException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class SessionTokenAuthenticationFilter extends OncePerRequestFilter {
    private final SessionTokenCookie sessionTokenCookie;
    private final WalletAuthService walletAuthService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        Optional<String> rawSessionToken = sessionTokenCookie.read(request);

        if (rawSessionToken.isPresent()) {
            authenticate(request, rawSessionToken.get());
        }

        filterChain.doFilter(request, response);
    }

    private void authenticate(HttpServletRequest request, String rawSessionToken) {
        try {
            String requiredSessionToken = sessionTokenCookie.requireValue(rawSessionToken);
            LoginSession session = walletAuthService.authenticate(requiredSessionToken);
            OwnerId owner = OwnerId.of(session.userId().value());
            Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                    owner, null, List.of()
            );
            SecurityContext context = SecurityContextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            SecurityContextHolder.setContext(context);
        } catch (CoreException exception) {
            SecurityContextHolder.clearContext();
            request.setAttribute(ApiAuthenticationEntryPoint.AUTHENTICATION_ERROR_ATTRIBUTE, exception);
        }
    }
}
