package com.aiblockclock.api.config;

import com.aiblockclock.api.support.auth.ApiAuthenticationEntryPoint;
import com.aiblockclock.api.support.auth.AuthWebProperties;
import com.aiblockclock.api.support.auth.SessionCookie;
import com.aiblockclock.api.support.auth.SessionExpirationFilter;
import com.aiblockclock.domain.auth.LoginSessionProperties;

import jakarta.servlet.DispatcherType;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            SessionExpirationFilter expirationFilter,
            SecurityContextRepository securityContextRepository,
            ApiAuthenticationEntryPoint authenticationEntryPoint
    ) throws Exception {
        return http
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                // 쿠키 인증의 CSRF 방어는 CsrfOriginInterceptor가 담당한다.
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true))
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint(authenticationEntryPoint))
                .authorizeHttpRequests(authorize -> authorize
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(
                                HttpMethod.POST,
                                "/api/v1/auth/wallet/message",
                                "/api/v1/auth/wallet/login"
                        ).permitAll()
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/actuator/**"
                        ).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(expirationFilter, AnonymousAuthenticationFilter.class)
                .build();
    }

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CookieSerializer cookieSerializer(
            AuthWebProperties webProperties,
            LoginSessionProperties sessionProperties
    ) {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setCookieName(SessionCookie.NAME);
        serializer.setCookiePath(SessionCookie.PATH);
        serializer.setUseHttpOnlyCookie(true);
        serializer.setUseSecureCookie(webProperties.secureCookie());
        serializer.setUseBase64Encoding(false);
        serializer.setSameSite("Lax");
        serializer.setCookieMaxAge(Math.toIntExact(sessionProperties.ttl().toSeconds()));
        return serializer;
    }
}
