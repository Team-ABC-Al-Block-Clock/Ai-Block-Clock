package com.aiblockclock.api.support.auth;

import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;
import com.aiblockclock.support.response.ApiResult;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {
    public static final String AUTHENTICATION_ERROR_ATTRIBUTE =
            ApiAuthenticationEntryPoint.class.getName() + ".error";

    private final JsonMapper jsonMapper;

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException, ServletException {
        CoreException coreException = authenticationError(request);
        ErrorType errorType = coreException == null
                ? ErrorType.REQUESTER_NOT_IDENTIFIED
                : coreException.getErrorType();
        Object errorData = coreException == null ? null : coreException.getData();

        response.setStatus(errorType.getStatus().value());
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        jsonMapper.writeValue(response.getOutputStream(), ApiResult.fail(errorType, errorData));
    }

    private CoreException authenticationError(HttpServletRequest request) {
        Object error = request.getAttribute(AUTHENTICATION_ERROR_ATTRIBUTE);

        return error instanceof CoreException coreException ? coreException : null;
    }
}
