package com.planb.performance.external;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.foodNtrCpnt.FoodNtrCpntClient;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import com.planb.global.client.foodNtrCpnt.helper.FoodNtrCpntHelper;
import com.planb.global.client.foodNtrCpnt.properties.FoodNtrCpntProperties;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoSearchKeywordSanitizer;
import com.planb.global.client.kakaoMapService.properties.KakaoMapServiceProperties;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import com.planb.global.client.kakaoMobilityService.properties.KakaoMobilityServiceProperties;
import com.planb.global.client.kor2Service.Kor2ServiceClient;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import com.planb.global.client.kor2Service.properties.Kor2ServiceProperties;
import com.planb.performance.external.ExternalHttpStubServer.Api;
import com.planb.performance.external.ExternalHttpStubServer.RecordedRequest;
import com.planb.performance.external.ExternalHttpStubServer.Scenario;
import com.planb.performance.external.ExternalHttpStubServer.Settings;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ExternalHttpStubServerTest {

    private ExternalHttpStubServer stub;

    private Kor2ServiceHandler kor2;

    private KakaoMapServiceHandler kakao;

    private FoodNtrCpntHandler food;

    @AfterEach
    void stop() {

        if (stub != null) {
            stub.close();
        }
    }

    @Test
    @DisplayName("도 지역 관광지 조회는 지역코드·시군구코드·지역목록 순으로 스텁을 호출")
    void provinceAttractionsResolveAreaAndSigunguCodes() {

        start(Settings.normal());

        Kor2KeywordSearchResponse response = kor2
                .searchAttractions(
                        "경상북도",
                        "경주시"
                )
                .block();

        assertThat(items(response))
                .isNotEmpty();

        List<RecordedRequest> requests = stub.requests(Api.KOR2);

        assertThat(requests)
                .extracting(RecordedRequest::path)
                .containsExactly(
                        "/areaCode2",
                        "/areaCode2",
                        "/areaBasedList2"
                );

        assertThat(requests
                .get(1)
                .query())
                .containsEntry("areaCode", "35");

        assertThat(requests
                .get(2)
                .query())
                .containsEntry("areaCode", "35")
                .containsEntry("contentTypeId", "12")
                .containsKey("sigunguCode");
    }

    @Test
    @DisplayName("관광지 fixture는 TourismTool의 후보 필터를 모두 통과")
    void attractionFixturesPassToolCandidateFilter() {

        start(Settings.normal());

        TourismTool tool = new TourismTool(
                kor2,
                kakao,
                mock(NutritionService.class),
                mock(NutritionEvaluationCollector.class)
        );

        int fixtureSize = items(
                kor2
                        .searchAttractions(
                                "서울",
                                "종로구"
                        )
                        .block()
        )
                .size();

        List<Kor2KeywordSearchResponse.Item> candidates = items(
                tool.searchAttractionsByRegion(
                        "서울",
                        "종로구"
                )
        );

        assertThat(candidates)
                .hasSize(fixtureSize);
    }

    @Test
    @DisplayName("음식점 검색과 상세 조회는 음식점마다 서로 다른 대표 메뉴를 돌려줌")
    void restaurantsHaveDistinctMenus() {

        start(Settings.normal());

        List<Kor2KeywordSearchResponse.Item> restaurants = items(
                kor2
                        .searchRestaurants(
                                "비빔밥",
                                "서울",
                                "종로구"
                        )
                        .block()
        );

        assertThat(restaurants)
                .isNotEmpty();

        assertThat(stub
                .requests(Api.KOR2)
                .getLast()
                .query())
                .containsEntry("keyword", "비빔밥")
                .containsEntry("contentTypeId", "39");

        Set<String> menus = restaurants
                .stream()
                .map(restaurant -> kor2
                        .getRestaurantDetail(restaurant.contentid())
                        .block()
                        .response()
                        .body()
                        .items()
                        .item()
                        .getFirst()
                        .firstmenu())
                .collect(Collectors.toSet());

        assertThat(menus)
                .hasSize(restaurants.size());
    }

    @Test
    @DisplayName("영양성분 조회는 요청한 음식명으로 응답해 이름 정확 일치 필터를 통과")
    void foodNutritionMatchesRequestedName() {

        start(Settings.normal());

        List<FoodNtrCpntResponse.Item> found = food
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest.of("한우광양불고기")
                )
                .block();

        assertThat(found)
                .singleElement()
                .extracting(FoodNtrCpntResponse.Item::foodName)
                .isEqualTo("한우광양불고기");

        assertThat(stub
                .requests(Api.FOOD_NUTRITION)
                .getFirst()
                .query())
                .containsEntry("FOOD_NM_KR", "한우광양불고기");
    }

    @Test
    @DisplayName("장소 검색은 같은 검색어에 같은 장소, 다른 검색어에 다른 장소를 돌려줌")
    void placeSearchIsStablePerKeyword() {

        start(Settings.normal());

        PlaceWithRouteResult first = findCafe("스텁 카페 하나");
        PlaceWithRouteResult again = findCafe("스텁 카페 하나");
        PlaceWithRouteResult other = findCafe("스텁 카페 둘");

        assertThat(first.found())
                .isTrue();
        assertThat(again)
                .isEqualTo(first);
        assertThat(other.candidateId())
                .isNotEqualTo(first.candidateId());
        assertThat(other.placeName())
                .isNotEqualTo(first.placeName());
        assertThat(first.travelMinutes())
                .isNotNull();

        assertThat(stub.requests(Api.KAKAO_MAP))
                .extracting(RecordedRequest::path)
                .contains(
                        "/v2/local/search/keyword.json",
                        "/v2/routing/publictraffic"
                );
    }

    @Test
    @DisplayName("자동차 경로는 Kakao Mobility 스텁의 소요시간을 분 단위로 돌려줌")
    void carRouteUsesMobilityStub() {

        start(Settings.normal());

        KakaoRouteResult route = kakao
                .getCarRoute(
                        "127.0",
                        "37.5",
                        "127.1",
                        "37.6"
                )
                .map(response -> new KakaoMobilityRouteHelper().makeCarRouteResult(
                        "출발",
                        "도착",
                        response
                ))
                .block();

        assertThat(route.travelMinutes())
                .isPositive();

        assertThat(stub
                .requests(Api.KAKAO_MOBILITY)
                .getFirst()
                .query())
                .containsEntry("origin", "127.0,37.5")
                .containsEntry("destination", "127.1,37.6");
    }

    @Test
    @DisplayName("같은 요청은 항상 같은 응답 본문을 받음")
    void sameRequestGetsSameBody() throws Exception {

        start(Settings.normal());

        String url = stub.baseUrl(Api.KOR2)
                + "/areaBasedList2?serviceKey=x&contentTypeId=39&areaCode=1";

        assertThat(get(url)
                        .body())
                .isEqualTo(get(url)
                        .body());
    }

    @Test
    @DisplayName("fixture가 없는 경로는 404로 드러냄")
    void unknownPathReturnsNotFound() throws Exception {

        start(Settings.normal());

        assertThat(get(stub.baseUrl(Api.KOR2) + "/unknown")
                        .statusCode())
                .isEqualTo(404);
    }

    @Test
    @DisplayName("API별 4xx 시나리오는 해당 API만 실패시키고 나머지는 정상 응답")
    void clientErrorScenarioAffectsOnlyTargetApi() {

        start(Settings
                .normal()
                .with(Api.KAKAO_MAP, Scenario.CLIENT_ERROR));

        assertThatThrownBy(() -> kakao
                .searchPlace("스텁 카페")
                .block())
                .isInstanceOf(WebClientResponseException.class)
                .satisfies(error -> assertThat(((WebClientResponseException) error)
                        .getStatusCode()
                        .is4xxClientError())
                        .isTrue());

        assertThat(items(
                kor2
                        .searchAttractions(
                                "서울",
                                "종로구"
                        )
                        .block()
        ))
                .isNotEmpty();
    }

    @Test
    @DisplayName("5xx 시나리오는 서버 오류로 응답")
    void serverErrorScenarioReturns5xx() {

        start(Settings
                .normal()
                .with(Api.FOOD_NUTRITION, Scenario.SERVER_ERROR));

        assertThatThrownBy(() -> food
                .getFoodNutrition(FoodNtrCpntSearchRequest.of("비빔밥"))
                .block())
                .isInstanceOf(WebClientResponseException.class)
                .satisfies(error -> assertThat(((WebClientResponseException) error)
                        .getStatusCode()
                        .is5xxServerError())
                        .isTrue());
    }

    @Test
    @DisplayName("지연 시나리오는 설정한 시간만큼 늦게 정상 응답")
    void delayScenarioRespondsLate() {

        start(new Settings(
                Scenario.NORMAL,
                Map.of(Api.KOR2, Scenario.DELAY),
                Duration.ofMillis(300),
                Duration.ofSeconds(5)
        ));

        long started = System.nanoTime();

        List<Kor2KeywordSearchResponse.Item> restaurants = items(
                kor2
                        .searchRestaurantCandidates(
                                "서울",
                                "종로구"
                        )
                        .block()
        );

        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(restaurants)
                .isNotEmpty();

        // 지역코드·목록 조회 모두 지연
        assertThat(elapsedMs)
                .isGreaterThanOrEqualTo(600);
    }

    @Test
    @DisplayName("timeout 시나리오는 응답 없이 연결을 붙잡았다가 끊음")
    void timeoutScenarioHoldsThenDropsConnection() {

        start(new Settings(
                Scenario.NORMAL,
                Map.of(Api.KAKAO_MOBILITY, Scenario.TIMEOUT),
                Duration.ofMillis(50),
                Duration.ofMillis(300)
        ));

        long started = System.nanoTime();

        assertThatThrownBy(() -> kakao
                .getCarRoute(
                        "127.0",
                        "37.5",
                        "127.1",
                        "37.6"
                )
                .block())
                .isInstanceOf(WebClientRequestException.class);

        assertThat((System.nanoTime() - started) / 1_000_000)
                .isGreaterThanOrEqualTo(300);
    }

    @Test
    @DisplayName("환경변수로 전체 시나리오와 API별 시나리오를 지정")
    void settingsFromEnvironment() {

        Settings settings = Settings.fromEnvironment(Map.of(
                        "STUB_SCENARIO",
                        "delay",
                        "STUB_SCENARIO_KAKAO_MAP",
                        "server-error",
                        "STUB_DELAY_MS",
                        "40",
                        "STUB_TIMEOUT_MS",
                        "900"
                ));

        assertThat(settings.scenarioOf(Api.KOR2))
                .isEqualTo(Scenario.DELAY);
        assertThat(settings.scenarioOf(Api.KAKAO_MAP))
                .isEqualTo(Scenario.SERVER_ERROR);
        assertThat(settings.delay())
                .isEqualTo(Duration.ofMillis(40));
        assertThat(settings.timeout())
                .isEqualTo(Duration.ofMillis(900));
    }

    @Test
    @DisplayName("기록한 요청은 비울 수 있음")
    void recordedRequestsCanBeCleared() {

        start(Settings.normal());

        kor2
                .searchAttractions(
                        "서울",
                        "종로구"
                )
                .block();

        assertThat(stub.requests(Api.KOR2))
                .isNotEmpty();

        stub.clear();

        assertThat(stub.requests(Api.KOR2))
                .isEmpty();
    }

    private void start(Settings settings) {

        stub = ExternalHttpStubServer.start(0, settings);

        kor2 = new Kor2ServiceHandler(
                new Kor2ServiceClient(
                        WebClient.builder(),
                        new Kor2ServiceProperties(
                                stub.baseUrl(Api.KOR2),
                                "loadtest"
                        )
                )
        );

        kakao = new KakaoMapServiceHandler(
                new KakaoMapServiceClient(
                        WebClient.builder(),
                        new KakaoMapServiceProperties(
                                stub.baseUrl(Api.KAKAO_MAP),
                                "loadtest"
                        )
                ),
                new KakaoMobilityServiceClient(
                        WebClient.builder(),
                        new KakaoMobilityServiceProperties(
                                stub.baseUrl(Api.KAKAO_MOBILITY),
                                "loadtest"
                        )
                ),
                new KakaoMapRouteHelper(),
                new KakaoMobilityRouteHelper(),
                new KakaoSearchKeywordSanitizer(),
                new KakaoPlaceSearchHelper()
        );

        food = new FoodNtrCpntHandler(
                new FoodNtrCpntClient(
                        WebClient.builder(),
                        new FoodNtrCpntProperties(
                                stub.baseUrl(Api.FOOD_NUTRITION),
                                "loadtest"
                        )
                ),
                new FoodNtrCpntHelper()
        );
    }

    private PlaceWithRouteResult findCafe(String keyword) {

        return kakao
                .findPlaceWithRoute(
                        keyword,
                        "스텁 관광지 01",
                        Transportation.TRANSIT,
                        List.of(),
                        "CE7",
                        "126.9780",
                        "37.5665"
                )
                .block();
    }

    private List<Kor2KeywordSearchResponse.Item> items(
            Kor2KeywordSearchResponse response
    ) {

        return response
                .response()
                .body()
                .items()
                .item();
    }

    private HttpResponse<String> get(String url) throws Exception {

        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(
                    HttpRequest
                            .newBuilder(URI.create(url))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofString()
            );
        }
    }
}
