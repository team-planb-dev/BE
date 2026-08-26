package com.planb.global.client.kor2Service.handler;

import com.planb.global.client.helper.DataUriBuilder;
import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;

@Component
@RequiredArgsConstructor
public class Kor2ServiceHandler {

    private final Kor2ServiceClient kor2ServiceClient;


    // Ko2Service API : 키워드로 검색하기
    public Mono<Kor2KeywordSearchResponse> searchKeyword(
            String keyword
    ) {

        URI uri = DataUriBuilder
                .from(
                        kor2ServiceClient.baseUrl(),
                        "/searchKeyword2",
                        kor2ServiceClient.serviceKey()
                )
                .queryParam(
                        "MobileOS",
                        "ETC"
                )
                .queryParam(
                        "MobileApp",
                        "PlanB"
                )
                .queryParam(
                        "_type",
                        "json"
                )
                .queryParam(
                        "keyword",
                        keyword
                )
                .build();

        return kor2ServiceClient
                .get(
                        uri,
                        Kor2KeywordSearchResponse.class
                );
    }

    // Ko2Service API : 음식점 상세정보 조회
    public Mono<Kor2RestaurantIntroResponse> getRestaurantDetail
    (String contentId) {

        URI uri = DataUriBuilder
                .from(
                        kor2ServiceClient.baseUrl(),
                        "/detailIntro2",
                        kor2ServiceClient.serviceKey()
                )
                .queryParam(
                        "MobileOS",
                        "ETC"
                )
                .queryParam(
                        "MobileApp",
                        "PlanB"
                )
                .queryParam(
                        "_type",
                        "json"
                )
                .queryParam(
                        "contentId",
                        contentId
                )
                .queryParam(
                        "contentTypeId",
                        39
                )
                .build();

        return kor2ServiceClient
                .get(
                        uri,
                        Kor2RestaurantIntroResponse.class
                );
    }
}