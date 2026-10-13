package com.planb.unit.global.client.kakaoMapService;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.dto.response.KakaoRouteFailure;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoSearchKeywordSanitizer;
import com.planb.global.client.kakaoMapService.properties.KakaoMapServiceProperties;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import com.planb.global.client.kakaoMobilityService.properties.KakaoMobilityServiceProperties;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.tool.execution.DefaultToolCallResultConverter;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 카카오 경로 조회 실패의 단계·유형·공급자 오류 보존
 */
class KakaoMapServiceTestHandler {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final AtomicInteger transitCalls = new AtomicInteger();

    // 자동차 경로 응답 본문
    private String carBody = "{\"routes\":[{\"result_code\":0,\"result_msg\":\"길찾기 성공\",\"summary\":{\"distance\":1000,\"duration\":600}}]}";

    // 장소 검색 응답, null이면 기본 fixture
    private int searchStatus = 200;

    private String searchBody;

    // 대중교통 응답 헤더 전송 후 본문 지연
    private Duration bodyDelay = Duration.ZERO;

    private HttpServer server;

    @AfterEach
    void stop() {

        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("대중교통 200 응답의 이동시간과 실패 정보 부재")
    void keepsTransitSuccess() throws IOException {

        KakaoRouteResult result = transit(handler(
                200,
                fixture("loadtest/kakao/public-traffic-route.json")
        ));

        assertEquals(
                20,
                result.travelMinutes()
        );
        assertNull(result.failure());
        assertEquals(
                1.0,
                count(
                        "TRANSIT",
                        "ok"
                )
        );
    }

    @Test
    @DisplayName("대중교통 400 응답의 상태·공급자 코드·메시지·실제 요청 좌표 보존")
    void keepsTransitBadRequest() throws IOException {

        KakaoRouteFailure failure = transit(handler(
                400,
                "{\"code\":-10,\"msg\":\"API limit has been exceeded.\"}"
        ))
                .failure();

        assertEquals(
                KakaoRouteFailure.Stage.ROUTE,
                failure.stage()
        );
        assertEquals(
                KakaoRouteFailure.Kind.HTTP_ERROR,
                failure.kind()
        );
        assertEquals(
                400,
                failure.httpStatus()
        );
        assertEquals(
                "-10",
                failure.providerCode()
        );
        assertEquals(
                "API limit has been exceeded.",
                failure.providerMessage()
        );
        assertEquals(
                List.of(
                        "126.9767218661",
                        "37.5760307000",
                        "126.9835749364",
                        "37.5718885849"
                ),
                List.of(
                        failure.startX(),
                        failure.startY(),
                        failure.endX(),
                        failure.endY()
                )
        );
        assertEquals(
                1.0,
                count(
                        "TRANSIT",
                        "route_http_400"
                )
        );
    }

    @Test
    @DisplayName("같은 400의 서로 다른 공급자 코드 구분")
    void distinguishesProviderCodes() throws IOException {

        KakaoRouteFailure failure = transit(handler(
                400,
                "{\"code\":-2,\"msg\":\"invalid parameter\"}"
        ))
                .failure();

        assertEquals(
                "-2",
                failure.providerCode()
        );
    }

    @Test
    @DisplayName("본문 없음·비JSON·알 수 없는 구조의 안전 처리와 메시지 길이 제한·키 마스킹")
    void handlesUnparsableBodies() throws IOException {

        assertNull(transit(handler(
                400,
                ""
        ))
                .failure()
                .providerCode());

        server.stop(0);

        KakaoRouteFailure html = transit(handler(
                400,
                "<html>KakaoAK secret-value " + "x".repeat(500) + "</html>"
        ))
                .failure();

        assertNull(html.providerCode());
        assertFalse(html
                .providerMessage()
                .contains("secret-value"));
        assertTrue(html
                .providerMessage()
                .length() <= 120);

        server.stop(0);

        assertNull(transit(handler(
                400,
                "{\"foo\":1}"
        ))
                .failure()
                .providerCode());
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403, 429, 500, 503})
    @DisplayName("HTTP 4xx·5xx 상태 보존")
    void keepsHttpStatus(int status) throws IOException {

        KakaoRouteFailure failure = transit(handler(
                status,
                "{\"code\":-1,\"msg\":\"error\"}"
        ))
                .failure();

        assertEquals(
                status,
                failure.httpStatus()
        );
        assertEquals(
                KakaoRouteFailure.Kind.HTTP_ERROR,
                failure.kind()
        );
    }

