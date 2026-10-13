package com.planb.unit.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.RouteAnchor;
import com.planb.domain.travel.service.TravelMinutesResolver;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.properties.KakaoMapServiceProperties;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoSearchKeywordSanitizer;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.properties.KakaoMobilityServiceProperties;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * P2-4D 실측 이동시간 거부의 공급자 응답 재현
 */
class TravelMinutesTestRouteResolver {

    // 임의 4xx 예시 본문(-10 형태), 처리 결과는 본문과 무관
    private static final String REJECTED_BODY =
            "{\"code\":-10,\"msg\":\"API limit has been exceeded.\"}";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    private final AtomicInteger transitCalls = new AtomicInteger();

    // 지정 출발 경도 요청만 400 응답
    private volatile String rejectedStartX;

    private HttpServer server;

    @AfterEach
    void stop() {

        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    @DisplayName("대중교통 경로 정상 응답 시 실제 C1 1일차 이동시간 전 구간 채움")
    void fillsTransitMinutesWhenProviderResponds() throws IOException {

        CreatePlanAiResponse result = resolver(false).fill(
                seoulFirstDay(),
                Transportation.TRANSIT,
                RouteAnchor.from("서울역")
        );

        assertTrue(travelMinutes(result)
                .stream()
                .allMatch(Objects::nonNull));
    }

    @Test
    @DisplayName("대중교통 경로 400 응답 시 원거리 구간 이동시간 누락 유지")
    void keepsTransitMinutesMissingWhenProviderRejects() throws IOException {

        CreatePlanAiResponse result = resolver(true).fill(
                seoulFirstDay(),
                Transportation.TRANSIT,
                RouteAnchor.from("서울역")
        );

        // 앞 6구간 누락, 500m 이하 덕수궁 대한문→고려삼계탕(396m)만 도보 추정 8분
        assertEquals(
                Arrays.asList(
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        8
                ),
                travelMinutes(result)
        );
    }

    @Test
    @DisplayName("대중교통 경로 400 응답과 무관한 자동차 경로의 이동시간 채움")
    void fillsCarMinutesWhenOnlyTransitRejects() throws IOException {

        CreatePlanAiResponse result = resolver(true).fill(
                seoulFirstDay(),
                Transportation.CAR,
                RouteAnchor.from("서울역")
        );

        assertEquals(
                List.of(),
                travelMinutes(result)
                        .stream()
                        .filter(Objects::isNull)
                        .toList()
        );
    }

    @Test
    @DisplayName("일부 구간 실패의 실패 구간·공급자 사유 보존과 정상 구간 분리")
    void reportsFailedSegmentsOnly() throws IOException {

        TravelMinutesResolver.Filled filled = resolver(true).fillReporting(
                seoulFirstDay(),
                Transportation.TRANSIT,
                RouteAnchor.from("서울역")
        );

        assertEquals(
                List.of(
                        "서울역→동우설렁탕 본점",
                        "동우설렁탕 본점→경복궁",
                        "경복궁→결",
                        "결→가야밀냉면해물칼국수",
                        "가야밀냉면해물칼국수→경희궁 흥화문",
                        "경희궁 흥화문→덕수궁 대한문"
                ),
                filled
                        .missing()
                        .stream()
                        .map(segment -> segment.origin() + "→" + segment.destination())
                        .toList()
        );

        assertTrue(filled
                .missing()
                .stream()
                .allMatch(segment -> segment
                        .failure()
                        .httpStatus() == 400));
    }

    @Test
    @DisplayName("장소 아닌 이름 있는 복약 슬롯의 조회 실패는 누락 보고 제외")
    void excludesNonPlaceSlotsFromMissing() throws IOException {

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>(seoulFirstDay()
                .planDays()
                .getFirst()
                .schedules());

        schedules.add(
                2,
                slot(
                        ScheduleType.CHECK_IN,
                        CourseType.MEDICATION,
                        "복약 알림",
                        "127.1",
                        "37.7",
                        11,
                        20
                )
        );

        TravelMinutesResolver.Filled filled = resolver(true).fillReporting(
                new CreatePlanAiResponse(List.of(new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.of(
                                2026,
                                10,
                                19
                        ),
                        schedules
                ))),
                Transportation.TRANSIT,
                RouteAnchor.from("서울역")
        );

        assertTrue(filled
                .missing()
                .stream()
                .noneMatch(segment -> "복약 알림".equals(segment.destination())));
    }

