package com.planb.unit.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanDayDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.handler.MissingSlotCompleter;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.TourismTool;
import com.planb.ai.prompt.PlaceReselectPrompt;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.helper.PlanEditValidator;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.repository.PlanRepository;
import com.planb.domain.travel.service.NutritionService;
import com.planb.domain.travel.service.PlanService;
import com.planb.domain.travel.service.ScheduleNormalizer;
import com.planb.domain.travel.service.TravelMinutesResolver;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 등록 알레르기 식재료·분류어 식당의 선택 제외 계약
class PlanAllergenTestService {

    private final TravelRecommendHandler handler = mock(TravelRecommendHandler.class);

    private final KakaoMapServiceHandler kakao = mock(KakaoMapServiceHandler.class);

    private final NutritionService nutritionService = mock(NutritionService.class);

    private final PlanPlaceResolver resolver = new PlanPlaceResolver();

    private final PlanService service = new PlanService(
            mock(PlanRepository.class),
            resolver,
            new PlanEditValidator(),
            new ScheduleNormalizer(resolver),
            handler,
            new TravelMinutesResolver(
                    kakao,
                    4
            ),
            new NutritionEvaluationCollector(),
            nutritionService,
            new MissingSlotCompleter(mock(TourismTool.class)),
            new SimpleMeterRegistry(),
                new com.planb.domain.travel.service.MealWindowRecoveryService(
                        resolver,
                        new ScheduleNormalizer(resolver),
                        new TravelMinutesResolver(
                    kakao,
                    4
            )
                )
        );

    private final LocalDate date = LocalDate.of(
            2026,
            9,
            10
    );

