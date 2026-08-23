package com.planb.global.client;

import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriBuilder;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class ApiClient<P extends ApiProperties> {

    protected final WebClient webClient;
    protected P properties;

    protected ApiClient(
            WebClient.Builder webClientBuilder,
            P properties
    ) {

        this.properties = properties;

        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
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
}