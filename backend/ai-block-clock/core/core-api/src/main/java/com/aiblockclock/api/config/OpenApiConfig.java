package com.aiblockclock.api.config;

import com.aiblockclock.api.support.auth.CurrentUser;

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
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;

import java.util.List;

@Configuration
public class OpenApiConfig {

    private static final String APPLICATION_JSON = "application/json";

    /** Spring Session 기본 쿠키 이름. */
    private static final String SESSION_COOKIE_SCHEME = "SESSION";

    /** Spring Security 가 세션의 CSRF 토큰과 비교하는 헤더 이름. */
    private static final String CSRF_HEADER_SCHEME = "X-CSRF-TOKEN";

    private static final String UNAUTHORIZED_EXAMPLE =
            "{ \"status\": \"FAIL\", \"data\": null,"
                    + " \"error\": { \"code\": \"AUTH_005\", \"message\": \"요청자를 확인할 수 없습니다.\", \"data\": null } }";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentUser.class);
    }

    /**
     * 보호 API는 HttpOnly Spring Session 쿠키를 사용하고, 바꾸는 요청은 CSRF 토큰 헤더도 요구한다.
     * 관리자가 Swagger 로 심사하므로 `GET /api/v1/auth/csrf` 로 받은 토큰을 Authorize 에 넣을 칸을 둔다.
     * 서버 주소는 문서를 연 곳 그대로(`/`) 둔다. Nginx 뒤에서 http 주소가 적혀 https 화면이 요청을 막는 일을 피한다.
     */
    @Bean
    public OpenAPI openAPI() {
        SecurityScheme sessionCookie = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name(SESSION_COOKIE_SCHEME)
                .description("지갑 로그인으로 발급받은 HttpOnly 세션 쿠키");
        SecurityScheme csrfHeader = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(CSRF_HEADER_SCHEME)
                .description("`GET /api/v1/auth/csrf` 가 돌려준 `token`. 로그인한 뒤에 받아야 한다");

        return new OpenAPI()
                .info(new Info()
                        .title("AI Block Clock API")
                        .description("AI Block Clock 백엔드 API 문서")
                        .version("v1"))
                .servers(List.of(new Server().url("/")))
                .components(new Components()
                        .addSecuritySchemes(SESSION_COOKIE_SCHEME, sessionCookie)
                        .addSecuritySchemes(CSRF_HEADER_SCHEME, csrfHeader))
                .addSecurityItem(new SecurityRequirement()
                        .addList(SESSION_COOKIE_SCHEME)
                        .addList(CSRF_HEADER_SCHEME));
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
