package com.planb.unit.global.client.kor2Service;

import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.properties.Kor2ServiceProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import com.sun.net.httpserver.HttpServer;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import reactor.netty.http.client.HttpClient;

import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class Kor2ServiceTestClient {

    private static final URI SEARCH_URI = URI.create(
            "https://apis.data.go.kr/B551011/KorService2/searchKeyword2?serviceKey=secret&keyword=x"
    );

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("로컬 HTTP 429 복구·소진과 503·응답 타임아웃의 시도 예산")
    void enforcesAttemptBudgetAcrossRealStubResponses() throws Exception {

        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        AtomicInteger recoveredCalls = new AtomicInteger();
        AtomicInteger exhaustedCalls = new AtomicInteger();
        AtomicInteger unavailableCalls = new AtomicInteger();
        AtomicInteger timeoutCalls = new AtomicInteger();
        AtomicInteger invalidCalls = new AtomicInteger();
        CountDownLatch releaseDelayedResponse = new CountDownLatch(1);
        byte[] success = """
                {"response":{"header":{"resultCode":"0000","resultMsg":"OK"},
                "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
                """.getBytes(StandardCharsets.UTF_8);

        try (var workers = Executors.newVirtualThreadPerTaskExecutor()) {
            server.setExecutor(workers);
            server.createContext("/", exchange -> {
                try (exchange) {
                    int status = switch (exchange.getRequestURI().getPath()) {
                        case "/recover" -> recoveredCalls.incrementAndGet() == 1 ? 429 : 200;
                        case "/exhaust" -> exhaustedCalls.incrementAndGet() <= 2 ? 429 : 200;
                        case "/unavailable" -> unavailableCalls.incrementAndGet() == 1 ? 503 : 200;
                        case "/timeout" -> {
                            if (timeoutCalls.incrementAndGet() == 1) {
                                try {
                                    releaseDelayedResponse.await(5, TimeUnit.SECONDS);
                                } catch (InterruptedException interrupted) {
                                    Thread.currentThread().interrupt();
                                }
                            }
                            yield 200;
                        }
                        default -> {
                            invalidCalls.incrementAndGet();
                            yield 400;
                        }
                    };
                    if (status == 200) {
                        exchange.getResponseHeaders().set("Content-Type", "application/json");
                        exchange.sendResponseHeaders(200, success.length);
                        exchange.getResponseBody().write(success);
                    } else {
                        exchange.getResponseHeaders().set("Retry-After", "20");
                        exchange.sendResponseHeaders(status, -1);
                    }
                }
            });
            server.start();

            try {
                String base = "http://127.0.0.1:" + server.getAddress().getPort();
                Kor2ServiceClient client = new Kor2ServiceClient(
                        WebClient.builder().clientConnector(new ReactorClientHttpConnector(
                                HttpClient.create().responseTimeout(Duration.ofSeconds(1))
                        )),
                        new Kor2ServiceProperties(base, "local-stub"),
                        meterRegistry
                );

                readStub(client, base + "/recover");
                assertThat(recoveredCalls.get())
                        .isEqualTo(2);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> readStub(client, base + "/exhaust"))
                        .isInstanceOf(WebClientResponseException.TooManyRequests.class);
                assertThat(exhaustedCalls.get())
                        .isEqualTo(2);
                readStub(client, base + "/exhaust");
                assertThat(exhaustedCalls.get())
                        .isEqualTo(3);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> readStub(client, base + "/unavailable"))
                        .isInstanceOf(WebClientResponseException.ServiceUnavailable.class);
                assertThat(unavailableCalls.get())
                        .isEqualTo(1);
                readStub(client, base + "/unavailable");
                assertThat(unavailableCalls.get())
                        .isEqualTo(2);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> readStub(client, base + "/timeout"))
                        .hasRootCauseInstanceOf(io.netty.handler.timeout.ReadTimeoutException.class);
                assertThat(timeoutCalls.get())
                        .isEqualTo(1);
                releaseDelayedResponse.countDown();
                readStub(client, base + "/timeout");
                assertThat(timeoutCalls.get())
                        .isEqualTo(2);

                org.assertj.core.api.Assertions.assertThatThrownBy(() -> readStub(client, base + "/invalid"))
                        .isInstanceOf(WebClientResponseException.BadRequest.class);
                assertThat(invalidCalls.get())
                        .isEqualTo(1);
            } finally {
                releaseDelayedResponse.countDown();
                server.stop(0);
            }
        }
    }

    private void readStub(
            Kor2ServiceClient client,
            String url
    ) {

        assertThat(client
                .get(URI.create(url), Kor2KeywordSearchResponse.class)
                .block(Duration.ofSeconds(5)))
                .isNotNull();
    }

    @Test
    @DisplayName("정상 응답은 endpoint별 ok로 기록")
    void recordsOkOutcome() {

        Kor2ServiceClient client = client(List.of(json(HttpStatus.OK, "0000")));

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(count("searchKeyword2", "ok"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("HTTP 200이어도 본문 resultCode가 성공이 아니면 result_코드로 기록")
    void recordsNonSuccessResultCode() {

        Kor2ServiceClient client = client(List.of(json(HttpStatus.OK, "22")));

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(count("searchKeyword2", "result_22"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("HTTP 429는 1초 뒤 한 번만 재시도하고 각 응답을 기록")
    void retriesOnceOnTooManyRequests() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(
                        status(HttpStatus.TOO_MANY_REQUESTS),
                        json(HttpStatus.OK, "0000")
                ),
                calls
        );

        StepVerifier
                .withVirtualTime(() -> client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(calls.get())
                .isEqualTo(2);
        assertThat(count("searchKeyword2", "http_429"))
                .isEqualTo(1.0);
        assertThat(count("searchKeyword2", "ok"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("일일 한도 22는 HTTP 429여도 재시도 제외")
    void doesNotRetryDailyQuotaEvenOnTooManyRequests() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(gatewayXml(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "22",
                        "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"
                )),
                calls
        );

        StepVerifier
                .withVirtualTime(() -> client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .expectError(WebClientResponseException.TooManyRequests.class)
                .verify(Duration.ofSeconds(5));

        assertThat(calls.get())
                .isEqualTo(1);
        assertThat(count("searchKeyword2", "http_429_gateway_22"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("초당 한도 23과 HTTP 429는 기존 한 번 재시도 유지")
    void retainsSingleRetryForPerSecondQuotaOnTooManyRequests() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(
                        gatewayXml(
                                HttpStatus.TOO_MANY_REQUESTS,
                                "23",
                                "LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR"
                        ),
                        json(HttpStatus.OK, "0000")
                ),
                calls
        );

        StepVerifier
                .withVirtualTime(() -> client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .expectNextCount(1)
                .verifyComplete();

        assertThat(calls.get())
                .isEqualTo(2);
        assertThat(count("searchKeyword2", "http_429_gateway_23"))
                .isEqualTo(1.0);
        assertThat(count("searchKeyword2", "ok"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("재시도 후에도 429면 예외 전파")
    void propagatesRepeatedTooManyRequests() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(
                        status(HttpStatus.TOO_MANY_REQUESTS),
                        status(HttpStatus.TOO_MANY_REQUESTS)
                ),
                calls
        );

        StepVerifier
                .withVirtualTime(() -> client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(1))
                .expectError(WebClientResponseException.TooManyRequests.class)
                .verify();

        assertThat(calls.get())
                .isEqualTo(2);
        assertThat(count("searchKeyword2", "http_429"))
                .isEqualTo(2.0);
    }

    @Test
    @DisplayName("429 외 HTTP 오류는 재시도하지 않고 http_상태로 기록")
    void doesNotRetryOtherHttpErrors() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(status(HttpStatus.INTERNAL_SERVER_ERROR)),
                calls
        );

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectError(WebClientResponseException.InternalServerError.class)
                .verify();

        assertThat(calls.get())
                .isEqualTo(1);
        assertThat(count("searchKeyword2", "http_500"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("2xx 게이트웨이 오류 XML은 반환 코드로 기록하고 기존 디코딩 실패 유형으로 전파")
    void recordsGatewayReasonCodeOfSuccessStatus() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(gatewayXml(
                        HttpStatus.OK,
                        "23",
                        "LIMITED_NUMBER_OF_SERVICE_REQUESTS_PER_SECOND_EXCEEDS_ERROR"
                )),
                calls
        );

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(DecodingException.class)
                        .hasMessageContaining("returnReasonCode=23"))
                .verify();

        assertThat(calls.get())
                .isEqualTo(1);
        assertThat(count("searchKeyword2", "gateway_23"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("text/xml 2xx 게이트웨이 오류도 반환 코드로 기록")
    void recordsGatewayReasonCodeOfXmlContentType() {

        Kor2ServiceClient client = client(List.of(gatewayXml(
                HttpStatus.OK,
                MediaType.TEXT_XML,
                "22",
                "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"
        )));

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectError(DecodingException.class)
                .verify();

        assertThat(count("searchKeyword2", "gateway_22"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("오류 상태의 게이트웨이 오류 XML은 상태와 반환 코드를 함께 기록")
    void recordsGatewayReasonCodeOfErrorStatus() {

        AtomicInteger calls = new AtomicInteger();

        Kor2ServiceClient client = client(
                List.of(gatewayXml(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "22",
                        "LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR"
                )),
                calls
        );

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectError(WebClientResponseException.InternalServerError.class)
                .verify();

        assertThat(calls.get())
                .isEqualTo(1);
        assertThat(count("searchKeyword2", "http_500_gateway_22"))
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("게이트웨이 형식이 아닌 해석 불가 본문은 decode_error로 기록")
    void recordsDecodeError() {

        Kor2ServiceClient client = client(List.of(ClientResponse
                .create(HttpStatus.OK)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .body("<html>temporary error</html>")
                .build()));

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectError(DecodingException.class)
                .verify();

        assertThat(count("searchKeyword2", "decode_error"))
                .isEqualTo(1.0);
    }

    private Kor2ServiceClient client(List<ClientResponse> responses) {

        return client(
                responses,
                new AtomicInteger()
        );
    }

    private Kor2ServiceClient client(
            List<ClientResponse> responses,
            AtomicInteger calls
    ) {

        WebClient.Builder builder = WebClient
                .builder()
                .exchangeFunction(request -> Mono.fromSupplier(() -> responses.get(Math.min(
                        calls.getAndIncrement(),
                        responses.size() - 1
                ))));

        return new Kor2ServiceClient(
                builder,
                new Kor2ServiceProperties(
                        "https://apis.data.go.kr/B551011/KorService2",
                        "secret"
                ),
                meterRegistry
        );
    }

    private ClientResponse json(
            HttpStatus status,
            String resultCode
    ) {

        return ClientResponse
                .create(status)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .body("""
                        {"response":{"header":{"resultCode":"%s","resultMsg":"OK"},
                        "body":{"items":"","numOfRows":0,"pageNo":1,"totalCount":0}}}
                        """.formatted(resultCode))
                .build();
    }

    // 공공데이터포털 게이트웨이 오류 형식(TourAPI 활용매뉴얼 v4.4 예시 구조)
    private ClientResponse gatewayXml(
            HttpStatus status,
            String reasonCode,
            String authMessage
    ) {

        return gatewayXml(
                status,
                MediaType.APPLICATION_JSON,
                reasonCode,
                authMessage
        );
    }

    private ClientResponse gatewayXml(
            HttpStatus status,
            MediaType contentType,
            String reasonCode,
            String authMessage
    ) {

        return ClientResponse
                .create(status)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        contentType.toString()
                )
                .body("""
                        <OpenAPI_ServiceResponse>
                            <cmmMsgHeader>
                                <errMsg>SERVICE ERROR</errMsg>
                                <returnAuthMsg>%s</returnAuthMsg>
                                <returnReasonCode>%s</returnReasonCode>
                            </cmmMsgHeader>
                        </OpenAPI_ServiceResponse>
                        """.formatted(
                        authMessage,
                        reasonCode
                ))
                .build();
    }

    private ClientResponse status(HttpStatus status) {

        return ClientResponse
                .create(status)
                .build();
    }

    private double count(
            String endpoint,
            String outcome
    ) {

        var counter = meterRegistry
                .find("planb.external.kor2.response")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .counter();

        return counter == null
                ? 0
                : counter.count();
    }
}
