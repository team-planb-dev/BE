package com.planb.global.client.foodNtrCpnt.properties;

import com.planb.global.client.ApiProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "external.food-ntr-cpnt")
public record FoodNtrCpntProperties
        (String baseUrl,
         String serviceKey)
        implements ApiProperties {
}