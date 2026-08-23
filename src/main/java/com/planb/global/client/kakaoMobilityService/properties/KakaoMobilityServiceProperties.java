package com.planb.global.client.kakaoMobilityService.properties;

import com.planb.global.client.ApiProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(
        prefix = "external.kakao-mobility"
)
public record KakaoMobilityServiceProperties(
        String baseUrl,
        String apiKey
) implements ApiProperties {
}