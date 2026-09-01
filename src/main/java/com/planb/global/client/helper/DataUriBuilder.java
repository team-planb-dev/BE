package com.planb.global.client.helper;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class DataUriBuilder {

    private final String baseUrl;
    private final String path;
    private final String serviceKey;

    private final Map<String, Object> queryParams =
            new LinkedHashMap<>();

    private DataUriBuilder
            (String baseUrl,
             String path,
             String serviceKey) {

        this.baseUrl = baseUrl;
        this.path = path;
        this.serviceKey = serviceKey;
    }

    public static DataUriBuilder from
            (String baseUrl,
             String path,
             String serviceKey) {

        return new DataUriBuilder(
                baseUrl,
                path,
                serviceKey
        );
    }

    public DataUriBuilder queryParam
            (String name,
             Object value) {

        if (value != null) {
            queryParams.put(
                    name,
                    value
            );
        }

        return this;
    }

    public URI build() {

        StringBuilder url = new StringBuilder()
                .append(baseUrl)
                .append(path)
                .append("?serviceKey=")
                .append(serviceKey);

        queryParams.forEach(
                (name, value) -> url
                        .append("&")
                        .append(name)
                        .append("=")
                        .append(
                                encode(
                                        String.valueOf(value)
                                )
                        )
        );

        return URI.create(
                url.toString()
        );
    }

    private static String encode(String value) {

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }
}