    @Test
    @DisplayName("200 응답의 경로 없음과 유효하지 않은 이동시간 구분")
    void separatesNoRouteAndInvalidMinutes() throws IOException {

        KakaoRouteFailure noRoute = transit(handler(
                200,
                "{\"status\":\"EQUAL_POINTS\"}"
        ))
                .failure();

        assertEquals(
                KakaoRouteFailure.Kind.NO_RESULT,
                noRoute.kind()
        );
        assertEquals(
                "EQUAL_POINTS",
                noRoute.providerCode()
        );

        server.stop(0);

        assertEquals(
                KakaoRouteFailure.Kind.INVALID_MINUTES,
                transit(handler(
                        200,
                        "{\"status\":\"OK\",\"routes\":[{\"properties\":{\"totalDistance\":100,\"totalTime\":-60}}]}"
                ))
                        .failure()
                        .kind()
        );
    }

    @Test
    @DisplayName("자동차 200 응답의 경로 실패 코드·메시지 보존")
    void keepsCarResultCode() throws IOException {

        carBody = "{\"routes\":[{\"result_code\":104,\"result_msg\":\"출발지와 도착지가 5 m 이내로 설정된 경우 경로를 탐색할 수 없음\"}]}";

        KakaoRouteFailure failure = handler(
                200,
                "{}"
        )
                .getRoute(
                        "경복궁",
                        "결",
                        Transportation.CAR,
                        "126.9767218661",
                        "37.5760307000",
                        "126.9835749364",
                        "37.5718885849"
                )
                .block()
                .failure();

        assertEquals(
                KakaoRouteFailure.Kind.NO_RESULT,
                failure.kind()
        );
        assertEquals(
                "104",
                failure.providerCode()
        );
        assertTrue(failure
                .providerMessage()
                .startsWith("출발지와 도착지가"));
    }

    @Test
    @DisplayName("헤더 수신 후 본문 지연 timeout의 HTTP 200 오분류 방지")
    void keepsBodyTimeout() throws IOException {

        bodyDelay = Duration.ofSeconds(2);

        startServer(
                200,
                fixture("loadtest/kakao/public-traffic-route.json"),
                Duration.ZERO
        );

        assertEquals(
                KakaoRouteFailure.Kind.TIMEOUT,
                transit(handler(
                        baseUrl(),
                        WebClient
                                .builder()
                                .clientConnector(new ReactorClientHttpConnector(HttpClient
                                        .create()
                                        .responseTimeout(Duration.ofMillis(300))))
                ))
                        .failure()
                        .kind()
        );
    }

    @Test
    @DisplayName("AI Tool 응답 JSON의 실패 정보 제외")
    void hidesFailureFromToolResult() {

        String json = new DefaultToolCallResultConverter().convert(
                new KakaoRouteResult(
                        "경복궁",
                        "결",
                        null,
                        null,
                        new KakaoRouteFailure(
                                KakaoRouteFailure.Stage.ROUTE,
                                KakaoRouteFailure.Kind.HTTP_ERROR,
                                400,
                                "-10",
                                "limit",
                                "WebClientResponseException",
                                "126.9",
                                "37.5",
                                "126.9",
                                "37.5"
                        )
                ),
                KakaoRouteResult.class
        );

        assertFalse(json.contains("failure"));
        assertTrue(json.contains("\"origin\""));
        assertTrue(json.contains("\"travelMinutes\""));
    }

