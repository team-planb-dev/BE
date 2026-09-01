package com.planb.global.client.kakaoMobilityService;

import com.planb.global.client.ApiClient;
import com.planb.global.client.kakaoMobilityService.properties.KakaoMobilityServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class KakaoMobilityServiceClient
        extends ApiClient<KakaoMobilityServiceProperties> {

    public KakaoMobilityServiceClient(
            WebClient.Builder webClientBuilder,
            KakaoMobilityServiceProperties properties
    ) {
        super(
                webClientBuilder,
                properties
        );
    }

    public String serviceKey() {
        return properties.apiKey();
    }

    public String baseUrl(){
        return properties.baseUrl();
    }
}