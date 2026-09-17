package com.aiblockclock.api.config;

import com.aiblockclock.api.support.auth.CurrentOwnerArgumentResolver;
import com.aiblockclock.api.support.auth.AuthOriginInterceptor;
import com.aiblockclock.api.support.auth.AuthWebProperties;

import lombok.RequiredArgsConstructor;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {
    private final CurrentOwnerArgumentResolver currentOwnerArgumentResolver;
    private final AuthOriginInterceptor authOriginInterceptor;
    private final AuthWebProperties authWebProperties;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authOriginInterceptor).addPathPatterns("/api/v1/auth/**");
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/**")
                .allowedOrigins(authWebProperties.allowedOrigins().toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("Authorization", "Content-Type")
                .allowCredentials(true);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentOwnerArgumentResolver);
    }
}
