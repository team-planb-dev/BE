package com.planb.unit.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanDayDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.domain.travel.helper.PlanEditValidationHelper;
import org.junit.jupiter.api.BeforeEach;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.*;
import com.planb.domain.travel.helper.PlanPlaceHelper;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.domain.travel.service.PlanService;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.config.exception.domain.BaseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PlanPlaceValidationTest {
    private final TravelRecommendHandler handler = mock(TravelRecommendHandler.class);

    private final KakaoMapServiceHandler kakao = mock(KakaoMapServiceHandler.class);

    private final PlanPlaceHelper helper = new PlanPlaceHelper(kakao);

    private final NutritionEvaluationCollector nutrition = new NutritionEvaluationCollector();

    private final PlanService service = new PlanService(
            mock(PlanRepository.class),
            helper,
            new PlanEditValidationHelper(),
            handler,
            kakao,
            nutrition
    );

    private final LocalDate date = LocalDate.of(2026, 9, 10);

    private final TravelPlanContext travel = new TravelPlanContext(new CreateTravelRequest(
            "부산",
            "부산",
            "해운대구",
            date,
            DateType.ONE_NIGHT_TWO_DAYS,
            Transportation.CAR,
            "부산역",
            List.of(),
            TravelStyle.MATCH_MEAL_TIME,
            TravelTheme.TASTE,
            List.of(),
            List.of()
    ), List.of());

    @BeforeEach
    void ordinaryEditScope() {

        org.mockito.Mockito
                .lenient()
                .when(kakao.getRoute(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> kakao.getRoute(
                        invocation.<String>getArgument(0),
                        invocation.<String>getArgument(1),
                        invocation.<com.planb.domain.travel.entity.constant.Transportation>getArgument(2)));


        when(
                handler
                        .classifyEditScope(any()))
                .thenReturn(
                        new PlanEditScope(List
                                .of()));
    }

    @Test
    @DisplayName("음식점 원본을 관광지로 선택한 응답 거부")
    void restaurantSourceCannotBecomeAttraction() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tour("2784321", "39", "개금밀면"));

        PlanPlaceHelper.Validation result = helper
                .validate(
                        slot(
                                "tour:2784321",
                                "개금밀면",
                                9),
                        candidates,
                        Set.of(),
                        Set.of());

        assertFalse(result.valid());

        assertTrue(
                result
                        .reason()
                        .contains("39"));

        assertTrue(
                result
                        .reason()
                        .contains("ATTRACTION"));
    }

    @Test
    @DisplayName("지역 관광지 후보에 없는 candidateId를 반환한 일정 거부")
    void rejectsAttractionOutsideRegionalCandidates() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tour("1", "12", "경복궁"));

        PlanPlaceHelper.Validation result = helper
                .validate(
                        slot(
                                "tour:missing",
                                "창덕궁",
                                9
                        ),
                        candidates,
                        Set.of(),
                        Set.of()
                );

        assertFalse(result.valid());
    }

    @Test
    @DisplayName("일차와 날짜 식별자가 누락된 일정 거부")
    void rejectsMissingPlanDayIdentity() {

        when(
                handler
                        .createPlanByAi(
                                any(),
                                any()
                        )
        ).thenAnswer(
                invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(
                            tour(
                                    "1",
                                    "12",
                                    "해운대"
                            )
                    );

                    return new CreatePlanAiResponse(
                            List.of(
                                    new PlanDayDetail(
                                            null,
                                            null,
                                            List.of(
                                                    slot(
                                                            "tour:1",
                                                            "해운대",
                                                            9
                                                    )
                                            )
                                    )
                            )
                    );
                }
        );

        assertThrows(
                BaseException.class,
                () -> service.makePlanByAi(travel)
        );
    }

    @Test
    @DisplayName("경로 조회 후에도 이동시간이 없는 장소 일정 거부")
    void rejectsMissingFinalTravelMinutes() {

        PlanScheduleDetail missingTravelMinutes =
                new PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 0),
                        "해운대",
                        "부산",
                        "129.1",
                        "35.1",
                        "원본 사진",
                        "원본 썸네일",
                        60,
                        null,
                        Set.of(),
                        null,
                        null,
                        "tour:1"
                );

        when(
                handler
                        .createPlanByAi(
                                any(),
                                any()
                        )
        ).thenAnswer(
                invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(
                            tour(
                                    "1",
                                    "12",
                                    "해운대"
                            )
                    );

                    return new CreatePlanAiResponse(
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(missingTravelMinutes)
                                    )
                            )
                    );
                }
        );

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null
                        )
                )
        );

        assertThrows(
                BaseException.class,
                () -> service.makePlanByAi(travel)
        );
    }

    @Test
    @DisplayName("이동 슬롯에 포함된 복약 상세 거부")
    void rejectsMedicationPayloadOnTransportation() {

        PlanScheduleDetail transportation =
                new PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.TRANSPORTATION,
                        LocalTime.of(9, 0),
                        LocalTime.of(9, 30),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        30,
                        null,
                        Set.of(),
                        new CreatePlanAiResponse.MedicationSchedule(
                                30,
                                "잘못된 복약"
                        ),
                        null,
                        null
                );

        assertFalse(
                helper
                        .validate(
                                transportation,
                                new PlaceCandidateContext(),
                                Set.of(),
                                Set.of()
                        )
                        .valid()
        );
    }

    @Test
    @DisplayName("검색 원본으로 장소 정보 확정 및 미등록 후보 거부")
    void canonicalizesAiWrittenPlaceFieldsAndRejectsUnknownId() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates
                .record(tour("1", "12", "해운대"));

        PlanPlaceHelper.Validation result =
                helper
                        .validate(
                                slot(
                                        "tour:1",
                                        "가짜 이름",
                                        9),
                                candidates,
                                Set.of(),
                                Set.of());

        assertTrue(result.valid());

        assertEquals(
                "해운대",
                result
                        .schedule()
                        .locationName());

        assertEquals(
                "원본 사진",
                result
                        .schedule()
                        .imageUrl());

        assertFalse(
                helper
                        .validate(slot(
                                "tour:2",
                                "해운대",
                                9),
                                candidates,
                                Set.of(),
                                Set.of())
                        .valid());
    }

    @Test
    @DisplayName("장소 시간 간격과 체류시간이 다르면 거부")
    void rejectsPlaceDurationDifferentFromStayMinutes() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates
                .record(tour(
                        "1",
                        "12",
                        "해운대"));

        PlanScheduleDetail inconsistent = new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(9, 0),
                LocalTime.of(11, 50),
                "해운대",
                "부산",
                "129.1",
                "35.1",
                "원본 사진",
                "원본 썸네일",
                60,
                10,
                Set.of(),
                null,
                null,
                "tour:1");

        assertFalse(helper
                .validate(
                        inconsistent,
                        candidates,
                        Set.of(),
                        Set.of())
                .valid());
    }

    @Test
    @DisplayName("카카오 원본 유형과 일정 유형 조합의 독립 검증")
    void kakaoExistenceAloneCannotCertifyAttractionAndCombinationIsIndependent() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new PlaceWithRouteResult(
                true,
                "식당",
                "부산",
                "129.1",
                "35.1",
                10,
                "kakao:1",
                "FD6",
                "음식점"
        ));

        assertFalse(
                helper
                        .validate(
                                slot(
                                        "kakao:1",
                                        "식당",
                                        9),
                                candidates,
                                Set.of(),
                                Set.of())
                        .valid());

        candidates
                .record(
                        tour(
                                "1",
                                "12",
                                "해운대"));

        PlanScheduleDetail wrongCombination = new PlanScheduleDetail(
                ScheduleType
                        .LUNCH,
                CourseType
                        .ATTRACTION,
                LocalTime
                        .of(
                                9,
                                0),
                LocalTime
                        .of(
                                10,
                                0),
                "해운대",
                "부산",
                "129.1",
                "35.1",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                null,
                "tour:1"
        );

        assertTrue(
                helper
                        .validate(
                                wrongCombination,
                                candidates,
                                Set.of(),
                                Set.of())
                        .reason()
                        .contains("조합"));
    }

    @Test
    @DisplayName("카페 유형 제한 및 음식점 메뉴 중복 거부")
    void cafeSourceIsAllowedOnlyForCafeAndMenuDuplicatesAreRejected() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new PlaceWithRouteResult(
                true,
                "카페",
                "부산",
                "129.1",
                "35.1",
                10,
                "kakao:cafe",
                "CE7",
                "음식점 > 카페"
        ));

        PlanScheduleDetail cafe = new PlanScheduleDetail(
                ScheduleType
                        .ACTIVITY,
                CourseType
                        .CAFE_REST,
                LocalTime
                        .of(9, 0),
                LocalTime
                        .of(10, 0),
                "가짜 이름",
                "가짜 주소",
                null,
                null,
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                null,
                "kakao:cafe"
        );

        assertEquals(
                "카페",
                helper
                        .validate(
                                cafe,
                                candidates,
                                Set.of(),
                                Set.of())
                        .schedule()
                        .locationName());

        assertFalse(
                helper
                        .validate(
                                slot(
                                        "kakao:cafe",
                                        "카페",
                                        9),
                                candidates,
                                Set.of(),
                                Set.of())
                        .valid());

        candidates
                .record(
                        tour(
                                "2784321",
                                "39",
                                "개금밀면"));

        CreatePlanAiResponse.RestaurantDetail menu = new CreatePlanAiResponse.RestaurantDetail(
                "밀면",
                null,
                null,
                null,
                null,
                "가짜 주소",
                null,
                null,
                null
        );

        PlanScheduleDetail restaurant = new PlanScheduleDetail(
                ScheduleType
                        .LUNCH,
                CourseType
                        .RESTAURANT,
                LocalTime
                        .of(
                                12,
                                0),
                LocalTime
                        .of(
                                13,
                                0),
                "밀면",
                null,
                null,
                null,
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                menu,
                "tour:2784321"
        );

        assertEquals(
                "부산",
                helper
                        .validate(
                                restaurant,
                                candidates,
                                Set.of(),
                                Set.of())
                .schedule()
                        .restaurantDetail()
                        .address());

        assertFalse(
                helper
                        .validate(
                                restaurant,
                                candidates,
                                Set.of(),
                                Set.of("밀면"))
                        .valid());
    }

    @Test
    @DisplayName("호출별 후보 격리 및 재시도 이전 후보 제거")
    void candidateContextsAreIsolatedAndToolResetDropsPreviousAttempt() {

        TourismTool rawTool = mock(TourismTool.class);

        Kor2KeywordSearchResponse response =
                new Kor2KeywordSearchResponse(
                        new Kor2KeywordSearchResponse.Response(
                                null,
                                new Kor2KeywordSearchResponse
                                        .Body(
                                                new Kor2KeywordSearchResponse
                                                        .Items(
                                                                List
                                                                        .of(
                                                                                tour(
                                                                                        "2784321",
                                                                                        "39",
                                                                                        "개금밀면"))),
                        1,
                        1,
                        1
                )));

        when(
                rawTool
                        .searchAttractionsByRegion(
                                "부산",
                                "해운대구"
                        ))
                .thenReturn(response);

        PlaceCandidateContext first = new PlaceCandidateContext();

        PlaceCandidateContext second = new PlaceCandidateContext();

        PlanTourismTool scoped = new PlanTourismTool(rawTool, first);

        assertEquals(
                "39",
                scoped
                        .searchAttractionsByRegion(
                                "부산",
                                "해운대구"
                        )
                        .getFirst()
                        .type());

        assertNotNull(
                first
                        .find("tour:2784321"));

        assertNull(
                second
                        .find("tour:2784321"));

        scoped
                .resetCandidates();

        assertNull(
                first
                        .find("tour:2784321"));
    }

    @Test
    @DisplayName("잘못된 AI 복약 시간은 Java가 재생성하고 장소 재선택하지 않음")
    void invalidMedicationTimeIsRegeneratedBeforePlaceValidation() {

        TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule =
                new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                        RelatedMeal.LUNCH,
                        MealTiming.AFTER_MEAL,
                        30
                );

        TravelHealthContext healthContext =
                new TravelHealthContext(
                        "테스트 여행자",
                        DiseaseType.DIABETES,
                        WalkType.MODERATE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),
                        List.of(),
                        List.of(
                                new TravelHealthContext.MedicationInfoContext(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        null,
                                        Set.of(rule)
                                )
                        )
                );

        TravelPlanContext context =
                new TravelPlanContext(
                        travel.createTravelRequest(),
                        List.of(healthContext)
                );

        PlanScheduleDetail invalidMedication =
                new PlanScheduleDetail(
                        ScheduleType.CHECK_IN,
                        CourseType.MEDICATION,
                        LocalTime.of(12, 30),
                        LocalTime.of(12, 30),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        Set.of(),
                        new CreatePlanAiResponse.MedicationSchedule(
                                30,
                                "테스트 복약 점심 식후 30분"
                        ),
                        null,
                        null
                );

        stubCreate(
                new CreatePlanAiResponse(
                        List.of(
                                new PlanDayDetail(
                                        1,
                                        date,
                                        List.of(
                                                slot(
                                                        "tour:1",
                                                        "해운대",
                                                        9
                                                ),
                                                slot(
                                                        "tour:3",
                                                        "이기대",
                                                        11
                                                ),
                                                slot(
                                                        "tour:4",
                                                        "오죽헌",
                                                        13
                                                ),
                                                invalidMedication
                                        )
                                )
                        )
                )
        );

        PlanScheduleDetail medication = service
                .makePlanByAi(context)
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                .findFirst()
                .orElseThrow();

        assertEquals(
                LocalTime.of(12, 30),
                medication.startTime()
        );

        assertEquals(
                LocalTime.of(12, 40),
                medication.endTime()
        );

        assertEquals(
                10,
                medication.stayMinutes()
        );

        verify(
                handler,
                never()
        ).reselectPlace(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("편집 응답의 잘못된 AI 복약 시간은 검증 전에 Java가 재생성")
    void editRegeneratesMedicationBeforeValidation() {

        TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule =
                new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                        RelatedMeal.LUNCH,
                        MealTiming.AFTER_MEAL,
                        30
                );

        TravelHealthContext healthContext =
                new TravelHealthContext(
                        "테스트 여행자",
                        DiseaseType.DIABETES,
                        WalkType.MODERATE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),
                        List.of(),
                        List.of(
                                new TravelHealthContext.MedicationInfoContext(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        null,
                                        Set.of(rule)
                                )
                        )
                );

        PlanScheduleDetail place =
                slot(
                        "tour:1",
                        "해운대",
                        9
                );

        PlanScheduleDetail invalidMedication =
                new PlanScheduleDetail(
                        ScheduleType.CHECK_IN,
                        CourseType.MEDICATION,
                        LocalTime.of(7, 0),
                        LocalTime.of(7, 0),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        0,
                        null,
                        Set.of(),
                        null,
                        null,
                        null
                );

        when(
                handler
                        .editPlanByAi(
                                any(),
                                any()
                        )
        ).thenAnswer(
                invocation -> {
                    recordCandidates(
                            invocation
                                    .getArgument(1)
                    );

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    place,
                                                    slot(
                                                        "tour:3",
                                                        "이기대",
                                                        11
                                                ),
                                                    slot(
                                                        "tour:4",
                                                        "오죽헌",
                                                        13
                                                ),
                                                    invalidMedication
                                            )
                                    )
                            ),
                            List.of(),
                            true
                    );
                }
        );

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                10
                        )
                )
        );

        EditPlanAiResponse result =
                service.makeEditPlanByAi(
                        new PlanEditContext(
                                travel.createTravelRequest(),
                                List.of(healthContext),
                                existing(place),
                                "첫날 수정"
                        )
                );

        PlanScheduleDetail medication =
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                LocalTime.of(12, 30),
                medication.startTime()
        );

        assertEquals(
                LocalTime.of(12, 40),
                medication.endTime()
        );

        verify(
                handler,
                never()
        ).reselectPlace(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("경로상 식사가 허용 범위를 넘으면 앞 일정을 당기고 확정 식사시간으로 복약 일정을 계산")
    void usesFinalMealTimeAfterRouteNormalization() {

        TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule =
                new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                        RelatedMeal.LUNCH,
                        MealTiming.AFTER_MEAL,
                        30
                );

        TravelHealthContext healthContext =
                new TravelHealthContext(
                        "테스트 여행자",
                        DiseaseType.DIABETES,
                        WalkType.MODERATE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),
                        List.of(),
                        List.of(
                                new TravelHealthContext.MedicationInfoContext(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        null,
                                        Set.of(rule)
                                )
                        )
                );

        TravelPlanContext context =
                new TravelPlanContext(
                        travel.createTravelRequest(),
                        List.of(healthContext)
                );

        PlanScheduleDetail attraction =
                slot(
                        "tour:1",
                        "해운대",
                        11
                );

        PlanScheduleDetail lunch =
                new PlanScheduleDetail(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0),
                        "개금밀면",
                        "부산",
                        "129.1",
                        "35.1",
                        "원본 사진",
                        "원본 썸네일",
                        60,
                        null,
                        Set.of(),
                        null,
                        new CreatePlanAiResponse.RestaurantDetail(
                                "밀면",
                                null,
                                null,
                                null,
                                null,
                                "부산",
                                "129.1",
                                "35.1",
                                "원본 사진"
                        ),
                        "tour:2784321"
                );

        when(
                handler
                        .createPlanByAi(
                                any(),
                                any()
                        )
        ).thenAnswer(
                invocation -> {
                    recordCandidates(
                            invocation.getArgument(1)
                    );

                    return new CreatePlanAiResponse(
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    attraction,
                                                    lunch,
                                                    slot(
                                                        "tour:3",
                                                        "이기대",
                                                        15
                                                ),
                                                    slot(
                                                        "tour:4",
                                                        "오죽헌",
                                                        17
                                                )
                                            )
                                    )
                            )
                    );
                }
        );

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                31
                        )
                )
        );

        List<PlanScheduleDetail> schedules =
                service
                        .makePlanByAi(context)
                        .planDays()
                        .getFirst()
                        .schedules();

        PlanScheduleDetail normalizedLunch =
                schedules
                        .stream()
                        .filter(schedule -> schedule.scheduleType() == ScheduleType.LUNCH)
                        .findFirst()
                        .orElseThrow();

        PlanScheduleDetail medication =
                schedules
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow();

        PlanScheduleDetail normalizedAttraction = schedules.getFirst();

        assertEquals(
                LocalTime.of(10, 59),
                normalizedAttraction.startTime()
        );

        assertEquals(
                LocalTime.of(11, 59),
                normalizedAttraction.endTime()
        );

        assertEquals(
                LocalTime.of(12, 30),
                normalizedLunch.startTime()
        );

        assertEquals(
                LocalTime.of(13, 0),
                medication.startTime()
        );
    }

    @Test
    @DisplayName("장소 체류시간과 종료시간을 맞추고 다음 장소의 겹침을 보정")
    void normalizesPlaceDurationAndFollowingStartBeforeValidation() {

        PlanScheduleDetail lunch = new PlanScheduleDetail(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                LocalTime.of(14, 50),
                "교동쌈밥",
                "부산",
                "129.1",
                "35.1",
                "원본 사진",
                "원본 썸네일",
                60,
                0,
                Set.of(),
                null,
                new CreatePlanAiResponse.RestaurantDetail(
                        "쌈밥",
                        null,
                        null,
                        null,
                        null,
                        "부산",
                        "129.1",
                        "35.1",
                        "원본 사진"),
                "tour:2762860");

        PlanScheduleDetail cafe = new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.CAFE_REST,
                LocalTime.of(12, 44),
                LocalTime.of(13, 44),
                "커피플레이스",
                "부산",
                "129.2",
                "35.2",
                null,
                null,
                60,
                51,
                Set.of(),
                null,
                null,
                "kakao:cafe");

        CreatePlanAiResponse response = new CreatePlanAiResponse(
                List.of(
                        new PlanDayDetail(
                                1,
                                date,
                                List.of(
                                        lunch,
                                        cafe))));

        when(handler
                .createPlanByAi(
                        any(),
                        any()))
                .thenAnswer(
                        invocation -> {
                            PlaceCandidateContext candidates = invocation
                                    .getArgument(1);

                            candidates
                                    .record(tour(
                                            "2762860",
                                            "39",
                                            "교동쌈밥"));

                            candidates
                                    .record(new PlaceWithRouteResult(
                                            true,
                                            "커피플레이스",
                                            "부산",
                                            "129.2",
                                            "35.2",
                                            51,
                                            "kakao:cafe",
                                            "CE7",
                                            "음식점 > 카페"));

                            return response;
                        });

        List<PlanScheduleDetail> schedules = service
                .makePlanByAi(travel)
                .planDays()
                .getFirst()
                .schedules();

        assertEquals(
                LocalTime.of(13, 0),
                schedules
                        .getFirst()
                        .endTime());

        assertEquals(
                LocalTime.of(13, 51),
                schedules
                        .get(1)
                        .startTime());

        assertEquals(
                LocalTime.of(14, 51),
                schedules
                        .get(1)
                        .endTime());

        verify(
                handler,
                never()
        ).reselectPlace(
                any(),
                any());
    }

    @Test
    @DisplayName("실패 슬롯만 교체하고 이후 이동시간과 일정 시간 재계산")
    void replacesOnlyInvalidSlotAndRecalculatesFollowingRouteAndTime() {

        PlanScheduleDetail valid = slot(
                "tour:1",
                "해운대",
                9);

        PlanScheduleDetail invalid = slot(
                "tour:2784321",
                "개금밀면",
                10);

        PlanScheduleDetail following = slot(
                "tour:3",
                "이기대",
                11);

        stubCreate(
                new CreatePlanAiResponse(
                        List
                                .of(
                                        new PlanDayDetail(
                                                1,
                                                date,
                                                List
                                                        .of(
                                                                valid,
                                                                invalid,
                                                                following)))));

        when(
                handler
                        .reselectPlace(
                                any(),
                                any()))
                .thenAnswer(invocation -> {
                    PlaceReselectPrompt prompt = invocation.getArgument(0);

                    assertTrue(
                            prompt
                                    .reason()
                                    .contains("39"));

                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates
                            .record(
                                    tour(
                                            "2",
                                            "12",
                                            "동백섬"));

                    return slot(
                            "tour:2",
                            "AI 이름",
                            10);
        });

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()))
                .thenReturn(
                        Mono
                                .just(
                                        new KakaoRouteResult(
                                                null,
                                                null,
                                                null,
                                                30)));

        List<PlanScheduleDetail> result = service
                .makePlanByAi(travel)
                .planDays()
                .getFirst()
                .schedules();

        assertEquals(
                valid,
                result.get(0));

        assertEquals(
                "동백섬",
                result
                        .get(1)
                        .locationName());

        assertEquals(
                LocalTime
                        .of(10, 30),
                result
                        .get(1)
                        .startTime());

        assertEquals(
                LocalTime
                        .of(
                                12,
                                0),
                result
                        .get(2)
                        .startTime());

        verify(
                handler,
                times(1))
                .reselectPlace(
                        any(),
                        any());

        verify(kakao)
                .getRoute(
                        "동백섬",
                        "이기대",
                        Transportation.CAR);
    }

    @Test
    @DisplayName("중복 재추천에도 원본 검증 적용 및 실패 슬롯 삭제 방지")
    void duplicateReselectionUsesSameValidationAndCannotDeleteFailedSlot() {

        PlanScheduleDetail first = slot(
                "tour:1",
                "해운대",
                9);

        stubCreate(
                new CreatePlanAiResponse(
                        List
                                .of(
                                        new PlanDayDetail(
                                                1,
                                                date,
                                                List
                                                        .of(
                                                                first,
                                                                slot(
                                                                        "tour:1",
                                                                        "해운대",
                                                                        10))))));

        List<PlaceCandidateContext> contexts = new ArrayList<>();

        when(
                handler
                        .reselectPlace(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            PlaceCandidateContext candidates = invocation
                                    .getArgument(1);

                            contexts
                                    .add(candidates);

                            candidates
                                    .record(
                                            tour(
                                                    "2784321",
                                                    "39",
                                                    "개금밀면"));

                            return slot(
                                    "tour:2784321",
                                    "개금밀면",
                                    10);
                        });

        BaseException failure = assertThrows(
                BaseException.class,
                () -> service
                        .makePlanByAi(travel));

        assertEquals(
                "PLAN.EXCEPTION.INVALID_AI_PLACE",
                failure
                        .getErrorCode());

        assertEquals(
                2,
                contexts
                        .size());

        assertNotSame(
                contexts
                        .get(0),
                contexts
                        .get(1));
    }

    @Test
    @DisplayName("재선택에서 이전 시도의 후보 재사용 거부")
    void retryCannotReuseCandidateFromEarlierAttempt() {

        stubCreate(
                new CreatePlanAiResponse(
                        List
                                .of(
                                        new PlanDayDetail(
                                                1,
                                                date,
                                                List
                                                        .of(
                                                                slot(
                                                                        "tour:2784321",
                                                                        "개금밀면",
                                                                        9))))));

        List<PlaceCandidateContext> contexts = new ArrayList<>();

        when(
                handler
                        .reselectPlace(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            PlaceCandidateContext candidates = invocation
                                    .getArgument(1);

                            contexts
                                    .add(candidates);

                            if (contexts
                                    .size() == 1) {
                                candidates
                                        .record(
                                                tour(
                                                        "2",
                                                        "12",
                                                        "동백섬"));

                                return slot(
                                        "unknown",
                                        "동백섬",
                                        9);
                            }

                            return slot(
                                    "tour:2",
                                    "동백섬",
                                    9);
                        });

        assertThrows(
                BaseException.class,
                () -> service
                        .makePlanByAi(travel));

        assertEquals(
                2,
                contexts
                        .size());
    }

    @Test
    @DisplayName("이후 정상 슬롯을 예약하여 앞선 실패 슬롯의 재선택에서 제외")
    void reservesLaterValidSlotsBeforeReselectingEarlierFailure() {

        PlanScheduleDetail later = slot(
                "tour:3",
                "이기대",
                11);

        stubCreate(
                new CreatePlanAiResponse(
                        List
                                .of(
                                        new PlanDayDetail(
                                                1,
                                                date,
                                                List
                                                        .of(
                                                                slot(
                                                                        "tour:2784321",
                                                                        "개금밀면",
                                                                        9),
                                                                later)))));

        when(
                handler
                        .reselectPlace(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            PlaceReselectPrompt prompt = invocation
                                    .getArgument(0);

                            assertTrue(
                                    prompt
                                            .usedPlaces()
                                            .contains("tour:3"));

                            PlaceCandidateContext candidates = invocation
                                    .getArgument(1);

                            candidates
                                    .record(
                                            tour(
                                                    "3",
                                                    "12",
                                                    "이기대"));

                            return later;
                        });

        assertThrows(
                BaseException.class,
                () -> service
                        .makePlanByAi(travel));

        verify(
                handler,
                times(2))
                .reselectPlace(
                        any(),
                        any());
    }

    @Test
    @DisplayName("편집 실패 시 검증된 원본 복원 및 2일차 보존")
    void editFallsBackOnlyToVerifiedOriginalAndKeepsSecondDay() {

        PlanScheduleDetail original = slot(
                "kakao:old",
                "해운대",
                9);

        PlanScheduleDetail unchanged = slot(
                "tour:3",
                "이기대",
                9);

        GetAiPlanResponse dayOne = existing(original);

        GetAiPlanResponse existing = new GetAiPlanResponse(
                dayOne
                        .planName(),
                dayOne
                        .travelStyle(),
                dayOne
                        .travelTheme(),
                List
                        .of(),
                List
                        .of(),
                Set
                        .of(),
                List
                        .of(
                                dayOne
                                        .planDays()
                                        .getFirst(),
                                new GetAiPlanResponse.PlanDayDetail(
                                        2,
                                        date
                                                .plusDays(1),
                                        existing(unchanged)
                                                .planDays()
                                                .getFirst()
                                                .schedules())));

        EditPlanAiResponse ai = new EditPlanAiResponse(
                "부산",
                List
                        .of(
                                new PlanDayDetail(
                                        1,
                                        date,
                                        List
                                                .of(
                                                        slot(
                                                                "tour:2784321",
                                                                "개금밀면",
                                                                9))),
                                new PlanDayDetail(
                                        2,
                                        date
                                                .plusDays(1),
                                        List
                                                .of(unchanged))),
                List
                        .of(),
                true);

        when(
                handler
                        .editPlanByAi(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            recordCandidates(
                                    invocation
                                            .getArgument(1));

                            return ai;
                        });

        when(
                kakao
                        .searchPlace("해운대"))
                .thenReturn(
                        Mono
                                .just(
                                        kakaoPlace(
                                                "AT4",
                                                "해운대")));

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()))
                .thenReturn(
                        Mono
                                .just(
                                        new KakaoRouteResult(
                                                null,
                                                null,
                                                null,
                                                10)));

        EditPlanAiResponse result = service
                .makeEditPlanByAi(
                        new PlanEditContext(
                                travel
                                        .createTravelRequest(),
                                List
                                        .of(),
                                existing,
                                "첫날 수정"));

        assertEquals(
                original,
                result
                        .planDays()
                        .get(0)
                        .schedules()
                        .getFirst());

        assertEquals(
                ai
                        .planDays()
                        .get(1),
                result
                        .planDays()
                        .get(1));

        verify(
                handler,
                times(2))
                .reselectPlace(
                        any(),
                        any());
    }

    @Test
    @DisplayName("관광지로 잘못 분류된 기존 음식점 복원 거부")
    void editCannotRestoreRestaurantMislabelledAsAttraction() {

        PlanScheduleDetail invalid = slot(
                "tour:2784321",
                "개금밀면",
                9);

        when(
                handler
                        .editPlanByAi(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            recordCandidates(
                                    invocation
                                            .getArgument(1));

                            return new EditPlanAiResponse(
                                    "부산",
                                    List
                                            .of(
                                                    new PlanDayDetail(
                                                            1,
                                                            date,
                                                            List
                                                                    .of(invalid))),
                                    List
                                            .of(),
                                    true);
                        });

        when(
                kakao
                        .searchPlace("개금밀면"))
                .thenReturn(
                        Mono
                                .just(
                                        kakaoPlace(
                                                "FD6",
                                                "개금밀면")));

        assertThrows(
                BaseException.class,
                () -> service
                        .makeEditPlanByAi(
                                new PlanEditContext(
                                        travel
                                                .createTravelRequest(),
                                        List
                                                .of(),
                                        existing(invalid),
                                        "수정")));
    }

    @Test
    @DisplayName("변경되지 않은 요청 날짜만 재구성하고 다른 날짜 보존")
    void rebuildsOnlyUnchangedRequestedDayThroughService() {

        PlanEditContext context = rebuildContext();

        when(
                handler
                        .classifyEditScope(
                                any()))
                .thenReturn(
                        new PlanEditScope(
                                List
                                        .of(1)));

        stubUnchangedEdit();

        when(
                kakao
                        .searchPlace("이기대"))
                .thenReturn(
                        Mono
                                .just(
                                        kakaoPlace(
                                                "AT4",
                                                "이기대")));

        when(
                handler
                        .rebuildDay(
                                any(),
                                any(),
                                eq(1),
                                anyString(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            PlaceCandidateContext candidates = invocation
                                    .getArgument(4);

                            candidates
                                    .record(
                                            new PlaceWithRouteResult(
                                                    true,
                                                    "동백섬",
                                                    "부산",
                                                    "129.2",
                                                    "35.2",
                                                    10,
                                                    "kakao:new",
                                                    "AT4",
                                                    "관광명소"));

                            return new RebuildPlanDayResponse(
                                    true,
                                    "",
                                    List
                                            .of(
                                                    new PlanDayDetail(
                                                            1,
                                                            date,
                                                            List
                                                                    .of(
                                                                            slot(
                                                                                    "kakao:new",
                                                                                    "동백섬",
                                                                                    9)))));
                        });

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()))
                .thenReturn(
                        Mono
                                .just(
                                        new KakaoRouteResult(
                                                null,
                                                null,
                                                null,
                                                10)));

        EditPlanAiResponse result = service
                .makeEditPlanByAi(context);

        assertEquals(
                "동백섬",
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .locationName());

        assertEquals(
                new PlanDayDetail(
                        2,
                        date
                                .plusDays(1),
                        List
                                .of(
                                        slot(
                                                "kakao:old",
                                                "이기대",
                                                9))),
                result
                        .planDays()
                        .get(1));

        assertEquals(
                List
                        .of("1일차 장소 구성 재구성"),
                result
                        .changes());

        verify(
                handler,
                times(1))
                .rebuildDay(
                        any(),
                        any(),
                        eq(1),
                        anyString(),
                        any());

        verify(
                kakao,
                never())
                .getRoute(
                        anyString(),
                        eq("이기대"),
                        any());
    }

    @Test
    @DisplayName("재구성 응답의 잘못된 AI 복약 시간은 검증 전에 Java가 재생성")
    void rebuildRegeneratesMedicationBeforeValidation() {

        TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule =
                new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                        RelatedMeal.LUNCH,
                        MealTiming.AFTER_MEAL,
                        30
                );

        TravelHealthContext healthContext =
                new TravelHealthContext(
                        "테스트 여행자",
                        DiseaseType.DIABETES,
                        WalkType.MODERATE,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(8, 0),
                                LocalTime.of(12, 0),
                                LocalTime.of(18, 0)
                        ),
                        List.of(),
                        List.of(
                                new TravelHealthContext.MedicationInfoContext(
                                        "테스트 복약",
                                        MedicationBasis.WITH_MEAL,
                                        null,
                                        Set.of(rule)
                                )
                        )
                );

        PlanEditContext original = rebuildContext();

        PlanEditContext context =
                new PlanEditContext(
                        original.createTravelRequest(),
                        List.of(healthContext),
                        original.currentPlan(),
                        original.editRequest()
                );

        when(
                handler
                        .classifyEditScope(
                                any()
                        )
        ).thenReturn(
                new PlanEditScope(
                        List.of(1)
                )
        );

        stubUnchangedEditWithRequiredAttractions();

        when(
                kakao
                        .searchPlace("이기대")
        ).thenReturn(
                Mono.just(
                        kakaoPlace(
                                "AT4",
                                "이기대"
                        )
                )
        );

        when(
                kakao
                        .getRoute(
                                anyString(),
                                anyString(),
                                any()
                        )
        ).thenReturn(
                Mono.just(
                        new KakaoRouteResult(
                                null,
                                null,
                                null,
                                10
                        )
                )
        );

        when(
                handler
                        .rebuildDay(
                                any(),
                                any(),
                                eq(1),
                                anyString(),
                                any()
                        )
        ).thenAnswer(
                invocation -> {
                    PlaceCandidateContext candidates =
                            invocation.getArgument(4);

                    recordCandidates(candidates);

                    candidates.record(
                            new PlaceWithRouteResult(
                                    true,
                                    "동백섬",
                                    "부산",
                                    "129.2",
                                    "35.2",
                                    10,
                                    "kakao:new",
                                    "AT4",
                                    "관광명소"
                            )
                    );

                    PlanScheduleDetail invalidMedication =
                            new PlanScheduleDetail(
                                    ScheduleType.CHECK_IN,
                                    CourseType.MEDICATION,
                                    LocalTime.of(7, 0),
                                    LocalTime.of(7, 0),
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    null,
                                    0,
                                    null,
                                    Set.of(),
                                    null,
                                    null,
                                    null
                            );

                    return new RebuildPlanDayResponse(
                            true,
                            "",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    slot(
                                                            "kakao:new",
                                                            "동백섬",
                                                            9
                                                    ),
                                                    slot(
                                                            "tour:4",
                                                            "오죽헌",
                                                            11
                                                    ),
                                                    slot(
                                                            "tour:5",
                                                            "경포대",
                                                            13
                                                    ),
                                                    invalidMedication
                                            )
                                    )
                            )
                    );
                }
        );

        EditPlanAiResponse result =
                service.makeEditPlanByAi(context);

        PlanScheduleDetail medication =
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow();

        assertEquals(
                LocalTime.of(12, 30),
                medication.startTime()
        );

        assertEquals(
                LocalTime.of(12, 40),
                medication.endTime()
        );

        verify(
                handler,
                never()
        ).reselectPlace(
                any(),
                any()
        );
    }

    @Test
    @DisplayName("두 번 재구성해도 변경되지 않은 일정의 명시적 실패")
    void unchangedDayAfterTwoAttemptsFailsInsteadOfReportingSuccess() {

        when(
                handler
                        .classifyEditScope(
                                any()))
                .thenReturn(
                        new PlanEditScope(
                                List
                                        .of(1)));

        stubUnchangedEdit();

        when(
                kakao
                        .searchPlace("이기대"))
                .thenReturn(
                        Mono
                                .just(
                                        kakaoPlace(
                                                "AT4",
                                                "이기대")));

        List<PlaceCandidateContext> attempts = new ArrayList<>();

        when(
                handler
                        .rebuildDay(
                                any(),
                                any(),
                                eq(1),
                                anyString(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            PlaceCandidateContext candidates = invocation
                                    .getArgument(4);

                            attempts
                                    .add(candidates);

                            candidates
                                    .record(
                                            tour(
                                                    "1",
                                                    "12",
                                                    "해운대"));

                            return new RebuildPlanDayResponse(
                                    true,
                                    "",
                                    List
                                            .of(
                                                    new PlanDayDetail(
                                                            1,
                                                            date,
                                                            List
                                                                    .of(
                                                                            slot(
                                                                                    "tour:1",
                                                                                    "해운대",
                                                                                    9)))));
                        });

        BaseException failure = assertThrows(
                BaseException.class,
                () -> service
                        .makeEditPlanByAi(
                                rebuildContext()));

        assertEquals(
                "PLAN.EXCEPTION.EDIT_NOT_APPLIED",
                failure
                        .getErrorCode());

        assertEquals(
                2,
                attempts
                        .size());

        assertNotSame(
                attempts
                        .get(0),
                attempts
                        .get(1));
    }

    @Test
    @DisplayName("재구성 대상과 다른 날짜의 응답 거부")
    void wrongRebuildDayIsRejectedWithoutChangingOtherDay() {

        when(
                handler
                        .classifyEditScope(
                                any()))
                .thenReturn(
                        new PlanEditScope(
                                List
                                        .of(1)));

        stubUnchangedEdit();

        when(
                kakao
                        .searchPlace("이기대"))
                .thenReturn(
                        Mono
                                .just(
                                        kakaoPlace(
                                                "AT4",
                                                "이기대")));

        when(
                handler
                        .rebuildDay(
                                any(),
                                any(),
                                eq(1),
                                anyString(),
                                any()))
                .thenReturn(
                        new RebuildPlanDayResponse(
                                true,
                                "",
                                List
                                        .of(
                                                new PlanDayDetail(
                                                        2,
                                                        date
                                                                .plusDays(1),
                                                        List
                                                                .of(
                                                                        slot(
                                                                                "tour:3",
                                                                                "이기대",
                                                                                9))))));

        BaseException failure = assertThrows(
                BaseException.class,
                () -> service
                        .makeEditPlanByAi(
                                rebuildContext()));

        assertTrue(
                failure
                        .getMessage()
                        .contains("dayNumber=2"));

        verify(
                handler,
                times(2))
                .rebuildDay(
                        any(),
                        any(),
                        eq(1),
                        anyString(),
                        any());
    }

    @Test
    @DisplayName("불명확한 대상 날짜 및 장소 표기만 바꾼 응답 거부")
    void scopeAndPlaceComparisonRejectAmbiguousDayAndCosmeticChanges() {

        PlanEditValidationHelper validation = new PlanEditValidationHelper();

        assertThrows(
                BaseException.class,
                () -> validation
                        .rebuildDays(
                                new PlanEditScope(
                                        List
                                                .of(0)),
                                rebuildContext()));

        assertFalse(
                validation
                        .rebuilt(
                                rebuildContext(),
                                new PlanDayDetail(
                                        1,
                                        date,
                                        List
                                                .of(
                                                        slot(
                                                                "tour:1",
                                                                "해 운 대",
                                                                9)))));

        assertFalse(
                validation
                        .rebuilt(
                                rebuildContext(),
                                new PlanDayDetail(
                                        1,
                                        date,
                                        List
                                                .of(
                                                        slot(
                                                                "tour:1",
                                                                "해운대 다른 표기",
                                                                9)))));

        assertFalse(
                validation
                        .rebuilt(
                                rebuildContext(),
                                new PlanDayDetail(
                                        1,
                                        date,
                                        List
                                                .of())));
    }

    private void stubUnchangedEditWithRequiredAttractions() {

        when(
                handler
                        .editPlanByAi(
                                any(),
                                any()
                        )
        ).thenAnswer(
                invocation -> {
                    recordCandidates(
                            invocation
                                    .getArgument(1)
                    );

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    slot(
                                                        "tour:1",
                                                        "해운대",
                                                        9
                                                ),
                                                    slot(
                                                            "tour:4",
                                                            "오죽헌",
                                                            11
                                                    ),
                                                    slot(
                                                            "tour:5",
                                                            "경포대",
                                                            13
                                                    )
                                            )
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    slot(
                                                            "tour:3",
                                                            "이기대",
                                                            9
                                                    )
                                            )
                                    )
                            ),
                            List.of("최소 변경 원칙으로 유지"),
                            true
                    );
                }
        );
    }

    private void stubUnchangedEdit() {

        when(
                handler
                        .editPlanByAi(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            recordCandidates(
                                    invocation
                                            .getArgument(1));

                            return new EditPlanAiResponse(
                                    "부산",
                                    List
                                            .of(
                                                    new PlanDayDetail(
                                                            1,
                                                            date,
                                                            List
                                                                    .of(
                                                                            slot(
                                                                                    "tour:1",
                                                                                    "해운대",
                                                                                    9))),
                                                    new PlanDayDetail(
                                                            2,
                                                            date
                                                                    .plusDays(1),
                                                            List
                                                                    .of(
                                                                            slot(
                                                                                    "tour:3",
                                                                                    "이기대",
                                                                                    9)))),
                                    List
                                            .of("최소 변경 원칙으로 유지"),
                                    true);
                        });
    }

    private PlanEditContext rebuildContext() {

        GetAiPlanResponse first = existing(
                slot(
                        "tour:1",
                        "해운대",
                        9));

        GetAiPlanResponse original = new GetAiPlanResponse(
                first
                        .planName(),
                first
                        .travelStyle(),
                first
                        .travelTheme(),
                List
                        .of(),
                List
                        .of(),
                Set
                        .of(),
                List
                        .of(
                                first
                                        .planDays()
                                        .getFirst(),
                                new GetAiPlanResponse.PlanDayDetail(
                                        2,
                                        date
                                                .plusDays(1),
                                        existing(
                                                slot(
                                                        "tour:3",
                                                        "이기대",
                                                        9))
                                                .planDays()
                                                .getFirst()
                                                .schedules())));

        return new PlanEditContext(
                travel
                        .createTravelRequest(),
                List
                        .of(),
                original,
                "1일차 일정을 관광지 위주로 통째로 다시 짜주세요.");
    }

    @Test
    @DisplayName("빈 그룹 코드의 명시적 사찰 상세 분류 보완과 알 수 없는 분류 거부")
    void emptyGroupRequiresVerifiedDetailedCategory() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();
        candidates.record(new PlaceWithRouteResult(
                true,
                "불국사",
                "경주",
                "129.3",
                "35.7",
                10,
                "kakao:temple",
                "",
                "문화,예술 > 종교 > 불교 > 절,사찰"));

        assertTrue(helper
                .validate(slot("kakao:temple", "불국사", 9), candidates, Set.of(), Set.of())
                .valid());

        candidates
                .record(
                        new PlaceWithRouteResult(
                                true,
                                "안압지",
                                "경주",
                                "129.2",
                                "35.8",
                                10,
                                "kakao:pond",
                                "",
                                "여행 > 관광,명소 > 연못"));

        assertTrue(helper
                .validate(slot("kakao:pond", "안압지", 9), candidates, Set.of(), Set.of())
                .valid());

        candidates.record(new PlaceWithRouteResult(
                true,
                "불국사",
                "경주",
                "129.3",
                "35.7",
                10,
                "kakao:temple",
                "",
                "분류 없음"));

        assertFalse(helper
                .validate(slot("kakao:temple", "불국사", 9), candidates, Set.of(), Set.of())
                .valid());
    }

    private void stubCreate(CreatePlanAiResponse response) {

        when(
                handler
                        .createPlanByAi(
                                any(),
                                any()))
                .thenAnswer(
                        invocation -> {
                            recordCandidates(
                                    invocation
                                            .getArgument(1));

                            return response;
                        });
    }

    private void recordCandidates(PlaceCandidateContext candidates) {

        candidates
                .record(
                        tour(
                                "1",
                                "12",
                                "해운대"));

        candidates
                .record(
                        tour(
                                "3",
                                "12",
                                "이기대"));

        candidates
                .record(
                        tour(
                                "2784321",
                                "39",
                                "개금밀면"));

        candidates
                .record(
                        tour(
                                "4",
                                "12",
                                "오죽헌"));

        candidates
                .record(
                        tour(
                                "5",
                                "12",
                                "경포대"));
    }

    private PlanScheduleDetail slot(
            String id,
            String name,
            int hour
    ) {

        return new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime
                        .of(
                                hour,
                                0),
                LocalTime
                        .of(
                                hour + 1,
                                0),
                name,
                "부산",
                "129.1",
                "35.1",
                "원본 사진",
                "원본 썸네일",
                60,
                10,
                Set
                        .of(),
                null,
                null,
                id);
    }

    private Kor2KeywordSearchResponse.Item tour(
            String id,
            String type,
            String name
    ) {

        return new Kor2KeywordSearchResponse.Item(
                "부산",
                "",
                null,
                id,
                type,
                null,
                "원본 사진",
                "원본 썸네일",
                null,
                "129.1",
                "35.1",
                null,
                null,
                null,
                name,
                null,
                null,
                null,
                null,
                null);
    }

    private KakaoPlaceSearchResponse kakaoPlace(String category, String name) {

        return new KakaoPlaceSearchResponse(
                null,
                List
                        .of(
                                new KakaoPlaceSearchResponse.Document(
                                        "old",
                                        name,
                                        "관광",
                                        category,
                                        null,
                                        null,
                                        "부산",
                                        "부산",
                                        "129.1",
                                        "35.1",
                                        null,
                                        null)));
    }

    private GetAiPlanResponse existing(PlanScheduleDetail slot) {

        GetAiPlanResponse.PlanScheduleDetail old = new GetAiPlanResponse.PlanScheduleDetail(
                slot
                        .scheduleType(),
                slot
                        .courseType(),
                slot
                        .startTime(),
                slot
                        .endTime(),
                slot
                        .locationName(),
                slot
                        .location(),
                slot
                        .longitude(),
                slot
                        .latitude(),
                slot
                        .imageUrl(),
                slot
                        .thumbNailImageUrl(),
                slot
                        .stayMinutes(),
                slot
                        .travelMinutes(),
                slot
                        .tags(),
                null,
                null);

        return new GetAiPlanResponse(
                "부산",
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List
                        .of(),
                List
                        .of(),
                Set
                        .of(),
                List
                        .of(
                                new GetAiPlanResponse.PlanDayDetail(
                                        1,
                                        date,
                                        List
                                                .of(old))));
    }
}
