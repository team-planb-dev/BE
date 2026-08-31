package com.planb.global.client.kor2Service.handler;

import com.planb.global.client.helper.DataUriBuilder;
import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2AreaCodeResponse;
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

    // Ko2Service API : 키워드만으로 관광정보 검색
    public Mono<Kor2KeywordSearchResponse> searchKeywordOnly(
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
                        "numOfRows",
                        100
                )
                .queryParam(
                        "pageNo",
                        1
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


    // Ko2Service API : 지역 조건을 포함한 키워드 검색
    public Mono<Kor2KeywordSearchResponse> searchKeyword(
            String keyword,
            String locationDo,
            String locationSigungu,
            Integer contentTypeId
    ) {

        return getAreaCode()
                .map(response ->
                        findCode(
                                response,
                                locationDo
                        )
                )
                .flatMap(areaCode ->
                        getSigunguCode(areaCode)
                                .map(response ->
                                        findCode(
                                                response,
                                                locationSigungu
                                        )
                                )
                                .flatMap(sigunguCode ->
                                        searchKeywordByCode(
                                                keyword,
                                                areaCode,
                                                sigunguCode,
                                                contentTypeId
                                        )
                                )
                );
    }


    // Ko2Service API : 시/도 지역코드 조회
    private Mono<Kor2AreaCodeResponse> getAreaCode() {

        URI uri = DataUriBuilder
                .from(
                        kor2ServiceClient.baseUrl(),
                        "/areaCode2",
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
                        "numOfRows",
                        100
                )
                .queryParam(
                        "pageNo",
                        1
                )
                .build();

        return kor2ServiceClient
                .get(
                        uri,
                        Kor2AreaCodeResponse.class
                );
    }


    // Ko2Service API : 시/군/구 지역코드 조회
    private Mono<Kor2AreaCodeResponse> getSigunguCode(
            String areaCode
    ) {

        URI uri = DataUriBuilder
                .from(
                        kor2ServiceClient.baseUrl(),
                        "/areaCode2",
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
                        "numOfRows",
                        100
                )
                .queryParam(
                        "pageNo",
                        1
                )
                .queryParam(
                        "areaCode",
                        areaCode
                )
                .build();

        return kor2ServiceClient
                .get(
                        uri,
                        Kor2AreaCodeResponse.class
                );
    }


    // Ko2Service API : 지역코드 기반 실제 키워드 검색
    private Mono<Kor2KeywordSearchResponse> searchKeywordByCode(
            String keyword,
            String areaCode,
            String sigunguCode,
            Integer contentTypeId
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
                        "numOfRows",
                        100
                )
                .queryParam(
                        "pageNo",
                        1
                )
                .queryParam(
                        "keyword",
                        keyword
                )
                .queryParam(
                        "areaCode",
                        areaCode
                )
                /*
                .queryParam(
                        "sigunguCode",
                        sigunguCode
                )

                 */
                .queryParam(
                        "contentTypeId",
                        contentTypeId
                )
                .build();

        return kor2ServiceClient
                .get(
                        uri,
                        Kor2KeywordSearchResponse.class
                );
    }


    // 지역명과 일치하는 지역코드 추출
    private String findCode(
            Kor2AreaCodeResponse response,
            String location
    ) {

        return response
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(item ->
                        item.name().equals(location)
                )
                .findFirst()
                .orElseThrow()
                .code();
    }


    // Ko2Service API : 음식점 상세정보 조회
    public Mono<Kor2RestaurantIntroResponse> getRestaurantDetail(
            String contentId
    ) {

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
                        "numOfRows",
                        1
                )
                .queryParam(
                        "pageNo",
                        1
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