    @BeforeEach
    void stubBoundaries() {

        lenient()
                .when(nutritionService.evaluateFoodNutrition(
                        anyString(),
                        anyList()
                ))
                .thenReturn(Mono.just(new NutritionEvaluationResult(
                        List.of(),
                        NutritionEvaluationStatus.UNAVAILABLE,
                        List.of(),
                        null,
                        null,
                        null
                )));

        lenient()
                .when(kakao.getRoute(
                        any(),
                        any(),
                        any()
                ))
                .thenReturn(Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        10
                )));

        lenient()
                .when(kakao.getRoute(
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any(),
                        any()
                ))
                .thenReturn(Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        10
                )));
    }

    @Test
    @DisplayName("생성 응답의 새우 알레르기 분류어 식당은 사유와 함께 해당 슬롯만 재선택")
    void reselectsMealMatchingAllergenCategory() {

        when(handler.createPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(item(
                            "1",
                            "12",
                            "해운대",
                            "129.11"
                    ));
                    candidates.record(item(
                            "2",
                            "12",
                            "이기대",
                            "129.12"
                    ));
                    candidates.record(item(
                            "7",
                            "39",
                            "해물왕창칼국수",
                            "129.13"
                    ));
                    candidates.recordRestaurantDetail(
                            "tour:7",
                            intro(
                                    "7",
                                    "해물듬뿍칼국수"
                            )
                    );

                    return new CreatePlanAiResponse(List.of(new PlanDayDetail(
                            1,
                            date,
                            List.of(
                                    attraction(
                                            "tour:1",
                                            "해운대",
                                            "129.11",
                                            9
                                    ),
                                    attraction(
                                            "tour:2",
                                            "이기대",
                                            "129.12",
                                            10
                                    ),
                                    lunch(
                                            "tour:7",
                                            "해물왕창칼국수",
                                            "해물듬뿍칼국수",
                                            "129.13"
                                    )
                            )
                    )));
                });

        ArgumentCaptor<PlaceReselectPrompt> prompt = ArgumentCaptor.forClass(PlaceReselectPrompt.class);

        when(handler.reselectPlace(
                prompt.capture(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(item(
                            "20",
                            "39",
                            "할매국밥",
                            "129.14"
                    ));
                    candidates.recordRestaurantDetail(
                            "tour:20",
                            intro(
                                    "20",
                                    "돼지국밥"
                            )
                    );

                    return lunch(
                            "tour:20",
                            "할매국밥",
                            "돼지국밥",
                            "129.14"
                    );
                });

        CreatePlanAiResponse result = service.makePlanByAi(context());

        assertThat(result
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .extracting(PlanScheduleDetail::locationName)
                .containsExactly("할매국밥");

        verify(handler)
                .reselectPlace(
                        any(),
                        any()
                );

        assertThat(prompt
                .getValue()
                .reason())
                .contains(
                        "알레르기",
                        "해물"
                );
    }

    @Test
    @DisplayName("재선택이 다시 알레르기 식당을 고르면 한 번 더 재선택")
    void reselectsAgainWhenReselectionIsAlsoAllergen() {

        stubAllergenLunchCreation();

        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> reselected(
                        invocation.getArgument(1),
                        "21",
                        "중화반점",
                        "짬뽕",
                        "129.15"
                ))
                .thenAnswer(invocation -> reselected(
                        invocation.getArgument(1),
                        "20",
                        "할매국밥",
                        "돼지국밥",
                        "129.14"
                ));

        CreatePlanAiResponse result = service.makePlanByAi(context());

        assertThat(result
                .planDays()
                .getFirst()
                .schedules())
                .filteredOn(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .extracting(PlanScheduleDetail::locationName)
                .containsExactly("할매국밥");

        verify(
                handler,
                times(2)
        )
                .reselectPlace(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("재선택 예산 안에서 알레르기 식당만 나오면 결과에 넣지 않고 장소 검증 실패")
    void failsWhenEveryReselectionIsAllergen() {

        stubAllergenLunchCreation();

        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> reselected(
                        invocation.getArgument(1),
                        "21",
                        "중화반점",
                        "짬뽕",
                        "129.15"
                ));

        assertThatThrownBy(() -> service.makePlanByAi(context()))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(PlanEditExceptionEnum.INVALID_AI_PLACE.getCode());
                            assertThat(exception.getMessage())
                                    .contains("알레르기");
                        }
                );
    }

    private void stubAllergenLunchCreation() {

        when(handler.createPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(item(
                            "1",
                            "12",
                            "해운대",
                            "129.11"
                    ));
                    candidates.record(item(
                            "2",
                            "12",
                            "이기대",
                            "129.12"
                    ));
                    candidates.record(item(
                            "7",
                            "39",
                            "해물왕창칼국수",
                            "129.13"
                    ));
                    candidates.recordRestaurantDetail(
                            "tour:7",
                            intro(
                                    "7",
                                    "해물듬뿍칼국수"
                            )
                    );

                    return new CreatePlanAiResponse(List.of(new PlanDayDetail(
                            1,
                            date,
                            List.of(
                                    attraction(
                                            "tour:1",
                                            "해운대",
                                            "129.11",
                                            9
                                    ),
                                    attraction(
                                            "tour:2",
                                            "이기대",
                                            "129.12",
                                            10
                                    ),
                                    lunch(
                                            "tour:7",
                                            "해물왕창칼국수",
                                            "해물듬뿍칼국수",
                                            "129.13"
                                    )
                            )
                    )));
                });
    }

    // 재선택 호출의 후보 기록과 식당 선택
    private PlanScheduleDetail reselected(
            PlaceCandidateContext candidates,
            String id,
            String name,
            String menu,
            String longitude
    ) {

        candidates.record(item(
                id,
                "39",
                name,
                longitude
        ));
        candidates.recordRestaurantDetail(
                "tour:" + id,
                intro(
                        id,
                        menu
                )
        );

        return lunch(
                "tour:" + id,
                name,
                menu,
                longitude
        );
    }

    private TravelPlanContext context() {

        return new TravelPlanContext(
                new CreateTravelRequest(
                        "부산",
                        "부산",
                        "해운대구",
                        date,
                        DateType.DAY_TRIP,
                        Transportation.CAR,
                        "부산역",
                        List.of(),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of(),
                        List.of()
                ),
                List.of(new TravelHealthContext(
                        "동행인",
                        List.of(),
                        WalkType.MINIMAL,
                        new TravelHealthContext.MealInfoContext(
                                LocalTime.of(
                                        8,
                                        0
                                ),
                                LocalTime.of(
                                        12,
                                        0
                                ),
                                null
                        ),
                        List.of(new TravelHealthContext.FoodInfoContext(
                                "새우",
                                FoodType.ALLERGY
                        )),
                        List.of()
                ))
        );
    }

    private PlanScheduleDetail attraction(
            String candidateId,
            String name,
            String longitude,
            int hour
    ) {

        return new PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(
                        hour,
                        0
                ),
                LocalTime.of(
                        hour,
                        50
                ),
                name,
                name + " 주소",
                longitude,
                "35.1",
                null,
                null,
                50,
                null,
                Set.of(),
                null,
                null,
                candidateId
        );
    }

    private PlanScheduleDetail lunch(
            String candidateId,
            String name,
            String menu,
            String longitude
    ) {

        return new PlanScheduleDetail(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(
                        12,
                        0
                ),
                LocalTime.of(
                        13,
                        0
                ),
                name,
                name + " 주소",
                longitude,
                "35.1",
                null,
                null,
                60,
                null,
                Set.of(),
                null,
                new RestaurantDetail(
                        menu,
                        null,
                        null,
                        null,
                        null,
                        name + " 주소",
                        longitude,
                        "35.1",
                        null
                ),
                candidateId
        );
    }

    private Kor2KeywordSearchResponse.Item item(
            String id,
            String type,
            String name,
            String longitude
    ) {

        return new Kor2KeywordSearchResponse.Item(
                name + " 주소",
                "",
                null,
                id,
                type,
                null,
                null,
                null,
                null,
                longitude,
                "35.1",
                null,
                null,
                null,
                name,
                null,
                null,
                null,
                null,
                null
        );
    }

    private Kor2RestaurantIntroResponse intro(
            String contentId,
            String menu
    ) {

        return new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(
                new Kor2RestaurantIntroResponse.Header(
                        "0000",
                        "OK"
                ),
                new Kor2RestaurantIntroResponse.Body(
                        new Kor2RestaurantIntroResponse.Items(
                                List.of(
                                        new Kor2RestaurantIntroResponse.Item(
                                                contentId,
                                                "39",
                                                menu,
                                                menu
                                        )
                                )
                        ),
                        1,
                        1,
                        1
                )
        ));
    }
}
