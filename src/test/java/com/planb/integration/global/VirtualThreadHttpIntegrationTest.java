package com.planb.integration.global;

import com.planb.integration.IntegrationTest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

@Import(VirtualThreadHttpIntegrationTest.RequestThreadProbe.class)
// 실행 환경의 SPRING_THREADS_VIRTUAL_ENABLED가 표준 속성에 직접 연결되므로 함께 고정
@TestPropertySource(properties = {
        "SPRING_THREADS_VIRTUAL_ENABLED=true",
        "spring.threads.virtual.enabled=true"
})
class VirtualThreadHttpIntegrationTest extends IntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private RequestThreadProbe requestThreadProbe;

    @Autowired
    private Environment environment;

    @Test
    @DisplayName("가상 스레드 모드의 실제 HTTP 요청 처리")
    void httpRequestRunsOnVirtualThread() throws Exception {

        HttpResponse<Void> response = HttpClient
                .newHttpClient()
                .send(
                        HttpRequest
                                .newBuilder(URI.create(
                                        "http://localhost:" + port
                                                + "/api/v1/user/check/duplication/username"
                                                + "?username=virtual-test%40example.com"
                                ))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding()
                );

        assertThat(response.statusCode())
                .isEqualTo(200);
        assertThat(requestThreadProbe.wasVirtual())
                .isTrue();
        assertThat(environment.getProperty("spring.main.keep-alive", Boolean.class))
                .isTrue();
    }

    @TestConfiguration
    static class RequestThreadProbe implements WebMvcConfigurer {

        private final AtomicReference<Boolean> requestWasVirtual =
                new AtomicReference<>();

        boolean wasVirtual() {

            return requestWasVirtual.get();
        }

        @Override
        public void addInterceptors(InterceptorRegistry registry) {

            registry
                    .addInterceptor(new HandlerInterceptor() {

                        @Override
                        public boolean preHandle(
                                HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler
                        ) {

                            requestWasVirtual.set(Thread.currentThread().isVirtual());

                            return true;
                        }
                    })
                    .addPathPatterns("/api/v1/user/check/duplication/username");
        }
    }
}
