package com.planb.global.config.external;


import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationPropertiesScan(basePackages = "com.planb.global.client")
public class ExternalAPIConfig {
}
