package com.planb.global.config.web.cors;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsMvcConfig implements WebMvcConfigurer {

    // HTTP CORS와 WebSocket 핸드셰이크가 함께 쓰는 허용 origin 목록
    // CORS 설정 분리로 인한 STOMP 403 재발 방지를 위한 단일 관리
    public static final String[] ALLOWED_ORIGINS = {
            "http://localhost:3000",
            "http://localhost:5173",
            "https://yeoro-frontend.vercel.app"
    };

    @Override

    public void addCorsMappings(CorsRegistry registry) {

        registry
                .addMapping("/**")
                .allowedOrigins(ALLOWED_ORIGINS)
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true)
                .exposedHeaders("Authorization") // access토큰 접근 설정
                .maxAge(3600);
    }
}
