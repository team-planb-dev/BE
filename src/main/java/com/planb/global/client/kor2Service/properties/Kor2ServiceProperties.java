package com.planb.global.client.kor2Service.properties;

import com.planb.global.client.ApiProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.kor2-service")
public record Kor2ServiceProperties
        (String baseUrl,
         String serviceKey)
        implements ApiProperties {
}
