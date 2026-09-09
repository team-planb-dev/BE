package com.planb.global.config.web.swagger;

import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwt = "JWT";

        // OpenAPI에서 사용할 재사용 가능한 구성 요소
        Components components = new Components()
                .addSecuritySchemes(jwt, new SecurityScheme()
                        .name(jwt)
                        .type(SecurityScheme.Type.HTTP)// http 인증방식 사용
                        .scheme("bearer") // Bearer 토큰 방식
                        .bearerFormat("JWT") // 토큰 형식 JWT 지정
                );

        return new OpenAPI()
                .components(components)
                .info(apiInfo());
    }

    private Info apiInfo() {
        return new Info()
                .title("PlanB API")
                .description("""
                             ## 인증 안내

                             인증이 필요한 REST API에는 `JWT` 보안 표시가 있습니다.
                             로그인 후 발급받은 Access Token을 다음 형식으로 전달해 주세요.

                             `Authorization: Bearer {accessToken}`

                             Refresh Token은 `refreshToken` 이름의 HttpOnly Cookie로 사용합니다.
                             로그인과 로그아웃은 Security Filter가 처리합니다. 실제 요청 경로와 형식은
                             `인증 API 문서`의 Mock API를 확인해 주세요.

                             ## 공통 응답 및 오류 안내

                             REST API는 일반적으로 `ApiResult` 형식으로 응답합니다.
                             `success=false`이면 `error.errorCode`와 `error.message`를 확인해 주세요.
                             만료되거나 잘못된 Access Token은 HTTP 401로 응답할 수 있습니다.
                             접근 권한이 없으면 HTTP 403으로 응답합니다.
                             일부 비즈니스 오류는 현재 HTTP 200과 `success=false`로 반환됩니다.
                             """)
                .version("1.0.0");
    }
}
