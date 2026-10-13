package com.planb.unit.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanDayDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.handler.MissingSlotCompleter;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
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
import com.planb.global.client.kakaoMapService.dto.response.KakaoRouteFailure;
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
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// 편집 요청 범위 밖 원본 보존 계약
class PlanEditScopeTestService {

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
            new SimpleMeterRegistry()
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

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of()));
    }

    @Test
    @DisplayName("AI가 그대로 복사한 대상 밖 장소의 재선택 없는 보존")
    void keepsCopiedSlotsWithoutReselection() {

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(restaurant(
                            "7",
                            "초량밀면",
                            "129.31"
                    ));
                    candidates.recordRestaurantDetail(
                            "tour:7",
                            intro(
                                    "7",
                                    "물밀면"
                            )
                    );

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            original()
                                                    .planDays()
                                                    .get(0)
                                                    .schedules()
                                                    .stream()
                                                    .map(this::copy)
                                                    .toList()
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    copy(original()
                                                            .planDays()
                                                            .get(1)
                                                            .schedules()
                                                            .get(0)),
                                                    meal(
                                                            ScheduleType.LUNCH,
                                                            "tour:7",
                                                            "초량밀면",
                                                            "물밀면",
                                                            "129.31",
                                                            12
                                                    )
                                            )
                                    )
                            ),
                            List.of("2일차 점심을 초량밀면으로 변경했습니다."),
                            true
                    );
                });

        // 실제 재선택 AI처럼 새 장소를 돌려주는 경로
        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));

                    return attractionSlot(
                            "tour:8",
                            "오륙도",
                            "129.41",
                            9
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 메뉴를 다른 것으로 바꿔줘"));

        assertThat(names(
                result,
                1
        ))
                .containsExactly(
                        "해운대",
                        "개금밀면"
                );

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "이기대",
                        "초량밀면"
                );

        verify(
                handler,
                never()
        )
                .reselectPlace(
                        any(),
                        any()
                );
    }

    @Test
    @DisplayName("요청 대상 밖에서 AI가 바꾼 장소의 원본 복원")
    void restoresChangesOutsideTargets() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                null,
                false
        ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    recordNewLunch(candidates);
                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    attractionSlot(
                                                            "tour:8",
                                                            "오륙도",
                                                            "129.41",
                                                            9
                                                    ),
                                                    copy(original()
                                                            .planDays()
                                                            .get(0)
                                                            .schedules()
                                                            .get(1))
                                            )
                                    ),
                                    newLunchDay()
                            ),
                            List.of("2일차 점심을 초량밀면으로 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 식당을 바꿔줘"));

        assertThat(names(
                result,
                1
        ))
                .containsExactly(
                        "해운대",
                        "개금밀면"
                );

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "이기대",
                        "초량밀면"
                );
    }

    @Test
    @DisplayName("관광지만 바꾸는 요청의 식사·MUST_HAVE 보존")
    void replacesOnlyAttractionsOfTargetDay() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.ATTRACTION,
                null,
                false
        ));

        GetAiPlanResponse plan = withMustHave();

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    recordNewLunch(candidates);
                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));
                    candidates.record(attraction(
                            "9",
                            "송도해변",
                            "129.42"
                    ));

                    List<GetAiPlanResponse.PlanScheduleDetail> secondDay = plan
                            .planDays()
                            .get(1)
                            .schedules();

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    attractionSlot(
                                                            "tour:8",
                                                            "오륙도",
                                                            "129.41",
                                                            9
                                                    ),
                                                    copy(secondDay.get(1)),
                                                    meal(
                                                            ScheduleType.LUNCH,
                                                            "tour:7",
                                                            "초량밀면",
                                                            "물밀면",
                                                            "129.31",
                                                            12
                                                    ),
                                                    attractionSlot(
                                                            "tour:9",
                                                            "송도해변",
                                                            "129.42",
                                                            14
                                                    )
                                            )
                                    )
                            ),
                            List.of("2일차 관광지를 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context(
                "2일차 관광지를 전부 다른 곳으로 바꿔줘",
                plan
        ));

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "오륙도",
                        "태종대",
                        "할매국밥",
                        "송도해변"
                );

        assertThat(names(
                result,
                1
        ))
                .containsExactly(
                        "해운대",
                        "개금밀면"
                );
    }

    @Test
    @DisplayName("요청 대상이 그대로면 변경 미반영 거부")
    void rejectsWhenTargetIsNotChanged() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                null,
                false
        ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenReturn(new EditPlanAiResponse(
                        "부산",
                        List.of(
                                copyDay(
                                        original(),
                                        0
                                ),
                                copyDay(
                                        original(),
                                        1
                                )
                        ),
                        List.of("다른 실제 후보가 없어 기존 장소를 유지했습니다."),
                        true
                ));

        assertThatThrownBy(() -> service.makeEditPlanByAi(context("2일차 점심 식당을 바꿔줘")))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlanEditExceptionEnum.EDIT_NOT_APPLIED.getCode())
                );
    }

    @Test
    @DisplayName("날짜 마지막 장소 변경 시 다음 날짜 첫 장소 이동시간 재계산")
    void recalculatesNextDayFirstTravelAfterLastPlaceChange() {

        targets(new PlanEditScope.EditTarget(
                1,
                PlanEditScope.EditSlot.LUNCH,
                null,
                false
        ));

        when(kakao.getRoute(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenAnswer(invocation -> Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        "초량밀면".equals(invocation.getArgument(0))
                                ? 33
                                : 10
                )));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    copy(original()
                                                            .planDays()
                                                            .get(0)
                                                            .schedules()
                                                            .get(0)),
                                                    meal(
                                                            ScheduleType.LUNCH,
                                                            "tour:7",
                                                            "초량밀면",
                                                            "물밀면",
                                                            "129.31",
                                                            12
                                                    )
                                            )
                                    ),
                                    copyDay(
                                            original(),
                                            1
                                    )
                            ),
                            List.of("1일차 점심을 초량밀면으로 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("1일차 점심 식당을 바꿔줘"));

        assertThat(result
                .planDays()
                .get(1)
                .schedules()
                .getFirst()
                .travelMinutes())
                .isEqualTo(33);
    }

    @Test
    @DisplayName("여러 날짜 일정의 날짜 없는 요청은 수정 없이 대상 확인 요청")
    void asksTargetDayWhenUnspecified() {

        // 구조화 응답의 일차 미지정 표기 0
        targets(new PlanEditScope.EditTarget(
                0,
                PlanEditScope.EditSlot.LUNCH,
                "",
                true
        ));

        assertUnclear("점심 메뉴를 바꿔줘");
    }

    @Test
    @DisplayName("해당 날짜에 없는 장소 지정은 수정 없이 대상 확인 요청")
    void asksTargetWhenNamedPlaceIsAbsent() {

        targets(new PlanEditScope.EditTarget(
                1,
                PlanEditScope.EditSlot.ATTRACTION,
                "경복궁",
                false
        ));

        assertUnclear("1일차 경복궁 대신 다른 곳으로 바꿔줘");
    }

    @Test
    @DisplayName("일정에 없는 날짜 지정은 수정 없이 대상 확인 요청")
    void asksTargetWhenDayIsAbsent() {

        targets(new PlanEditScope.EditTarget(
                5,
                PlanEditScope.EditSlot.DINNER,
                null,
                false
        ));

        assertUnclear("5일차 저녁을 바꿔줘");
    }

    @Test
    @DisplayName("메뉴만 변경 시 같은 음식점의 다른 메뉴 후보 전달과 음식점 유지")
    void keepsSameRestaurantForMenuOnlyRequest() {

        menuOnlyLunch();
        sameRestaurantWithMenus(
                "돼지국밥",
                "순대국밥"
        );

        ArgumentCaptor<PlanEditContext> sent = ArgumentCaptor.forClass(PlanEditContext.class);

        when(handler.editPlanByAi(
                sent.capture(),
                any()
        ))
                .thenReturn(lunchChoice(
                        "tour:20",
                        "할매국밥",
                        "순대국밥",
                        "129.22"
                ));

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 메뉴를 다른 것으로 바꿔줘"));

        assertThat(sent
                .getValue()
                .editTargets())
                .singleElement()
                .satisfies(guide -> {
                    assertThat(guide.sameRestaurantCandidateId())
                            .isEqualTo("tour:20");
                    assertThat(guide.sameRestaurantMenus())
                            .containsExactly("순대국밥");
                });

        assertThat(lunch(
                result,
                2
        ))
                .extracting(
                        PlanScheduleDetail::locationName,
                        slot -> slot
                                .restaurantDetail()
                                .menuName()
                )
                .containsExactly(
                        "할매국밥",
                        "순대국밥"
                );
    }

    @Test
    @DisplayName("같은 음식점에 다른 메뉴가 있는데 음식점을 바꾼 메뉴 변경 거부")
    void rejectsRestaurantChangeWhenSameRestaurantHasOtherMenu() {

        menuOnlyLunch();
        sameRestaurantWithMenus(
                "돼지국밥",
                "순대국밥"
        );

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return lunchChoice(
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31"
                    );
                });

        assertThatThrownBy(() -> service.makeEditPlanByAi(context("2일차 점심 메뉴를 다른 것으로 바꿔줘")))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlanEditExceptionEnum.EDIT_NOT_APPLIED.getCode())
                );
    }

    @Test
    @DisplayName("기존 음식점 메뉴를 확인하지 못하면 음식점 변경 허용과 변경 사유 기록")
    void allowsRestaurantChangeWhenNoOtherMenu() {

        menuOnlyLunch();

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return lunchChoice(
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31"
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 메뉴를 다른 것으로 바꿔줘"));

        assertThat(lunch(
                result,
                2
        )
                .locationName())
                .isEqualTo("초량밀면");

        assertThat(result.changes())
                .contains(
                        "2일차 점심: 할매국밥(돼지국밥) → 초량밀면(물밀면)",
                        "2일차 점심: 기존 음식점의 메뉴 정보를 확인하지 못해 음식점을 변경했습니다."
                );
    }

    @Test
    @DisplayName("지목한 장소 하나만 교체, 지목한 MUST_HAVE 포함")
    void replacesOnlyNamedPlace() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.ATTRACTION,
                "태종대",
                false
        ));

        GetAiPlanResponse plan = withMustHave();

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));
                    candidates.record(attraction(
                            "9",
                            "송도해변",
                            "129.42"
                    ));

                    List<GetAiPlanResponse.PlanScheduleDetail> secondDay = plan
                            .planDays()
                            .get(1)
                            .schedules();

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    attractionSlot(
                                                            "tour:9",
                                                            "송도해변",
                                                            "129.42",
                                                            9
                                                    ),
                                                    attractionSlot(
                                                            "tour:8",
                                                            "오륙도",
                                                            "129.41",
                                                            10
                                                    ),
                                                    copy(secondDay.get(2)),
                                                    copy(secondDay.get(3))
                                            )
                                    )
                            ),
                            List.of("2일차 태종대를 오륙도로 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context(
                "2일차 태종대 대신 다른 곳으로 바꿔줘",
                plan
        ));

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "이기대",
                        "오륙도",
                        "할매국밥",
                        "광안리"
                );

        // AI 문구 대신 확정 결과 기준 변경 내역
        assertThat(result.changes())
                .containsExactly("2일차 관광지: 태종대 → 오륙도");
    }

    @Test
    @DisplayName("같은 날짜 안에서 바뀐 장소 다음 구간 이동시간 재계산")
    void recalculatesFollowingTravelWithinDay() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                null,
                false
        ));

        GetAiPlanResponse plan = withMustHave();

        when(kakao.getRoute(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenAnswer(invocation -> Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        "초량밀면".equals(invocation.getArgument(0))
                                ? 33
                                : 10
                )));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    List<GetAiPlanResponse.PlanScheduleDetail> secondDay = plan
                            .planDays()
                            .get(1)
                            .schedules();

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    copy(secondDay.get(0)),
                                                    copy(secondDay.get(1)),
                                                    meal(
                                                            ScheduleType.LUNCH,
                                                            "tour:7",
                                                            "초량밀면",
                                                            "물밀면",
                                                            "129.31",
                                                            12
                                                    ),
                                                    copy(secondDay.get(3))
                                            )
                                    )
                            ),
                            List.of("2일차 점심을 초량밀면으로 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context(
                "2일차 점심 식당을 바꿔줘",
                plan
        ));

        assertThat(result
                .planDays()
                .get(1)
                .schedules()
                .stream()
                .filter(slot -> "광안리".equals(slot.locationName()))
                .findFirst()
                .orElseThrow()
                .travelMinutes())
                .isEqualTo(33);
    }

    @Test
    @DisplayName("대상의 검증 가능한 후보가 없으면 변경 미반영, 경로 공급자 실패와 구분")
    void separatesMissingCandidateFromRouteFailure() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                null,
                false
        ));

        // 이번 호출에서 검색하지 않은 후보, 재선택도 후보 없음
        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenReturn(lunchChoice(
                        "tour:99",
                        "가짜식당",
                        "가짜메뉴",
                        "129.99"
                ));

        assertThatThrownBy(() -> service.makeEditPlanByAi(context("2일차 점심 식당을 바꿔줘")))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlanEditExceptionEnum.EDIT_NOT_APPLIED.getCode())
                );
    }

    @Test
    @DisplayName("대상 변경 후 경로 공급자 실패는 경로 조회 실패로 전달")
    void reportsRouteFailureForChangedTarget() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                null,
                false
        ));

        when(kakao.getRoute(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenAnswer(invocation -> Mono.just("초량밀면".equals(invocation.getArgument(1))
                        ? new KakaoRouteResult(
                                null,
                                null,
                                null,
                                null,
                                new KakaoRouteFailure(
                                        KakaoRouteFailure.Stage.ROUTE,
                                        KakaoRouteFailure.Kind.HTTP_ERROR,
                                        400,
                                        "-10",
                                        null,
                                        null,
                                        "129.21",
                                        "35.1",
                                        "129.31",
                                        "35.1"
                                )
                        )
                        : new KakaoRouteResult(
                                null,
                                null,
                                null,
                                10
                        )));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return lunchChoice(
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31"
                    );
                });

        assertThatThrownBy(() -> service.makeEditPlanByAi(context("2일차 점심 식당을 바꿔줘")))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlanEditExceptionEnum.ROUTE_LOOKUP_FAILED.getCode())
                );
    }

    @Test
    @DisplayName("일차 없는 밀도 감소 요청은 대상 확인 없이 기존 경로 처리")
    void keepsDensityRequestWithoutDay() {

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(
                        List.of(),
                        true,
                        List.of(
                                1,
                                2
                        )
                ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenReturn(new EditPlanAiResponse(
                        "부산",
                        List.of(
                                copyDay(
                                        original(),
                                        0
                                ),
                                copyDay(
                                        original(),
                                        1
                                )
                        ),
                        List.of("전체 일정의 이동을 줄였습니다."),
                        true
                ));

        EditPlanAiResponse result = service.makeEditPlanByAi(context("전체적으로 덜 걷게 해줘"));

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "이기대",
                        "할매국밥"
                );
    }

    @Test
    @DisplayName("하루 일정의 일차 없는 요청은 그 날짜 대상으로 처리")
    void resolvesUnspecifiedDayOnSingleDayPlan() {

        targets(new PlanEditScope.EditTarget(
                0,
                PlanEditScope.EditSlot.LUNCH,
                "",
                false
        ));

        GetAiPlanResponse base = original();

        GetAiPlanResponse singleDay = new GetAiPlanResponse(
                base.planName(),
                base.travelStyle(),
                base.travelTheme(),
                List.of(),
                List.of(),
                Set.of(),
                List.of(base
                        .planDays()
                        .get(0))
        );

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(new PlanDayDetail(
                                    1,
                                    date,
                                    List.of(
                                            copy(base
                                                    .planDays()
                                                    .get(0)
                                                    .schedules()
                                                    .get(0)),
                                            meal(
                                                    ScheduleType.LUNCH,
                                                    "tour:7",
                                                    "초량밀면",
                                                    "물밀면",
                                                    "129.31",
                                                    12
                                            )
                                    )
                            )),
                            List.of("점심을 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context(
                "점심 식당을 바꿔줘",
                singleDay,
                DateType.DAY_TRIP
        ));

        assertThat(names(
                result,
                1
        ))
                .containsExactly(
                        "해운대",
                        "초량밀면"
                );
    }

    @Test
    @DisplayName("관광지 전부 변경 요청에서 일부만 바뀌면 변경 미반영 거부")
    void rejectsPartiallyReplacedAttractions() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.ATTRACTION,
                "",
                false
        ));

        GetAiPlanResponse plan = withMustHave();

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));

                    List<GetAiPlanResponse.PlanScheduleDetail> secondDay = plan
                            .planDays()
                            .get(1)
                            .schedules();

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    attractionSlot(
                                                            "tour:8",
                                                            "오륙도",
                                                            "129.41",
                                                            9
                                                    ),
                                                    copy(secondDay.get(1)),
                                                    copy(secondDay.get(2)),
                                                    copy(secondDay.get(3))
                                            )
                                    )
                            ),
                            List.of("2일차 관광지를 변경했습니다."),
                            true
                    );
                });

        assertThatThrownBy(() -> service.makeEditPlanByAi(context(
                "2일차 관광지를 전부 다른 곳으로 바꿔줘",
                plan
        )))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlanEditExceptionEnum.EDIT_NOT_APPLIED.getCode())
                );
    }

    @Test
    @DisplayName("한 날짜의 여러 대상 동시 반영과 확정 결과 기준 변경 내역")
    void appliesMultipleTargetsOnOneDay() {

        targets(
                new PlanEditScope.EditTarget(
                        2,
                        PlanEditScope.EditSlot.LUNCH,
                        "",
                        false
                ),
                new PlanEditScope.EditTarget(
                        2,
                        PlanEditScope.EditSlot.ATTRACTION,
                        "태종대",
                        false
                )
        );

        GetAiPlanResponse plan = withMustHave();

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    recordNewLunch(candidates);
                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));

                    List<GetAiPlanResponse.PlanScheduleDetail> secondDay = plan
                            .planDays()
                            .get(1)
                            .schedules();

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    copy(secondDay.get(0)),
                                                    attractionSlot(
                                                            "tour:8",
                                                            "오륙도",
                                                            "129.41",
                                                            10
                                                    ),
                                                    meal(
                                                            ScheduleType.LUNCH,
                                                            "tour:7",
                                                            "초량밀면",
                                                            "물밀면",
                                                            "129.31",
                                                            12
                                                    ),
                                                    copy(secondDay.get(3))
                                            )
                                    )
                            ),
                            List.of("AI 변경 문구"),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context(
                "2일차 점심 식당과 태종대를 바꿔줘",
                plan
        ));

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "이기대",
                        "오륙도",
                        "초량밀면",
                        "광안리"
                );

        assertThat(result.changes())
                .containsExactlyInAnyOrder(
                        "2일차 점심: 할매국밥(돼지국밥) → 초량밀면(물밀면)",
                        "2일차 관광지: 태종대 → 오륙도"
                );
    }

    @Test
    @DisplayName("새 선택이 다른 날짜 보존 장소와 겹치면 보존 장소 유지, 새 선택만 재선택")
    void keepsPreservedPlaceWhenNewChoiceDuplicatesIt() {

        targets(new PlanEditScope.EditTarget(
                1,
                PlanEditScope.EditSlot.LUNCH,
                "",
                false
        ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(restaurant(
                            "20",
                            "할매국밥",
                            "129.22"
                    ));
                    candidates.recordRestaurantDetail(
                            "tour:20",
                            intro(
                                    "20",
                                    "돼지국밥"
                            )
                    );

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    copy(original()
                                                            .planDays()
                                                            .get(0)
                                                            .schedules()
                                                            .get(0)),
                                                    meal(
                                                            ScheduleType.LUNCH,
                                                            "tour:20",
                                                            "할매국밥",
                                                            "돼지국밥",
                                                            "129.22",
                                                            12
                                                    )
                                            )
                                    ),
                                    copyDay(
                                            original(),
                                            1
                                    )
                            ),
                            List.of("1일차 점심을 할매국밥으로 변경했습니다."),
                            true
                    );
                });

        // 보존 장소가 아닌 새 선택의 재선택
        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return meal(
                            ScheduleType.LUNCH,
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31",
                            12
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("1일차 점심 식당을 바꿔줘"));

        assertThat(names(
                result,
                1
        ))
                .containsExactly(
                        "해운대",
                        "초량밀면"
                );

        assertThat(names(
                result,
                2
        ))
                .containsExactly(
                        "이기대",
                        "할매국밥"
                );
    }

    @Test
    @DisplayName("같은 원본을 두 번 복사한 AI 응답의 두 번째 슬롯은 보존 대신 재선택")
    void reselectsSecondCopyOfSameOriginal() {

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenReturn(new EditPlanAiResponse(
                        "부산",
                        List.of(
                                new PlanDayDetail(
                                        1,
                                        date,
                                        List.of(
                                                copy(original()
                                                        .planDays()
                                                        .get(0)
                                                        .schedules()
                                                        .get(0)),
                                                copy(original()
                                                        .planDays()
                                                        .get(0)
                                                        .schedules()
                                                        .get(0)),
                                                copy(original()
                                                        .planDays()
                                                        .get(0)
                                                        .schedules()
                                                        .get(1))
                                        )
                                ),
                                copyDay(
                                        original(),
                                        1
                                )
                        ),
                        List.of("1일차 일정을 조정했습니다."),
                        true
                ));

        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    candidates.record(attraction(
                            "8",
                            "오륙도",
                            "129.41"
                    ));

                    return attractionSlot(
                            "tour:8",
                            "오륙도",
                            "129.41",
                            9
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("1일차 시간을 조정해줘"));

        assertThat(names(
                result,
                1
        ))
                .containsExactlyInAnyOrder(
                        "해운대",
                        "오륙도",
                        "개금밀면"
                );
    }

    @Test
    @DisplayName("기존 음식점 응답 해석 실패는 메뉴 후보 없음으로 처리")
    void treatsUndecodableRestaurantLookupAsUnknown() {

        menuOnlyLunch();

        when(handler.findRestaurant(
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenThrow(new org.springframework.core.codec.DecodingException("XML 오류 본문"));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return lunchChoice(
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31"
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 메뉴를 다른 것으로 바꿔줘"));

        assertThat(result.changes())
                .contains("2일차 점심: 기존 음식점의 메뉴 정보를 확인하지 못해 음식점을 변경했습니다.");
    }

    @Test
    @DisplayName("같은 음식점에 다른 메뉴가 없으면 음식점 변경과 사유 기록")
    void notesRestaurantChangeWhenSameRestaurantHasNoOtherMenu() {

        menuOnlyLunch();
        sameRestaurantWithMenus(
                "돼지국밥",
                "돼지국밥"
        );

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return lunchChoice(
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31"
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 메뉴를 다른 것으로 바꿔줘"));

        assertThat(result.changes())
                .contains("2일차 점심: 같은 음식점에 다른 메뉴가 없어 음식점을 변경했습니다.");
    }

    @Test
    @DisplayName("요청 장소명의 일부만 말해도 그 이름을 포함한 유일한 저장 장소로 확정")
    void resolvesPartialPlaceName() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.ATTRACTION,
                "태종",
                false
        ));

        GetAiPlanResponse plan = withMustHave();

        ArgumentCaptor<PlanEditContext> sent = ArgumentCaptor.forClass(PlanEditContext.class);

        when(handler.editPlanByAi(
                sent.capture(),
                any()
        ))
                .thenThrow(new IllegalStateException("대상 확정 확인 후 중단"));

        assertThatThrownBy(() -> service.makeEditPlanByAi(context(
                "2일차 태종 대신 다른 곳",
                plan
        )))
                .isInstanceOf(IllegalStateException.class);

        assertThat(sent
                .getValue()
                .editTargets())
                .singleElement()
                .satisfies(guide -> assertThat(guide.placeName())
                        .isEqualTo("태종대"));
    }

    @Test
    @DisplayName("요청 장소명이 저장 장소명보다 길면 포함 관계로 확정하지 않음")
    void rejectsLongerRequestedNameContainingStoredName() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.ATTRACTION,
                "이기대 해안산책로",
                false
        ));

        assertUnclear("2일차 이기대 해안산책로 대신 다른 곳");
    }

    @Test
    @DisplayName("날짜 재구성과 다른 날짜 대상 혼합 시 다음 날짜 첫 이동시간 재계산")
    void recalculatesNextDayAfterRebuiltDayWithTargets() {

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(
                        List.of(1),
                        false,
                        List.of(),
                        List.of(new PlanEditScope.EditTarget(
                                2,
                                PlanEditScope.EditSlot.LUNCH,
                                "",
                                false
                        ))
                ));

        when(kakao.getRoute(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenAnswer(invocation -> Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        "송도해변".equals(invocation.getArgument(0))
                                ? 44
                                : 10
                )));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    recordNewLunch(candidates);
                    candidates.record(attraction(
                            "9",
                            "송도해변",
                            "129.42"
                    ));

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    new PlanDayDetail(
                                            1,
                                            date,
                                            List.of(
                                                    copy(original()
                                                            .planDays()
                                                            .get(0)
                                                            .schedules()
                                                            .get(0)),
                                                    attractionSlot(
                                                            "tour:9",
                                                            "송도해변",
                                                            "129.42",
                                                            12
                                                    )
                                            )
                                    ),
                                    newLunchDay()
                            ),
                            List.of("1일차 재구성, 2일차 점심 변경"),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("1일차는 통째로 다시, 2일차 점심 식당도 바꿔줘"));

        assertThat(result
                .planDays()
                .get(1)
                .schedules()
                .getFirst()
                .travelMinutes())
                .isEqualTo(44);
    }

    @Test
    @DisplayName("처리 불가 응답은 대상 확인 없이 처리 불가 미리보기로 반환")
    void returnsUnprocessableResponseWithoutTargetCheck() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                "",
                false
        ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenReturn(new EditPlanAiResponse(
                        "부산",
                        List.of(
                                copyDay(
                                        original(),
                                        0
                                ),
                                copyDay(
                                        original(),
                                        1
                                )
                        ),
                        List.of(),
                        false
                ));

        EditPlanAiResponse result = service.makeEditPlanByAi(context("오늘 날씨 알려줘"));

        assertThat(result.processable())
                .isFalse();

        assertThat(result.changes())
                .isEmpty();
    }

    @Test
    @DisplayName("식사 종류를 잘못 붙인 AI 새 식당도 같은 시작 시각 식사 대상으로 채택")
    void acceptsMislabeledMealAtSameStartTime() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                "",
                false
        ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    PlanScheduleDetail lunch = meal(
                            ScheduleType.LUNCH,
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31",
                            12
                    );

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            original(),
                                            0
                                    ),
                                    new PlanDayDetail(
                                            2,
                                            date.plusDays(1),
                                            List.of(
                                                    copy(original()
                                                            .planDays()
                                                            .get(1)
                                                            .schedules()
                                                            .get(0)),
                                                    new PlanScheduleDetail(
                                                            ScheduleType.ACTIVITY,
                                                            lunch.courseType(),
                                                            lunch.startTime(),
                                                            lunch.endTime(),
                                                            lunch.locationName(),
                                                            lunch.location(),
                                                            lunch.longitude(),
                                                            lunch.latitude(),
                                                            lunch.imageUrl(),
                                                            lunch.thumbNailImageUrl(),
                                                            lunch.stayMinutes(),
                                                            lunch.travelMinutes(),
                                                            lunch.tags(),
                                                            lunch.medication(),
                                                            lunch.restaurantDetail(),
                                                            lunch.candidateId()
                                                    )
                                            )
                                    )
                            ),
                            List.of("2일차 점심을 초량밀면으로 변경했습니다."),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("2일차 점심 식당을 바꿔줘"));

        assertThat(lunch(
                result,
                2
        )
                .locationName())
                .isEqualTo("초량밀면");
    }

    @Test
    @DisplayName("부분 변경 대상과 함께 온 밀도 감소 날짜의 고정 변경 문구")
    void addsDensityChangeLineWithTargets() {

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(
                        List.of(),
                        false,
                        List.of(1),
                        List.of(new PlanEditScope.EditTarget(
                                2,
                                PlanEditScope.EditSlot.LUNCH,
                                "",
                                false
                        ))
                ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            original(),
                                            0
                                    ),
                                    newLunchDay()
                            ),
                            List.of("AI 변경 문구"),
                            true
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(context("1일차는 덜 걷게, 2일차 점심 식당도 바꿔줘"));

        assertThat(result.changes())
                .containsExactly(
                        "2일차 점심: 할매국밥(돼지국밥) → 초량밀면(물밀면)",
                        "1일차 일정 밀도 조정"
                );
    }

    @Test
    @DisplayName("편집 대상 식사의 알레르기 식당은 해당 대상만 재선택")
    void reselectsAllergenRestaurantForEditTarget() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                "",
                false
        ));

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordAllergenLunch(invocation.getArgument(1));

                    GetAiPlanResponse plan = allergyPlan(
                            "할매국밥",
                            "돼지국밥"
                    );

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    allergenLunchDay(plan)
                            ),
                            List.of("2일차 점심 변경"),
                            true
                    );
                });

        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordNewLunch(invocation.getArgument(1));

                    return meal(
                            ScheduleType.LUNCH,
                            "tour:7",
                            "초량밀면",
                            "물밀면",
                            "129.31",
                            12
                    );
                });

        EditPlanAiResponse result = service.makeEditPlanByAi(allergicContext(
                "2일차 점심 식당을 바꿔줘",
                allergyPlan(
                        "할매국밥",
                        "돼지국밥"
                )
        ));

        assertThat(lunch(
                result,
                2
        )
                .locationName())
                .isEqualTo("초량밀면");
    }

    @Test
    @DisplayName("알레르기 식당 재선택 실패 시 알레르기 저장 원본으로 되돌리지 않음")
    void doesNotRestoreAllergenOriginalAfterFailedReselection() {

        GetAiPlanResponse plan = allergyPlan(
                "해물파전집",
                "해물파전"
        );

        when(handler.editPlanByAi(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordAllergenLunch(invocation.getArgument(1));

                    return new EditPlanAiResponse(
                            "부산",
                            List.of(
                                    copyDay(
                                            plan,
                                            0
                                    ),
                                    allergenLunchDay(plan)
                            ),
                            List.of("2일차 점심 변경"),
                            true
                    );
                });

        // 재선택도 매번 알레르기 식당 선택
        when(handler.reselectPlace(
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    recordAllergenLunch(invocation.getArgument(1));

                    return meal(
                            ScheduleType.LUNCH,
                            "tour:30",
                            "해물칼국수집",
                            "해물칼국수",
                            "129.33",
                            12
                    );
                });

        assertThatThrownBy(() -> service.makeEditPlanByAi(allergicContext(
                "점심을 바꿔줘",
                plan
        )))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> {
                            assertThat(exception.getErrorCode())
                                    .isEqualTo(PlanEditExceptionEnum.INVALID_AI_PLACE.getCode());
                            assertThat(exception.getMessage())
                                    .contains("알레르기");
                        }
                );

        verify(
                handler,
                times(2)
        )
                .reselectPlace(
                        any(),
                        any()
                );
    }

    private void recordAllergenLunch(PlaceCandidateContext candidates) {

        candidates.record(restaurant(
                "30",
                "해물칼국수집",
                "129.33"
        ));
        candidates.recordRestaurantDetail(
                "tour:30",
                intro(
                        "30",
                        "해물칼국수"
                )
        );
    }

    // 새우 알레르기 동행인을 포함한 편집 요청
    private PlanEditContext allergicContext(
            String request,
            GetAiPlanResponse plan
    ) {

        PlanEditContext base = context(
                request,
                plan
        );

        return new PlanEditContext(
                base.createTravelRequest(),
                List.of(new TravelHealthContext(
                        "동행인",
                        List.of(),
                        WalkType.MINIMAL,
                        null,
                        List.of(new TravelHealthContext.FoodInfoContext(
                                "새우",
                                FoodType.ALLERGY
                        )),
                        List.of()
                )),
                plan,
                request
        );
    }

    // 하루 관광지 2곳(걷기 최소 기준)과 지정한 2일차 점심의 저장 일정
    private GetAiPlanResponse allergyPlan(
            String lunchName,
            String lunchMenu
    ) {

        return new GetAiPlanResponse(
                "부산",
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List.of(),
                List.of(),
                Set.of(),
                List.of(
                        new GetAiPlanResponse.PlanDayDetail(
                                1,
                                date,
                                List.of(
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "해운대",
                                                null,
                                                "129.11",
                                                9
                                        ),
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "동백섬",
                                                null,
                                                "129.15",
                                                10
                                        ),
                                        stored(
                                                ScheduleType.LUNCH,
                                                CourseType.RESTAURANT,
                                                "개금밀면",
                                                "밀면",
                                                "129.12",
                                                12
                                        )
                                )
                        ),
                        new GetAiPlanResponse.PlanDayDetail(
                                2,
                                date.plusDays(1),
                                List.of(
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "이기대",
                                                null,
                                                "129.21",
                                                9
                                        ),
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "광안리",
                                                null,
                                                "129.24",
                                                10
                                        ),
                                        stored(
                                                ScheduleType.LUNCH,
                                                CourseType.RESTAURANT,
                                                lunchName,
                                                lunchMenu,
                                                "129.34",
                                                12
                                        )
                                )
                        )
                )
        );
    }

    // 2일차 관광지 복사와 알레르기 분류어 점심
    private PlanDayDetail allergenLunchDay(GetAiPlanResponse plan) {

        List<GetAiPlanResponse.PlanScheduleDetail> secondDay = plan
                .planDays()
                .get(1)
                .schedules();

        return new PlanDayDetail(
                2,
                date.plusDays(1),
                List.of(
                        copy(secondDay.get(0)),
                        copy(secondDay.get(1)),
                        meal(
                                ScheduleType.LUNCH,
                                "tour:30",
                                "해물칼국수집",
                                "해물칼국수",
                                "129.33",
                                12
                        )
                )
        );
    }

    private void menuOnlyLunch() {

        targets(new PlanEditScope.EditTarget(
                2,
                PlanEditScope.EditSlot.LUNCH,
                null,
                true
        ));
    }

    // 기존 점심 음식점의 TourAPI 원본과 상세 메뉴
    private void sameRestaurantWithMenus(
            String firstMenu,
            String treatMenu
    ) {

        when(handler.findRestaurant(
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(4);

                    candidates.record(restaurant(
                            "20",
                            "할매국밥",
                            "129.22"
                    ));
                    candidates.recordRestaurantDetail(
                            "tour:20",
                            intro(
                                    "20",
                                    firstMenu,
                                    treatMenu
                            )
                    );

                    return Optional.of(candidates.find("tour:20"));
                });
    }

    private EditPlanAiResponse lunchChoice(
            String candidateId,
            String name,
            String menu,
            String longitude
    ) {

        return new EditPlanAiResponse(
                "부산",
                List.of(
                        copyDay(
                                original(),
                                0
                        ),
                        new PlanDayDetail(
                                2,
                                date.plusDays(1),
                                List.of(
                                        copy(original()
                                                .planDays()
                                                .get(1)
                                                .schedules()
                                                .get(0)),
                                        meal(
                                                ScheduleType.LUNCH,
                                                candidateId,
                                                name,
                                                menu,
                                                longitude,
                                                12
                                        )
                                )
                        )
                ),
                List.of("2일차 점심 메뉴를 변경했습니다."),
                true
        );
    }

    private PlanScheduleDetail lunch(
            EditPlanAiResponse response,
            int dayNumber
    ) {

        return response
                .planDays()
                .stream()
                .filter(day -> day.dayNumber() == dayNumber)
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .findFirst()
                .orElseThrow();
    }

    private void assertUnclear(String request) {

        assertThatThrownBy(() -> service.makeEditPlanByAi(context(request)))
                .isInstanceOfSatisfying(
                        BaseException.class,
                        exception -> assertThat(exception.getErrorCode())
                                .isEqualTo(PlanEditExceptionEnum.EDIT_TARGET_UNCLEAR.getCode())
                );

        verify(
                handler,
                never()
        )
                .editPlanByAi(
                        any(),
                        any()
                );
    }

    private void targets(PlanEditScope.EditTarget... targets) {

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(
                        List.of(),
                        true,
                        List.of(),
                        List.of(targets)
                ));
    }

    private void recordNewLunch(PlaceCandidateContext candidates) {

        candidates.record(restaurant(
                "7",
                "초량밀면",
                "129.31"
        ));
        candidates.recordRestaurantDetail(
                "tour:7",
                intro(
                        "7",
                        "물밀면"
                )
        );
    }

    // 2일차 관광지 원본 복사와 새 점심
    private PlanDayDetail newLunchDay() {

        return new PlanDayDetail(
                2,
                date.plusDays(1),
                List.of(
                        copy(original()
                                .planDays()
                                .get(1)
                                .schedules()
                                .get(0)),
                        meal(
                                ScheduleType.LUNCH,
                                "tour:7",
                                "초량밀면",
                                "물밀면",
                                "129.31",
                                12
                        )
                )
        );
    }

    private PlanEditContext context(String request) {

        return context(
                request,
                original()
        );
    }

    private PlanEditContext context(
            String request,
            GetAiPlanResponse plan
    ) {

        return context(
                request,
                plan,
                DateType.ONE_NIGHT_TWO_DAYS
        );
    }

    private PlanEditContext context(
            String request,
            GetAiPlanResponse plan,
            DateType dateType
    ) {

        return new PlanEditContext(
                new CreateTravelRequest(
                        "부산",
                        "부산",
                        "해운대구",
                        date,
                        dateType,
                        Transportation.CAR,
                        "부산역",
                        List.of(),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of(),
                        List.of()
                ),
                List.of(),
                plan,
                request
        );
    }

    // 2일차 이기대·태종대(MUST_HAVE)·할매국밥·광안리
    private GetAiPlanResponse withMustHave() {

        GetAiPlanResponse base = original();

        return new GetAiPlanResponse(
                base.planName(),
                base.travelStyle(),
                base.travelTheme(),
                List.of(),
                List.of(),
                Set.of(),
                List.of(
                        base
                                .planDays()
                                .get(0),
                        new GetAiPlanResponse.PlanDayDetail(
                                2,
                                date.plusDays(1),
                                List.of(
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "이기대",
                                                null,
                                                "129.21",
                                                9
                                        ),
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.MUST_HAVE,
                                                "태종대",
                                                null,
                                                "129.23",
                                                10
                                        ),
                                        stored(
                                                ScheduleType.LUNCH,
                                                CourseType.RESTAURANT,
                                                "할매국밥",
                                                "돼지국밥",
                                                "129.22",
                                                12
                                        ),
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "광안리",
                                                null,
                                                "129.24",
                                                14
                                        )
                                )
                        )
                )
        );
    }

    private PlanDayDetail copyDay(
            GetAiPlanResponse plan,
            int index
    ) {

        GetAiPlanResponse.PlanDayDetail day = plan
                .planDays()
                .get(index);

        return new PlanDayDetail(
                day.dayNumber(),
                day.date(),
                day
                        .schedules()
                        .stream()
                        .map(this::copy)
                        .toList()
        );
    }

    // 1일차 해운대·개금밀면, 2일차 이기대·할매국밥
    private GetAiPlanResponse original() {

        return new GetAiPlanResponse(
                "부산",
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List.of(),
                List.of(),
                Set.of(),
                List.of(
                        new GetAiPlanResponse.PlanDayDetail(
                                1,
                                date,
                                List.of(
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "해운대",
                                                null,
                                                "129.11",
                                                9
                                        ),
                                        stored(
                                                ScheduleType.LUNCH,
                                                CourseType.RESTAURANT,
                                                "개금밀면",
                                                "밀면",
                                                "129.12",
                                                12
                                        )
                                )
                        ),
                        new GetAiPlanResponse.PlanDayDetail(
                                2,
                                date.plusDays(1),
                                List.of(
                                        stored(
                                                ScheduleType.ACTIVITY,
                                                CourseType.ATTRACTION,
                                                "이기대",
                                                null,
                                                "129.21",
                                                9
                                        ),
                                        stored(
                                                ScheduleType.LUNCH,
                                                CourseType.RESTAURANT,
                                                "할매국밥",
                                                "돼지국밥",
                                                "129.22",
                                                12
                                        )
                                )
                        )
                )
        );
    }

    private GetAiPlanResponse.PlanScheduleDetail stored(
            ScheduleType scheduleType,
            CourseType courseType,
            String name,
            String menu,
            String longitude,
            int hour
    ) {

        return new GetAiPlanResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                LocalTime.of(
                        hour,
                        0
                ),
                LocalTime.of(
                        hour + 1,
                        0
                ),
                name,
                name + " 주소",
                longitude,
                "35.1",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                menu == null
                        ? null
                        : new GetAiPlanResponse.RestaurantDetail(
                                menu,
                                null,
                                null,
                                null,
                                null,
                                name + " 주소",
                                longitude,
                                "35.1",
                                null
                        )
        );
    }

    // 원본 값을 그대로 옮긴 AI 응답 슬롯, 저장 일정에는 candidateId 없음
    private PlanScheduleDetail copy(GetAiPlanResponse.PlanScheduleDetail old) {

        return resolver.fromExisting(old);
    }

    private PlanScheduleDetail meal(
            ScheduleType scheduleType,
            String candidateId,
            String name,
            String menu,
            String longitude,
            int hour
    ) {

        return new PlanScheduleDetail(
                scheduleType,
                CourseType.RESTAURANT,
                LocalTime.of(
                        hour,
                        0
                ),
                LocalTime.of(
                        hour + 1,
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

    private PlanScheduleDetail attractionSlot(
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
                        hour + 1,
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
                null,
                candidateId
        );
    }

    private Kor2KeywordSearchResponse.Item restaurant(
            String id,
            String name,
            String longitude
    ) {

        return item(
                id,
                "39",
                name,
                longitude
        );
    }

    private Kor2KeywordSearchResponse.Item attraction(
            String id,
            String name,
            String longitude
    ) {

        return item(
                id,
                "12",
                name,
                longitude
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

        return intro(
                contentId,
                menu,
                menu
        );
    }

    private Kor2RestaurantIntroResponse intro(
            String contentId,
            String firstMenu,
            String treatMenu
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
                                                firstMenu,
                                                treatMenu
                                        )
                                )
                        ),
                        1,
                        1,
                        1
                )
        ));
    }

    private List<String> names(
            EditPlanAiResponse response,
            int dayNumber
    ) {

        return response
                .planDays()
                .stream()
                .filter(day -> day.dayNumber() == dayNumber)
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(resolver::requiresPlace)
                .map(PlanScheduleDetail::locationName)
                .toList();
    }
}
