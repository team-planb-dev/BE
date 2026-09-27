package com.planb.integration.global;

import com.planb.integration.IntegrationTest;
import com.planb.global.security.dto.UserAuthCache;
import com.planb.global.security.repository.UserAuthCacheRepository;
import com.planb.global.security.util.JwtUtil;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.boot.test.web.server.LocalManagementPort;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ActuatorSecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebEndpointsSupplier webEndpointsSupplier;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserAuthCacheRepository userAuthCacheRepository;

    @LocalServerPort
    private int applicationPort;

    @LocalManagementPort
    private int managementPort;

    @Test
    @DisplayName("Actuator 관리 엔드포인트 최소 노출과 무인증 접근 차단")
    void actuatorEndpointsProtected() throws Exception {

        assertNotEquals(applicationPort, managementPort);

        Set<String> endpointIds = webEndpointsSupplier
                .getEndpoints()
                .stream()
                .map(endpoint -> endpoint
                        .getEndpointId()
                        .toString())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "health",
                        "metrics",
                        "prometheus"
                ),
                endpointIds
        );

        HttpResponse<Void> response = HttpClient
                .newHttpClient()
                .send(
                        HttpRequest
                                .newBuilder(URI.create(
                                        "http://localhost:" + managementPort + "/actuator/prometheus"
                                ))
                                .GET()
                                .build(),
                        HttpResponse.BodyHandlers.discarding()
                );

        assertTrue(response.statusCode() >= 400);
        assertTrue(response.statusCode() < 500);
    }

    @Test
    @DisplayName("인증된 일반 사용자의 공개 포트 Actuator 접근 차단")
    void publicApplicationPortDoesNotServeActuator() throws Exception {

        String username = "actuator-user";
        String sessionId = "actuator-session";

        userAuthCacheRepository.save(
                username,
                new UserAuthCache(
                        1L,
                        username,
                        "USER",
                        sessionId
                ),
                60_000L
        );

        String accessToken = jwtUtil.createJwt(
                "access",
                1L,
                username,
                "USER",
                sessionId,
                60_000L
        );

        for (String path : List.of(
                "/actuator/health",
                "/actuator/metrics",
                "/actuator/prometheus"
        )) {
            HttpResponse<String> response = HttpClient
                    .newHttpClient()
                    .send(
                            HttpRequest
                                    .newBuilder(URI.create(
                                            "http://localhost:" + applicationPort + path
                                    ))
                                    .header("Authorization", "Bearer " + accessToken)
                                    .GET()
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()
                    );

            assertTrue(
                    response.body()
                            .contains("\"success\":false"),
                    path
            );
        }
    }

    @Test
    @DisplayName("Boot WebClient prototype builder의 외부 HTTP 지표 수집")
    void webClientObservationRecorded() throws IOException {

        WebClient.Builder firstBuilder = applicationContext
                .getBean(WebClient.Builder.class);

        WebClient.Builder secondBuilder = applicationContext
                .getBean(WebClient.Builder.class);

        assertNotSame(firstBuilder, secondBuilder);

        HttpServer server = HttpServer.create(
                new InetSocketAddress(0),
                0
        );

        server.createContext("/health", exchange -> {
            byte[] response = "ok".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        server.start();

        try {
            String response = firstBuilder
                    .build()
                    .get()
                    .uri("http://localhost:" + server.getAddress().getPort() + "/health")
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            assertEquals("ok", response);
            assertNotNull(
                    meterRegistry
                            .find("http.client.requests")
                            .timer()
            );
        } finally {
            server.stop(0);
        }
    }
}
