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
import java.util.Set;

@Component
@RequiredArgsConstructor
public class Kor2ServiceHandler {

    private static final Set<String> METROPOLITAN_AREAS = Set.of(
            "서울",
            "인천",
            "대전",
            "대구",
            "광주",
            "부산",
            "울산",
            "세종특별자치시"
    );

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


    // Ko2Service API : 광역 지역은 시/도, 도 지역은 시/군 기준 관광지 후보 조회
    public Mono<Kor2KeywordSearchResponse> searchAttractions(
            String locationDo,
            String locationSigungu
    ) {

        return getAreaCode()
                .map(response ->
                        findCode(
                                response,
                                locationDo
                        )
                )
                .flatMap(areaCode -> {
                    if (METROPOLITAN_AREAS.contains(locationDo)) {
                        return searchAttractionsByAreaCode(
                                areaCode,
                                null
                        );
                    }

                    return getSigunguCode(areaCode)
                            .map(response ->
                                    findCode(
                                            response,
                                            locationSigungu
                                    )
                            )
                            .flatMap(sigunguCode ->
                                    searchAttractionsByAreaCode(
                                            areaCode,
                                            sigunguCode
                                    )
                            );
                });
    }


    // Ko2Service API : 시/군/구 기준 음식점 키워드 검색
    public Mono<Kor2KeywordSearchResponse> searchRestaurants(
            String keyword,
            String locationDo,
            String locationSigungu
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
                                        searchRestaurantByCode(
                                                keyword,
                                                areaCode,
                                                sigunguCode
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


    // Ko2Service API : 지역코드 기반 관광지 후보 조회
    private Mono<Kor2KeywordSearchResponse> searchAttractionsByAreaCode(
            String areaCode,
            String sigunguCode
    ) {

        URI uri = DataUriBuilder
                .from(
                        kor2ServiceClient.baseUrl(),
                        "/areaBasedList2",
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
                .queryParam(
                        "sigunguCode",
                        sigunguCode
                )
                .queryParam(
                        "contentTypeId",
                        12
                )
                .build();

        return kor2ServiceClient
                .get(
                        uri,
                        Kor2KeywordSearchResponse.class
                );
    }


    // Ko2Service API : 시/군/구 코드 기반 음식점 키워드 검색
    private Mono<Kor2KeywordSearchResponse> searchRestaurantByCode(
            String keyword,
            String areaCode,
            String sigunguCode
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
                .queryParam(
                        "sigunguCode",
                        sigunguCode
                )
                .queryParam(
                        "contentTypeId",
                        39
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