    @Test
    @DisplayName("공급자 호출과 논리 구간·도보 추정 지표 분리")
    void separatesProviderAndSegmentMetrics() throws IOException {

        resolver(true).fill(
                seoulFirstDay(),
                Transportation.TRANSIT,
                RouteAnchor.from("서울역")
        );

        assertEquals(
                7.0,
                registry
                        .counter(
                                "planb.external.kakao.route",
                                "transportation",
                                "TRANSIT",
                                "outcome",
                                "route_http_400"
                        )
                        .count()
        );
        assertEquals(
                List.of(
                        0.0,
                        1.0,
                        6.0
                ),
                List.of(
                        segmentCount("resolved"),
                        segmentCount("walk_estimate"),
                        segmentCount("missing")
                )
        );
    }

    @Test
    @DisplayName("같은 구간 요청 범위 1회 조회 유지")
    void memoizesProviderCalls() throws IOException {

        CreatePlanAiResponse.PlanDayDetail day = seoulFirstDay()
                .planDays()
                .getFirst();

        resolver(false).fill(
                new CreatePlanAiResponse(List.of(
                        day,
                        new CreatePlanAiResponse.PlanDayDetail(
                                2,
                                day
                                        .date()
                                        .plusDays(1),
                                day.schedules()
                        )
                )),
                Transportation.TRANSIT,
                RouteAnchor.from("서울역")
        );

        // 1일차 7구간 + 2일차 첫 구간(고려삼계탕→동우설렁탕 본점), 나머지 6구간 재사용
        assertEquals(
                8,
                transitCalls.get()
        );
    }

