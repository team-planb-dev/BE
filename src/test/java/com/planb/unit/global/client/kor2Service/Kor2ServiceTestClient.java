package com.planb.unit.global.client.kor2Service;

import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.properties.Kor2ServiceProperties;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
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
    @DisplayName("JSON이 아닌 게이트웨이 오류 본문은 decode_error로 기록")
    void recordsDecodeError() {

        Kor2ServiceClient client = client(List.of(ClientResponse
                .create(HttpStatus.OK)
                .header(
                        HttpHeaders.CONTENT_TYPE,
                        MediaType.APPLICATION_JSON_VALUE
                )
                .body("<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>23</returnReasonCode></cmmMsgHeader></OpenAPI_ServiceResponse>")
                .build()));

        StepVerifier
                .create(client.get(
                        SEARCH_URI,
                        Kor2KeywordSearchResponse.class
                ))
                .expectError()
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
