package com.planb.integration.global;

import com.planb.integration.IntegrationTest;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.web.WebEndpointsSupplier;
import org.springframework.context.ApplicationContext;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActuatorSecurityIntegrationTest extends IntegrationTest {

    @Autowired
    private ApplicationContext applicationContext;

    @Autowired
    private WebEndpointsSupplier webEndpointsSupplier;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    @DisplayName("Actuator 관리 엔드포인트 최소 노출과 무인증 접근 차단")
    void actuatorEndpointsProtected() throws Exception {

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

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().is4xxClientError());

        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().is4xxClientError());
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
