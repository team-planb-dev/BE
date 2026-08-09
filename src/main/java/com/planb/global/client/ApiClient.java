package com.planb.global.client;

import org.springframework.web.reactive.function.client.WebClient;

public abstract class ApiClient<P extends ApiProperties> {

    protected final WebClient webClient;
    protected P properties;

    protected ApiClient
            (WebClient.Builder webClientBuilder,
             P properties) {

        this.properties = properties;
        this.webClient = webClientBuilder
                .baseUrl(properties.baseUrl())
                .build();
    }
}
