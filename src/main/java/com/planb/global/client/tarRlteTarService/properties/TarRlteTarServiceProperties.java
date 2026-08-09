package com.planb.global.client.tarRlteTarService.properties;

import com.planb.global.client.ApiProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.tar-rlte-tar-service")
public record TarRlteTarServiceProperties
        (String baseUrl,
         String serviceKey)
        implements ApiProperties {
}
