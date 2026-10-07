package com.planb.global.client.kor2Service;

import com.planb.global.client.ApiClient;
import com.planb.global.client.kor2Service.properties.Kor2ServiceProperties;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.DecodingException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

/**
 * TourAPI(KorService2) 호출과 응답 결과 계측
 *
 * 게이트웨이 한도 초과(일일 22, 초당 23)가 HTTP 상태와 본문 resultCode 중 어디로 오는지
 * 공식 문서에 없어 둘 다 기록. 짧은 시간 초과로 볼 수 있는 HTTP 429만 1회 재시도
 */
@Slf4j
@Component
public class Kor2ServiceClient
        extends ApiClient<Kor2ServiceProperties> {

    private static final Set<String> SUCCESS_CODES = Set.of(
            "0000",
            "00"
    );

    private static final Duration TOO_MANY_REQUESTS_DELAY = Duration.ofSeconds(1);

    private final MeterRegistry meterRegistry;

    public Kor2ServiceClient(
            WebClient.Builder webClientBuilder,
            Kor2ServiceProperties properties,
            MeterRegistry meterRegistry
    ) {

        super(webClientBuilder, properties);

        this.meterRegistry = meterRegistry;
    }

    public String serviceKey() {
        return properties.serviceKey();
    }

    public String baseUrl() {
        return properties.baseUrl();
    }

    // 완성된 URI 기반 GET API 호출, 응답 결과 기록과 429 재시도
    @Override
    public <R> Mono<R> get(
            URI uri,
            Class<R> responseType
    ) {

        String endpoint = endpoint(uri);

        return Mono
                .defer(() -> super
                        .get(
                                uri,
                                responseType
                        )
                        .doOnNext(body -> record(
                                endpoint,
                                outcome(body)
                        ))
                        .doOnError(failure -> record(
                                endpoint,
                                outcome(failure)
                        )))
                .retryWhen(Retry
                        .fixedDelay(
                                1,
                                TOO_MANY_REQUESTS_DELAY
                        )
                        .filter(Kor2ServiceClient::tooManyRequests)
                        .onRetryExhaustedThrow((spec, signal) -> signal.failure()));
    }

    private void record(
            String endpoint,
            String outcome
    ) {

        if (!"ok".equals(outcome)) {
            // URI 쿼리에는 serviceKey가 있어 경로만 기록
            log.warn(
                    "[TOURAPI] 비정상 응답 - endpoint: {}, outcome: {}",
                    endpoint,
                    outcome
            );
        }

        Counter
                .builder("planb.external.kor2.response")
                .tag("endpoint", endpoint)
                .tag("outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

    private static String outcome(Object body) {

        String resultCode = body instanceof Kor2Result result
                ? result.resultCode()
                : null;

        return resultCode == null || SUCCESS_CODES.contains(resultCode)
                ? "ok"
                : "result_" + resultCode;
    }

    private static String outcome(Throwable failure) {

        if (failure instanceof WebClientResponseException response) {
            return "http_" + response
                    .getStatusCode()
                    .value();
        }

        if (failure instanceof DecodingException) {
            return "decode_error";
        }

        return "error";
    }

    private static boolean tooManyRequests(Throwable failure) {

        return failure instanceof WebClientResponseException response
                && response.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS;
    }

    // 쿼리 제외 마지막 경로 조각 (예: searchKeyword2)
    private static String endpoint(URI uri) {

        String path = uri.getPath();

        if (path == null || path.isBlank()) {
            return "unknown";
        }

        return path.substring(path.lastIndexOf('/') + 1);
    }
}
