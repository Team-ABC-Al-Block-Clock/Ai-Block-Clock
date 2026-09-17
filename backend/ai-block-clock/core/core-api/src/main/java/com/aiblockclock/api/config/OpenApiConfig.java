package com.aiblockclock.api.config;

import com.aiblockclock.api.support.auth.CurrentOwner;

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

    private static final String BEARER_SCHEME = "accessToken";

    private static final String UNAUTHORIZED_EXAMPLE =
            "{ \"status\": \"FAIL\", \"data\": null,"
                    + " \"error\": { \"code\": \"AUTH_005\", \"message\": \"요청자를 확인할 수 없습니다.\", \"data\": null } }";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentOwner.class);
    }

    /** 일반 API는 Access Token, 토큰 재발급과 로그아웃은 Refresh Token 쿠키를 사용한다. */
    @Bean
    public OpenAPI openAPI() {
        SecurityScheme accessToken = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("로그인으로 발급받은 Access Token");

        return new OpenAPI()
                .info(new Info()
                        .title("AI Block Clock API")
                        .description("AI Block Clock 백엔드 API 문서")
                        .version("v1"))
                .components(new Components().addSecuritySchemes(BEARER_SCHEME, accessToken)
                        .addSecuritySchemes("refreshToken", new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("refreshToken")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER_SCHEME));
    }

    /** 로그인 사용자가 필요한 API에 공통 Access Token 오류를 붙인다. */
    @Bean
    public OperationCustomizer unauthorizedResponse() {
        Example example = new Example().value(UNAUTHORIZED_EXAMPLE);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ApiResult"))
                .addExamples("default", example);
        ApiResponse unauthorized = new ApiResponse()
                .description("요청자를 확인할 수 없다 (`AUTH_005`)"
                        + " · Access Token 이 올바르지 않거나 만료됐다 (`AUTH_019`, `AUTH_020`)"
                        + " · 로그인 세션이 만료·종료됐다 (`AUTH_013`, `AUTH_014`, `AUTH_018`)")
                .content(new Content().addMediaType(APPLICATION_JSON, mediaType));

        return (operation, handlerMethod) -> {
            for (var parameter : handlerMethod.getMethodParameters()) {
                if (parameter.hasParameterAnnotation(CurrentOwner.class)) {
                    operation.getResponses().addApiResponse("401", unauthorized);
                    break;
                }
            }
            return operation;
        };
    }
}
