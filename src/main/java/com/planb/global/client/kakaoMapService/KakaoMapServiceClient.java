package com.planb.global.client.kakaoMapService;

import com.planb.global.client.ApiClient;
import com.planb.global.client.kakaoMapService.properties.KakaoMapServiceProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class KakaoMapServiceClient extends ApiClient<KakaoMapServiceProperties> {



    public KakaoMapServiceClient
            (WebClient.Builder webClientBuilder,
             KakaoMapServiceProperties properties) {
        super(webClientBuilder,properties);
    }

    public String serviceKey() {
        return properties.apiKey();
    }

    public String baseUrl(){
        return properties.baseUrl();
    }

}