    @Test
    @DisplayName("파싱 실패·연결 실패·응답 timeout 구분")
    void separatesTransportFailures() throws IOException {

        assertEquals(
                KakaoRouteFailure.Kind.DECODE,
                transit(handler(
                        200,
                        "{"
                ))
                        .failure()
                        .kind()
        );

        int closedPort;

        try (ServerSocket socket = new ServerSocket(0)) {
            closedPort = socket.getLocalPort();
        }

        assertEquals(
                KakaoRouteFailure.Kind.CONNECTION,
                transit(handler(
                        "http://127.0.0.1:" + closedPort,
                        WebClient.builder()
                ))
                        .failure()
                        .kind()
        );

        server.stop(0);

        startServer(
                200,
                "{}",
                Duration.ofSeconds(2)
        );

        assertEquals(
                KakaoRouteFailure.Kind.TIMEOUT,
                transit(handler(
                        baseUrl(),
                        WebClient
                                .builder()
                                .clientConnector(new ReactorClientHttpConnector(HttpClient
                                        .create()
                                        .responseTimeout(Duration.ofMillis(200))))
                ))
                        .failure()
                        .kind()
        );
    }

    @Test
    @DisplayName("좌표 검색 실패와 경로 조회 실패 구분, 경로 미호출")
    void separatesPointSearchFailure() throws IOException {

        startServer(
                200,
                "{\"meta\":{\"total_count\":0,\"pageable_count\":0,\"is_end\":true},\"documents\":[]}",
                Duration.ZERO
        );

        KakaoRouteFailure failure = handler(
                baseUrl(),
                WebClient.builder()
        )
                .getRoute(
                        "서울역",
                        "결",
                        Transportation.TRANSIT,
                        null,
                        null,
                        "126.9835749364",
                        "37.5718885849"
                )
                .block()
                .failure();

        assertEquals(
                KakaoRouteFailure.Stage.POINT_SEARCH,
                failure.stage()
        );
        assertEquals(
                KakaoRouteFailure.Kind.NO_RESULT,
                failure.kind()
        );
        assertEquals(
                0,
                transitCalls.get()
        );
    }

    @Test
    @DisplayName("좌표 검색 HTTP 오류의 errorType 코드와 확정 좌표 보존")
    void keepsPointSearchErrorTypeAndKnownPoint() throws IOException {

        searchStatus = 403;
        searchBody = "{\"errorType\":\"AccessDeniedError\",\"message\":\"disabled\"}";

        KakaoRouteFailure failure = handler(
                200,
                "{}"
        )
                .getRoute(
                        "경복궁",
                        "결",
                        Transportation.TRANSIT,
                        "126.9767218661",
                        "37.5760307000",
                        null,
                        null
                )
                .block()
                .failure();

        assertEquals(
                KakaoRouteFailure.Stage.POINT_SEARCH,
                failure.stage()
        );
        assertEquals(
                403,
                failure.httpStatus()
        );
        assertEquals(
                "AccessDeniedError",
                failure.providerCode()
        );
        assertEquals(
                List.of(
                        "126.9767218661",
                        "37.5760307000"
                ),
                Arrays.asList(
                        failure.startX(),
                        failure.startY()
                )
        );
    }

    @Test
    @DisplayName("좌표 검색 성공 후 경로 400의 실제 요청 좌표 보존")
    void keepsSearchedRequestPoints() throws IOException {

        KakaoRouteFailure failure = handler(
                400,
                "{\"code\":-10,\"msg\":\"limit\"}"
        )
                .getRoute(
                        "서울역",
                        "결",
                        Transportation.TRANSIT,
                        null,
                        null,
                        "126.9835749364",
                        "37.5718885849"
                )
                .block()
                .failure();

        assertEquals(
                KakaoRouteFailure.Stage.ROUTE,
                failure.stage()
        );
        assertEquals(
                List.of(
                        "126.9780",
                        "37.5665"
                ),
                List.of(
                        failure.startX(),
                        failure.startY()
                )
        );
    }

