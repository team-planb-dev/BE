package com.planb.global.client.kor2Service.handler;

import com.planb.global.client.helper.DataUriBuilder;
import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2AreaCodeResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import com.planb.global.client.kor2Service.Kor2ResponseCache;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.time.Duration;
import java.util.Set;

@Component
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

    // 지역 목록·키워드 검색은 하루, 음식점 메뉴 상세는 일주일 보존
    private static final Duration SEARCH_TTL = Duration.ofDays(1);

    private static final Duration DETAIL_TTL = Duration.ofDays(7);

    private final Kor2ServiceClient kor2ServiceClient;

    private final Kor2ResponseCache kor2ResponseCache;

    // 캐시 없는 생성 (단위 테스트·수동 생성용)
    public Kor2ServiceHandler(Kor2ServiceClient kor2ServiceClient) {

        this(
                kor2ServiceClient,
                Kor2ResponseCache.disabled()
        );
    }

    @Autowired
    public Kor2ServiceHandler(
            Kor2ServiceClient kor2ServiceClient,
            Kor2ResponseCache kor2ResponseCache
    ) {

        this.kor2ServiceClient = kor2ServiceClient;
        this.kor2ResponseCache = kor2ResponseCache;
    }

    // Ko2Service API : 키워드만으로 관광정보 검색 (캐시 우선)
    public Mono<Kor2KeywordSearchResponse> searchKeywordOnly(String keyword) {

        return kor2ResponseCache.cached(
                "searchKeyword2",
                keyword,
                SEARCH_TTL,
                Kor2KeywordSearchResponse.class,
                () -> fetchKeywordOnly(keyword)
        );
    }

    // Ko2Service API : 지역 관광지 후보 (캐시 우선)
    public Mono<Kor2KeywordSearchResponse> searchAttractions(
            String locationDo,
            String locationSigungu
    ) {

        return kor2ResponseCache.cached(
                "attractions",
                locationDo + ":" + locationSigungu,
                SEARCH_TTL,
                Kor2KeywordSearchResponse.class,
                () -> fetchAttractions(
                        locationDo,
                        locationSigungu
                )
        );
    }

    // Ko2Service API : 지역 음식점 키워드 검색 (캐시 우선)
    public Mono<Kor2KeywordSearchResponse> searchRestaurants(
            String keyword,
            String locationDo,
            String locationSigungu
    ) {

        return kor2ResponseCache.cached(
                "restaurants",
                keyword + ":" + locationDo + ":" + locationSigungu,
                SEARCH_TTL,
                Kor2KeywordSearchResponse.class,
                () -> fetchRestaurants(
                        keyword,
                        locationDo,
                        locationSigungu
                )
        );
    }

    // Ko2Service API : 지역 음식점 후보 (캐시 우선)
    public Mono<Kor2KeywordSearchResponse> searchRestaurantCandidates(
            String locationDo,
            String locationSigungu
    ) {

        return kor2ResponseCache.cached(
                "restaurantCandidates",
                locationDo + ":" + locationSigungu,
                SEARCH_TTL,
                Kor2KeywordSearchResponse.class,
                () -> fetchRestaurantCandidates(
                        locationDo,
                        locationSigungu
                )
        );
    }

    // Ko2Service API : 음식점 상세정보 (캐시 우선)
    public Mono<Kor2RestaurantIntroResponse> getRestaurantDetail(String contentId) {

        return kor2ResponseCache.cached(
                "detailIntro2",
                contentId,
                DETAIL_TTL,
                Kor2RestaurantIntroResponse.class,
                () -> fetchRestaurantDetail(contentId)
        );
    }

    // Ko2Service API : 키워드만으로 관광정보 검색
    private Mono<Kor2KeywordSearchResponse> fetchKeywordOnly(
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
    private Mono<Kor2KeywordSearchResponse> fetchAttractions(
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
                        return searchByAreaCode(
                                areaCode,
                                null,
                                12
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
                                    searchByAreaCode(
                                            areaCode,
                                            sigunguCode,
                                            12
                                    )
                            );
                });
    }


    // Ko2Service API : 광역 지역은 시/도, 도 지역은 시/군 기준 음식점 키워드 검색
    private Mono<Kor2KeywordSearchResponse> fetchRestaurants(
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
                .flatMap(areaCode -> {
                    if (METROPOLITAN_AREAS.contains(locationDo)) {
                        return searchRestaurantByCode(
                                keyword,
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
                                    searchRestaurantByCode(
                                            keyword,
                                            areaCode,
                                            sigunguCode
                                    )
                            );
                });
    }


    // Ko2Service API : 광역 지역은 시/도, 도 지역은 시/군 기준 음식점 후보 조회
    private Mono<Kor2KeywordSearchResponse> fetchRestaurantCandidates(
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
                        return searchByAreaCode(
                                areaCode,
                                null,
                                39
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
                                    searchByAreaCode(
                                            areaCode,
                                            sigunguCode,
                                            39
                                    )
                            );
                });
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


    // Ko2Service API : 지역코드와 콘텐츠 유형 기반 후보 조회
    private Mono<Kor2KeywordSearchResponse> searchByAreaCode(
            String areaCode,
            String sigunguCode,
            int contentTypeId
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
                        contentTypeId
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
                        item
                                .name()
                                .equals(location)
                )
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException(
                                "TourAPI 지역코드를 찾을 수 없습니다. 입력값: " + location
                        )
                )
                .code();
    }


    // Ko2Service API : 음식점 상세정보 조회
    private Mono<Kor2RestaurantIntroResponse> fetchRestaurantDetail(
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
