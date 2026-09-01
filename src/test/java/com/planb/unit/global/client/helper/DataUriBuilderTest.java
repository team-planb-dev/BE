package com.planb.unit.global.client.helper;

import com.planb.global.client.helper.DataUriBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DataUriBuilderTest {

    @Test
    @DisplayName("기본 URI 생성")
    void buildBasicUri() {

        String baseUrl =
                "https://apis.data.go.kr";

        String path =
                "/test/path";

        String serviceKey =
                "testServiceKey";

        URI result =
                DataUriBuilder.from(
                                baseUrl,
                                path,
                                serviceKey
                        )
                        .build();

        assertEquals(
                "https://apis.data.go.kr/test/path"
                        + "?serviceKey=testServiceKey",
                result.toString()
        );
    }

    @Test
    @DisplayName("Query Parameter 포함 URI 생성")
    void buildUriWithQueryParam() {

        String baseUrl =
                "https://apis.data.go.kr";

        String path =
                "/test/path";

        String serviceKey =
                "testServiceKey";

        URI result =
                DataUriBuilder.from(
                                baseUrl,
                                path,
                                serviceKey
                        )
                        .queryParam(
                                "pageNo",
                                1
                        )
                        .queryParam(
                                "numOfRows",
                                10
                        )
                        .build();

        assertEquals(
                "https://apis.data.go.kr/test/path"
                        + "?serviceKey=testServiceKey"
                        + "&pageNo=1"
                        + "&numOfRows=10",
                result.toString()
        );
    }

    @Test
    @DisplayName("Query Parameter URL Encoding")
    void encodeQueryParam() {

        String baseUrl =
                "https://apis.data.go.kr";

        String path =
                "/test/path";

        String serviceKey =
                "testServiceKey";

        URI result =
                DataUriBuilder.from(
                                baseUrl,
                                path,
                                serviceKey
                        )
                        .queryParam(
                                "foodName",
                                "돼지 국밥"
                        )
                        .build();

        assertEquals(
                "https://apis.data.go.kr/test/path"
                        + "?serviceKey=testServiceKey"
                        + "&foodName=%EB%8F%BC%EC%A7%80+%EA%B5%AD%EB%B0%A5",
                result.toString()
        );
    }

    @Test
    @DisplayName("Null Query Parameter 제외")
    void excludeNullQueryParam() {

        String baseUrl =
                "https://apis.data.go.kr";

        String path =
                "/test/path";

        String serviceKey =
                "testServiceKey";

        URI result =
                DataUriBuilder.from(
                                baseUrl,
                                path,
                                serviceKey
                        )
                        .queryParam(
                                "keyword",
                                null
                        )
                        .queryParam(
                                "pageNo",
                                1
                        )
                        .build();

        assertEquals(
                "https://apis.data.go.kr/test/path"
                        + "?serviceKey=testServiceKey"
                        + "&pageNo=1",
                result.toString()
        );
    }
}