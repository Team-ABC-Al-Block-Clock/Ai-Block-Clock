package com.aiblockclock.api.config;

import com.aiblockclock.api.support.auth.CurrentOwner;
import com.aiblockclock.api.support.auth.CurrentOwnerArgumentResolver;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.parameters.Parameter;
import io.swagger.v3.oas.models.responses.ApiResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.utils.SpringDocUtils;

@Configuration
public class OpenApiConfig {

    private static final String APPLICATION_JSON = "application/json";

    private static final String UNAUTHORIZED_EXAMPLE =
            "{ \"status\": \"FAIL\", \"data\": null,"
                    + " \"error\": { \"code\": \"AUTH_005\", \"message\": \"요청자를 확인할 수 없습니다.\", \"data\": null } }";

    static {
        SpringDocUtils.getConfig().addAnnotationsToIgnore(CurrentOwner.class);
    }

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AI Block Clock API")
                        .description("AI Block Clock 백엔드 API 문서")
                        .version("v1"));
    }

    /** 401 은 모든 엔드포인트가 같은 모양으로 내므로 여기서 한 번만 붙인다. */
    @Bean
    public OperationCustomizer unauthorizedResponse() {
        Example example = new Example().value(UNAUTHORIZED_EXAMPLE);
        MediaType mediaType = new MediaType()
                .schema(new Schema<>().$ref("#/components/schemas/ApiResult"))
                .addExamples("default", example);
        ApiResponse unauthorized = new ApiResponse()
                .description("요청자를 확인할 수 없다 (`AUTH_005`)")
                .content(new Content().addMediaType(APPLICATION_JSON, mediaType));

        return (operation, handlerMethod) -> {
            operation.getResponses().addApiResponse("401", unauthorized);
            return operation;
        };
    }

    /** 요청자 식별은 인증 연동 전까지 이 헤더로 한다. 401 과 마찬가지로 모든 엔드포인트가 같다. */
    @Bean
    public OperationCustomizer ownerHeader() {
        Schema<?> schema = new Schema<>().type("integer").format("int64").example(1);

        return (operation, handlerMethod) -> {
            operation.addParametersItem(new Parameter()
                    .in("header")
                    .name(CurrentOwnerArgumentResolver.OWNER_HEADER)
                    .required(true)
                    .description("요청자 ID. 인증 연동 전까지 쓰는 임시 헤더다")
                    .schema(schema));
            return operation;
        };
    }
}