    @Test
    @DisplayName("동시 요청 간 실패 구간 정보 비혼합")
    void isolatesConcurrentRequests() throws Exception {

        TravelMinutesResolver resolver = resolver(false);
        rejectedStartX = "126.9767218661";

        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<TravelMinutesResolver.Filled> seoul = executor.submit(() -> resolver.fillReporting(
                    seoulFirstDay(),
                    Transportation.TRANSIT,
                    RouteAnchor.from("서울역")
            ));
            Future<TravelMinutesResolver.Filled> other = executor.submit(() -> resolver.fillReporting(
                    otherDay(),
                    Transportation.TRANSIT,
                    RouteAnchor.from("부산역")
            ));

            assertEquals(
                    List.of("경복궁→결"),
                    seoul
                            .get()
                            .missing()
                            .stream()
                            .map(segment -> segment.origin() + "→" + segment.destination())
                            .toList()
            );
            assertEquals(
                    List.of(),
                    other
                            .get()
                            .missing()
            );
        } finally {
            executor.shutdownNow();
        }
    }

    private TravelMinutesResolver resolver(boolean rejectTransit) throws IOException {

        server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        0
                ),
                0
        );

        server.createContext(
                "/v2/routing/publictraffic",
                exchange -> {
                    transitCalls.incrementAndGet();

                    boolean rejected = rejectTransit || rejectedStartX != null && exchange
                            .getRequestURI()
                            .getQuery()
                            .contains("start_x=" + rejectedStartX);

                    respond(
                            exchange,
                            rejected ? 400 : 200,
                            rejected
                                    ? REJECTED_BODY
                                    : fixture("loadtest/kakao/public-traffic-route.json")
                    );
                }
        );

        server.createContext(
                "/v2/local/search/keyword.json",
                exchange -> respond(
                        exchange,
                        200,
                        fixture("loadtest/kakao/place-search.json")
                )
        );

        server.createContext(
                "/v1/directions",
                exchange -> respond(
                        exchange,
                        200,
                        fixture("loadtest/kakao/car-route.json")
                )
        );

        server.start();

        String baseUrl = "http://127.0.0.1:" + server
                .getAddress()
                .getPort();

        return new TravelMinutesResolver(
                new KakaoMapServiceHandler(
                        new KakaoMapServiceClient(
                                WebClient.builder(),
                                new KakaoMapServiceProperties(
                                        baseUrl,
                                        "stub"
                                )
                        ),
                        new KakaoMobilityServiceClient(
                                WebClient.builder(),
                                new KakaoMobilityServiceProperties(
                                        baseUrl,
                                        "stub"
                                )
                        ),
                        new KakaoMapRouteHelper(),
                        new KakaoMobilityRouteHelper(),
                        new KakaoSearchKeywordSanitizer(),
                        new KakaoPlaceSearchHelper(),
                        registry
                ),
                4,
                registry
        );
    }

    private double segmentCount(String result) {

        return registry
                .counter(
                        "planb.travel.route.segment",
                        "transportation",
                        "TRANSIT",
                        "result",
                        result
                )
                .count();
    }

    // 다른 출발 좌표의 단일 날짜
    private static CreatePlanAiResponse otherDay() {

        return new CreatePlanAiResponse(
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                LocalDate.of(
                                        2026,
                                        10,
                                        19
                                ),
                                List.of(
                                        slot(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "해운대해수욕장",
                                                "129.1586",
                                                "35.1587",
                                                10,
                                                0
                                        ),
                                        slot(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "동백섬",
                                                "129.1520",
                                                "35.1530",
                                                12,
                                                0
                                        )
                                )
                        )
                )
        );
    }

    private static void respond(
            HttpExchange exchange,
            int status,
            String body
    ) throws IOException {

        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange
                .getResponseHeaders()
                .set(
                        "Content-Type",
                        "application/json"
                );
        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    private static String fixture(String path) throws IOException {

        try (InputStream stream = TravelMinutesTestRouteResolver.class
                .getClassLoader()
                .getResourceAsStream(path)) {
            return new String(
                    Objects
                            .requireNonNull(stream)
                            .readAllBytes(),
                    StandardCharsets.UTF_8
            );
        }
    }

    private static List<Integer> travelMinutes(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .map(CreatePlanAiResponse.PlanScheduleDetail::travelMinutes)
                .toList();
    }

    // 실측 C1 서울 1회차 1일차 장소·좌표, AI 이동시간 null
    private static CreatePlanAiResponse seoulFirstDay() {

        return new CreatePlanAiResponse(
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                LocalDate.of(
                                        2026,
                                        10,
                                        19
                                ),
                                List.of(
                                        slot(
                                                ScheduleType.BREAKFAST,
                                                CourseType.LOCAL_FOOD,
                                                "동우설렁탕 본점",
                                                "127.0290796795",
                                                "37.5884526077",
                                                8,
                                                0
                                        ),
                                        slot(
                                                ScheduleType.ACTIVITY,
                                                CourseType.MUST_HAVE,
                                                "경복궁",
                                                "126.9767218661",
                                                "37.5760307000",
                                                9,
                                                30
                                        ),
                                        slot(
                                                ScheduleType.ACTIVITY,
                                                CourseType.CAFE_REST,
                                                "결",
                                                "126.9835749364385",
                                                "37.57188858492207",
                                                11,
                                                30
                                        ),
                                        slot(
                                                ScheduleType.LUNCH,
                                                CourseType.RESTAURANT,
                                                "가야밀냉면해물칼국수",
                                                "126.9485378950",
                                                "37.6555794652",
                                                12,
                                                0
                                        ),
                                        slot(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "경희궁 흥화문",
                                                "126.9707771853",
                                                "37.5698981615",
                                                14,
                                                20
                                        ),
                                        slot(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "덕수궁 대한문",
                                                "126.9765906796",
                                                "37.5651071556",
                                                15,
                                                40
                                        ),
                                        slot(
                                                ScheduleType.DINNER,
                                                CourseType.RESTAURANT,
                                                "고려삼계탕",
                                                "126.9729271831",
                                                "37.5630517738",
                                                18,
                                                0
                                        )
                                )
                        )
                )
        );
    }

    private static CreatePlanAiResponse.PlanScheduleDetail slot(
            ScheduleType scheduleType,
            CourseType courseType,
            String name,
            String longitude,
            String latitude,
            int hour,
            int minute
    ) {

        LocalTime start = LocalTime.of(
                hour,
                minute
        );

        return new CreatePlanAiResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                start,
                start.plusMinutes(50),
                name,
                "서울",
                longitude,
                latitude,
                null,
                null,
                50,
                null,
                Set.of(),
                null,
                null,
                null
        );
    }
}
