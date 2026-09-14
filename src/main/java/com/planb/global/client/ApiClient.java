package com.planb.global.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ApiClient<P extends ApiProperties> {

    // Spring 기본값은 256KB다. 카카오 대중교통 경로는 장거리 구간에서 이를 넘겨
    // DataBufferLimitException으로 응답 전체가 버려진다.
    private static final int MAX_IN_MEMORY_BYTES = 2 * 1024 * 1024;

    protected final WebClient webClient;
    protected P properties;

    protected ApiClient(
            WebClient.Builder webClientBuilder,
            P properties
    ) {

        this.properties = properties;

        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .codecs(configurer -> configurer
                        .defaultCodecs()
                        .maxInMemorySize(MAX_IN_MEMORY_BYTES))
                .build();
    }

    // GET API 호출
    public <R> Mono<R> get(
            Function<UriBuilder, URI> uriFunction,
            Class<R> responseType
    ) {

        return webClient
                .get()
                .uri(uriFunction)
                .retrieve()
                .bodyToMono(responseType);
    }

    // Header가 필요한 GET API 호출
    public <R> Mono<R> get(
            Function<UriBuilder, URI> uriFunction,
            Consumer<HttpHeaders> headersConsumer,
            Class<R> responseType
    ) {

        return webClient
                .get()
                .uri(uriFunction)
                .headers(headersConsumer)
                .retrieve()
                .bodyToMono(responseType);
    }

    // 완성된 URI 기반 GET API 호출
    public <R> Mono<R> get
    (URI uri,
     Class<R> responseType) {

        return webClient
                .get()
                .uri(uri)
                .retrieve()
                .bodyToMono(responseType);
    }
}