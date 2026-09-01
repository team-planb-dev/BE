package com.planb.global.client.kakaoMapService.properties;

import com.planb.global.client.ApiProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "external.kakao-map"
)
public record KakaoMapServiceProperties
        (String baseUrl,
         String apiKey)
        implements ApiProperties {
}
