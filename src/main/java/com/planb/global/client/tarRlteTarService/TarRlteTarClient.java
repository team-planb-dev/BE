package com.planb.global.client.tarRlteTarService;

import com.planb.global.client.ApiClient;
import com.planb.global.client.tarRlteTarService.properties.TarRlteTarServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class TarRlteTarClient
        extends ApiClient<TarRlteTarServiceProperties> {

    public TarRlteTarClient(
            WebClient.Builder webClientBuilder,
            TarRlteTarServiceProperties properties
    ) {
        super(webClientBuilder, properties);
    }
}