    @Test
    @DisplayName("공급자 지표 tag의 이동수단·결과 한정")
    void limitsMetricTags() throws IOException {

        transit(handler(
                400,
                "{\"code\":-10,\"msg\":\"limit\"}"
        ));

        Set<String> keys = registry
                .getMeters()
                .stream()
                .filter(meter -> "planb.external.kakao.route".equals(meter
                        .getId()
                        .getName()))
                .map(Meter::getId)
                .flatMap(id -> id
                        .getTags()
                        .stream())
                .map(tag -> tag.getKey())
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
                        "transportation",
                        "outcome"
                ),
                keys
        );
    }

    private KakaoRouteResult transit(KakaoMapServiceHandler handler) {

        return handler
                .getRoute(
                        "경복궁",
                        "결",
                        Transportation.TRANSIT,
                        "126.9767218661",
                        "37.5760307000",
                        "126.9835749364",
                        "37.5718885849"
                )
                .block();
    }

    private double count(
            String transportation,
            String outcome
    ) {

        return registry
                .counter(
                        "planb.external.kakao.route",
                        "transportation",
                        transportation,
                        "outcome",
                        outcome
                )
                .count();
    }

    private KakaoMapServiceHandler handler(
            int transitStatus,
            String transitBody
    ) throws IOException {

        startServer(
                transitStatus,
                transitBody,
                Duration.ZERO
        );

        return handler(
                baseUrl(),
                WebClient.builder()
        );
    }

    private KakaoMapServiceHandler handler(
            String baseUrl,
            WebClient.Builder builder
    ) {

        return new KakaoMapServiceHandler(
                new KakaoMapServiceClient(
                        builder,
                        new KakaoMapServiceProperties(
                                baseUrl,
                                "stub"
                        )
                ),
                new KakaoMobilityServiceClient(
                        WebClient.builder(),
                        new KakaoMobilityServiceProperties(
                                baseUrl,
                                "stub"
                        )
                ),
                new KakaoMapRouteHelper(),
                new KakaoMobilityRouteHelper(),
                new KakaoSearchKeywordSanitizer(),
                new KakaoPlaceSearchHelper(),
                registry
        );
    }

    private void startServer(
            int transitStatus,
            String transitBody,
            Duration delay
    ) throws IOException {

        server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                ),
                0
        );

        server.createContext(
                "/v2/routing/publictraffic",
                exchange -> {
                    transitCalls.incrementAndGet();
                    sleep(delay);

                    if (bodyDelay.isZero()) {
                        respond(
                                exchange,
                                transitStatus,
                                transitBody
                        );
                    } else {
                        respondSlowly(
                                exchange,
                                transitBody,
                                bodyDelay
                        );
                    }
                }
        );

        server.createContext(
                "/v1/directions",
                exchange -> respond(
                        exchange,
                        200,
                        carBody
                )
        );

        String search = searchBody != null
                ? searchBody
                : transitBody.contains("\"documents\":[]")
                        ? transitBody
                        : fixture("loadtest/kakao/place-search.json");

        server.createContext(
                "/v2/local/search/keyword.json",
                exchange -> respond(
                        exchange,
                        searchStatus,
                        search
                )
        );

        server.start();
    }

    private String baseUrl() {

        return "http://127.0.0.1:" + server
                .getAddress()
                .getPort();
    }

    private static void sleep(Duration delay) {

        try {
            Thread.sleep(delay.toMillis());
        } catch (InterruptedException exception) {
            Thread
                    .currentThread()
                    .interrupt();
        }
    }

    // 헤더와 본문 일부 전송 후 지연
    private static void respondSlowly(
            HttpExchange exchange,
            String body,
            Duration delay
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json"
                );
        exchange.sendResponseHeaders(
                200,
                bytes.length
        );

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(
                    bytes,
                    0,
                    1
            );
            output.flush();
            sleep(delay);
            output.write(
                    bytes,
                    1,
                    bytes.length - 1
            );
        } catch (IOException exception) {
            // 클라이언트 timeout 종료
        }
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json"
                );
        exchange.sendResponseHeaders(
                status,
                bytes.length == 0 ? -1 : bytes.length
        );

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String fixture(String path) throws IOException {

        try (InputStream stream = KakaoMapServiceTestHandler.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            return new String(
                    Objects
                            .requireNonNull(stream)
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }
}
