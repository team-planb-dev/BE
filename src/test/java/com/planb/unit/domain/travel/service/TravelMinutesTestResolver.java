package com.planb.unit.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import com.planb.global.client.kakaoMapService.dto.response.KakaoRouteFailure;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.RouteAnchor;
import com.planb.domain.travel.service.TravelMinutesResolver;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TravelMinutesTestResolver {

    private final KakaoMapServiceHandler kakao = mock(KakaoMapServiceHandler.class);

    @Test
    @DisplayName("경로 조회 실패 + 500m 이하 구간은 도보 추정으로 채움")
    void fillsNearbyFailedRouteWithWalkEstimate() {

        stubRoute(null);

        CreatePlanAiResponse result = new TravelMinutesResolver(kakao, 4).fill(
                plan(
                        slot("경주 김유신묘", "129.1923886866", "35.8453311317", 15),
                        slot("금산재 칼국수", "129.1928553267", "35.8448311971", null)
                ),
                Transportation.CAR,
                RouteAnchor.from("경주역")
        );

        assertEquals(
                2,
                minutesAt(result, 1)
        );
    }

    @Test
    @DisplayName("경로 조회 실패 + 원거리 구간은 기존 누락 유지(명시적 실패 계약)")
    void keepsFarFailedRouteMissing() {

        stubRoute(null);

        CreatePlanAiResponse result = new TravelMinutesResolver(kakao, 4).fill(
                plan(
                        slot("A", "129.0", "35.0", 15),
                        slot("B", "129.0", "35.01", null)
                ),
                Transportation.CAR,
                RouteAnchor.from("경주역")
        );

        assertNull(minutesAt(result, 1));
    }

    @Test
    @DisplayName("다음 날짜 첫 장소는 이전 날짜 마지막 장소 기준으로 조회")
    void usesPreviousDayLastPlace() {

        stubRoute(25);

        CreatePlanAiResponse result = new TravelMinutesResolver(kakao, 4).fill(
                new CreatePlanAiResponse(List.of(
                        day(1, slot("첫날 마지막 장소", "129.16", "35.16", 10)),
                        day(2, slot("둘째날 첫 장소", "129.17", "35.17", null))
                )),
                Transportation.TRANSIT,
                RouteAnchor.from("부산역")
        );

        assertEquals(
                25,
                result
                        .planDays()
                        .get(1)
                        .schedules()
                        .getFirst()
                        .travelMinutes()
        );

        verify(kakao)
                .getRoute(
                        "첫날 마지막 장소",
                        "둘째날 첫 장소",
                        Transportation.TRANSIT,
                        "129.16",
                        "35.16",
                        "129.17",
                        "35.17"
                );
    }

    @Test
    @DisplayName("같은 구간은 요청 안에서 한 번만 조회")
    void memoizesSameSegment() {

        stubRoute(12);

        CreatePlanAiResponse.PlanScheduleDetail origin = slot("A", "129.0", "35.0", 5);
        CreatePlanAiResponse.PlanScheduleDetail destination = slot("B", "129.1", "35.1", 0);

        new TravelMinutesResolver(kakao, 4).fill(
                new CreatePlanAiResponse(List.of(
                        day(1, origin, destination),
                        day(2, origin, destination)
                )),
                Transportation.CAR,
                RouteAnchor.from("출발")
        );

        verify(kakao, times(1))
                .getRoute(
                        "A",
                        "B",
                        Transportation.CAR,
                        "129.0",
                        "35.0",
                        "129.1",
                        "35.1"
                );
    }

    @Test
    @DisplayName("동시 경로 조회 수는 설정 상한을 넘지 않음")
    void limitsConcurrentLookups() {

        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();

        when(kakao.getRoute(
                anyString(),
                anyString(),
                any(Transportation.class),
                any(),
                any(),
                any(),
                any()
        ))
                .thenAnswer(invocation -> Mono
                        .defer(() -> {
                            maxInFlight.accumulateAndGet(
                                    inFlight.incrementAndGet(),
                                    Math::max
                            );

                            return Mono
                                    .delay(Duration.ofMillis(50))
                                    .map(ignored -> new KakaoRouteResult(
                                            null,
                                            null,
                                            null,
                                            10
                                    ))
                                    // 완료 신호 전에 감소시켜 flatMap의 다음 구독과 겹쳐 세지 않음
                                    .doOnNext(ignored -> inFlight.decrementAndGet());
                        }));

        new TravelMinutesResolver(kakao, 2).fill(
                plan(
                        slot("P0", "129.00", "35.00", 5),
                        slot("P1", "129.01", "35.01", null),
                        slot("P2", "129.02", "35.02", null),
                        slot("P3", "129.03", "35.03", null),
                        slot("P4", "129.04", "35.04", null),
                        slot("P5", "129.05", "35.05", null)
                ),
                Transportation.CAR,
                RouteAnchor.from("출발")
        );

        assertTrue(
                maxInFlight.get() <= 2,
                "max in flight " + maxInFlight.get()
        );
        assertTrue(
                maxInFlight.get() >= 1,
                "max in flight " + maxInFlight.get()
        );
    }

    @Test
    @DisplayName("단건 조회도 실패 시 근거리 도보 추정 적용")
    void singleLookupUsesWalkEstimate() {

        stubRoute(null);

        assertEquals(
                Optional.of(2),
                new TravelMinutesResolver(kakao, 4).minutes(
                        "경주 김유신묘",
                        slot("경주 김유신묘", "129.1923886866", "35.8453311317", 15),
                        slot("금산재 칼국수", "129.1928553267", "35.8448311971", null),
                        Transportation.CAR
                )
        );
    }

    @Test
    @DisplayName("Tool 비교 결과와 별개로 Java 최종 경로의 현재 이동시간 적용")
    void usesFinalRouteInsteadOfToolComparison() {

        when(kakao.getRoute(
                "A",
                "B",
                Transportation.TRANSIT,
                "129.0",
                "35.0",
                "129.1",
                "35.1"
        ))
                .thenReturn(
                        Mono.just(new KakaoRouteResult(
                                "A",
                                "B",
                                10000,
                                5
                        )),
                        Mono.just(new KakaoRouteResult(
                                "A",
                                "B",
                                10000,
                                35
                        ))
                );

        KakaoRouteResult comparison = comparisonTool()
                .getRoute(
                        "kakao:A",
                        "kakao:B",
                        Transportation.TRANSIT
                );

        CreatePlanAiResponse result = new TravelMinutesResolver(kakao, 4)
                .fill(
                        plan(slot(
                                "B",
                                "129.1",
                                "35.1",
                                null
                        )),
                        Transportation.TRANSIT,
                        new RouteAnchor(
                                "A",
                                slot(
                                        "A",
                                        "129.0",
                                        "35.0",
                                        5
                                )
                        )
                );

        assertEquals(5, comparison.travelMinutes());
        assertEquals(35, minutesAt(result, 0));
        verify(kakao, times(2))
                .getRoute(
                        "A",
                        "B",
                        Transportation.TRANSIT,
                        "129.0",
                        "35.0",
                        "129.1",
                        "35.1"
                );
    }

    @Test
    @DisplayName("Tool 비교 성공 후 최종 경로 실패 시 이전 이동시간 대체 금지")
    void preservesFinalFailureAfterSuccessfulToolComparison() {

        KakaoRouteFailure failure = new KakaoRouteFailure(
                KakaoRouteFailure.Stage.ROUTE,
                KakaoRouteFailure.Kind.HTTP_ERROR,
                400,
                "-10",
                "fixture quota failure",
                null,
                "129.0",
                "35.0",
                "129.1",
                "35.1"
        );

        when(kakao.getRoute(
                "A",
                "B",
                Transportation.TRANSIT,
                "129.0",
                "35.0",
                "129.1",
                "35.1"
        ))
                .thenReturn(
                        Mono.just(new KakaoRouteResult(
                                "A",
                                "B",
                                10000,
                                5
                        )),
                        Mono.just(new KakaoRouteResult(
                                "A",
                                "B",
                                null,
                                null,
                                failure
                        ))
                );

        assertEquals(
                5,
                comparisonTool()
                        .getRoute(
                                "kakao:A",
                                "kakao:B",
                                Transportation.TRANSIT
                        )
                        .travelMinutes()
        );

        TravelMinutesResolver.Filled result = new TravelMinutesResolver(kakao, 4)
                .fillReporting(
                        plan(slot(
                                "B",
                                "129.1",
                                "35.1",
                                null
                        )),
                        Transportation.TRANSIT,
                        new RouteAnchor(
                                "A",
                                slot(
                                        "A",
                                        "129.0",
                                        "35.0",
                                        5
                                )
                        )
                );

        assertNull(minutesAt(result.response(), 0));
        assertEquals(1, result.missing().size());
        assertEquals(failure, result.missing().getFirst().failure());
    }

    private PlanTourismTool comparisonTool() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new PlaceWithRouteResult(
                true,
                "A",
                "주소 A",
                "129.0",
                "35.0",
                null,
                "kakao:A",
                null,
                null
        ));
        candidates.record(new PlaceWithRouteResult(
                true,
                "B",
                "주소 B",
                "129.1",
                "35.1",
                null,
                "kakao:B",
                null,
                null
        ));

        return new PlanTourismTool(
                new TourismTool(
                        mock(Kor2ServiceHandler.class),
                        kakao,
                        mock(NutritionService.class),
                        mock(NutritionEvaluationCollector.class)
                ),
                candidates
        );
    }

    private void stubRoute(Integer minutes) {

        when(kakao.getRoute(
                anyString(),
                anyString(),
                any(Transportation.class),
                any(),
                any(),
                any(),
                any()
        ))
                .thenReturn(Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        minutes
                )));
    }

    private static Integer minutesAt(
            CreatePlanAiResponse response,
            int index
    ) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .get(index)
                .travelMinutes();
    }

    private static CreatePlanAiResponse plan(CreatePlanAiResponse.PlanScheduleDetail... slots) {

        return new CreatePlanAiResponse(List.of(day(1, slots)));
    }

    private static CreatePlanAiResponse.PlanDayDetail day(
            int dayNumber,
            CreatePlanAiResponse.PlanScheduleDetail... slots
    ) {

        return new CreatePlanAiResponse.PlanDayDetail(
                dayNumber,
                LocalDate
                        .of(2030, 1, 1)
                        .plusDays(dayNumber - 1),
                List.of(slots)
        );
    }

    private static CreatePlanAiResponse.PlanScheduleDetail slot(
            String name,
            String longitude,
            String latitude,
            Integer travelMinutes
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                name,
                "주소",
                longitude,
                latitude,
                null,
                null,
                60,
                travelMinutes,
                Set.of(),
                null,
                null,
                "tour:" + name
        );
    }
}
