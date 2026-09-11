package com.aiblockclock.api.support.auth;

import com.aiblockclock.domain.OwnerId;
import com.aiblockclock.support.error.CoreException;
import com.aiblockclock.support.error.ErrorType;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentOwnerArgumentResolver implements HandlerMethodArgumentResolver {
    static final String OWNER_HEADER = "X-Owner-Id";

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentOwner.class)
                && OwnerId.class.equals(parameter.getParameterType());
    }

    @Override
    public OwnerId resolveArgument(
            MethodParameter parameter,
            ModelAndViewContainer container,
            NativeWebRequest request,
            WebDataBinderFactory binderFactory
    ) {
        String header = request.getHeader(OWNER_HEADER);
        if (header == null || header.isBlank()) {
            throw new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIED);
        }

        try {
            return OwnerId.of(Long.parseLong(header.trim()));
        } catch (NumberFormatException e) {
            throw new CoreException(ErrorType.REQUESTER_NOT_IDENTIFIABLE);
        }
    }
}
