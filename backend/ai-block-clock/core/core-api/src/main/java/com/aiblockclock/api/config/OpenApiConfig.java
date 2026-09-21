package com.aiblockclock.api.config;

import com.aiblockclock.api.support.auth.CurrentUser;
import com.aiblockclock.api.support.auth.SessionCookie;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;

@Configuration
public class OpenApiConfig {

    private static final String APPLICATION_JSON = "application/json";

    private static final String SESSION_COOKIE_SCHEME = SessionCookie.NAME;

    private static final String UNAUTHORIZED_EXAMPLE =
            "{ \"status\": \"FAIL\", \"data\": null,"
                    + " \"error\": { \"code\": \"AUTH_005\", \"message\": \"요청자를 확인할 수 없습니다.\", \"data\": null } }";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    /** 보호 API는 HttpOnly Spring Session 쿠키를 사용한다. */
    @Bean
    public OpenAPI openAPI() {
        SecurityScheme sessionToken = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name(SessionCookie.NAME)
                .description("지갑 로그인으로 발급받은 HttpOnly 세션 쿠키");

        return new OpenAPI()
                .info(new Info()
                        .title("AI Block Clock API")
                        .description("AI Block Clock 백엔드 API 문서")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(SESSION_COOKIE_SCHEME, sessionToken))
                .addSecurityItem(new SecurityRequirement().addList(SESSION_COOKIE_SCHEME));
    }

    /** 로그인 사용자가 필요한 API에 공통 세션 인증 오류를 붙인다. */
    @Bean
    public OperationCustomizer unauthorizedResponse() {
        Example example = new Example().value(UNAUTHORIZED_EXAMPLE);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ApiResult"))
                .addExamples("default", example);
        ApiResponse unauthorized = new ApiResponse()
                .description("로그인 세션이 없거나 미사용으로 만료됐다 (`AUTH_005`)")
                .content(new Content().addMediaType(APPLICATION_JSON, mediaType));

        return (operation, handlerMethod) -> {
            for (var parameter : handlerMethod.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(CurrentUser.class)) {
                    operation.getResponses().addApiResponse("401", unauthorized);
                    break;
                }
            }
            return operation;
        };
    }
}
