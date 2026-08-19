package com.planb.global.client.kor2Service.handler;

import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class Kor2ServiceHandler {

    private final Kor2ServiceClient kor2ServiceClient;


    // Ko2Service API : 키워드로 검색하기
    public Mono<Kor2KeywordSearchResponse> searchKeyword(
            String keyword
    ) {

        return kor2ServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/searchKeyword2")
                        .queryParam("serviceKey", kor2ServiceClient.serviceKey())
                        .queryParam("MobileOS", "ETC")
                        .queryParam("MobileApp", "PlanB")
                        .queryParam("_type", "json")
                        .queryParam("keyword", keyword)
                        .build(),
                Kor2KeywordSearchResponse.class
        );
    }



}
