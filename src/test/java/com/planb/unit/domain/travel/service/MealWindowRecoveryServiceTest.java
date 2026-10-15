package com.planb.unit.domain.travel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.service.MealWindowRecoveryService;
import com.planb.domain.travel.service.ScheduleNormalizer;
import com.planb.domain.travel.service.TravelMinutesResolver;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MealWindowRecoveryServiceTest {

    @Test
    @DisplayName("등록 점심 창 안의 식당 복구와 앞뒤 관광지 시각 보존")
    void recoversLunchWithoutMovingOtherPlaces() throws Exception {

        Fixture fixture = fixture();
        var service = fixture.service();
        var input = fixture.input();
        var context = fixture.context();
        var candidates = fixture.candidates();
        var previous = fixture.previous();
        var next = fixture.next();

        var result = service.recover(input, context, candidates).planDays().getFirst().schedules();

        assertThat(result.get(0)).isEqualTo(previous);
        assertThat(result.get(1).candidateId()).isEqualTo("tour:910002");
        assertThat(result.get(1).startTime()).isEqualTo(LocalTime.NOON);
        assertThat(result.get(1).restaurantDetail().menuName()).isEqualTo("불고기");
        assertThat(result.get(2).locationName()).isEqualTo(next.locationName());
        assertThat(result.get(2).startTime()).isEqualTo(next.startTime());
        assertThat(result.get(2).endTime()).isEqualTo(next.endTime());
    }

    @Test
    @DisplayName("점심 뒤 고정 장소에 도착할 수 없는 후보 거부")
    void rejectsOutboundCollision() throws Exception {

        Fixture fixture = fixture();
        when(fixture.routes().lookup(anyString(), any(), any(), any()))
                .thenReturn(
                        new TravelMinutesResolver.SegmentMinutes(Optional.of(10), null),
                        new TravelMinutesResolver.SegmentMinutes(Optional.of(90), null)
                );
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                fixture.input(), fixture.context(), fixture.candidates()
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class)
                .hasMessageContaining("등록 식사 시간");
    }

    @Test
    @DisplayName("확인하지 못한 이동시간의 식사시간 오류와 구분")
    void distinguishesUnavailableRoute() throws Exception {

        Fixture fixture = fixture();
        when(fixture.routes().lookup(anyString(), any(), any(), any()))
                .thenReturn(new TravelMinutesResolver.SegmentMinutes(Optional.empty(), null));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                fixture.input(), fixture.context(), fixture.candidates()
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class)
                .hasMessageContaining("이동 경로 조회 실패");
    }

    @Test
    @DisplayName("다른 날짜에서 사용한 식당 재사용 차단")
    void rejectsRestaurantReservedByOtherDay() throws Exception {

        Fixture fixture = fixture();
        var used = new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.LUNCH, CourseType.RESTAURANT, LocalTime.NOON, LocalTime.of(13, 0),
                "가까운 식당", "서울 종로구", "126.98", "37.57", null, null, 60, 10,
                Set.of(), null,
                new CreatePlanAiResponse.RestaurantDetail(
                        "불고기",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "tour:910002"
        );
        var days = new java.util.ArrayList<>(fixture.input().planDays());
        days.add(new CreatePlanAiResponse.PlanDayDetail(
                2,
                LocalDate.of(2026, 10, 16),
                List.of(used)
        ));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                new CreatePlanAiResponse(days), fixture.context(), fixture.candidates()
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class);
        org.mockito.Mockito.verifyNoInteractions(fixture.routes());
    }

    @Test
    @DisplayName("등록 알레르기 메뉴의 경로 조회 전 제외")
    void rejectsAllergenBeforeRouteLookup() throws Exception {

        Fixture fixture = fixture();
        var health = fixture.context().healthContexts().getFirst();
        TravelPlanContext allergic = new TravelPlanContext(fixture.context().createTravelRequest(), List.of(
                new TravelHealthContext(health.travelerName(), health.diseaseTypes(), health.walkType(),
                        health.mealInfo(), List.of(new TravelHealthContext.FoodInfoContext(
                                "불고기", com.planb.domain.health.entity.constant.FoodType.ALLERGY
                        )), List.of())
        ));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                fixture.input(), allergic, fixture.candidates()
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class);
        org.mockito.Mockito.verifyNoInteractions(fixture.routes());
    }

    @Test
    @DisplayName("조회 원본 메뉴가 없는 식당 후보 제외")
    void rejectsCandidateWithoutOriginalMenus() throws Exception {

        Fixture fixture = fixture();
        PlaceCandidateContext empty = new PlaceCandidateContext();
        empty.record(new ObjectMapper().readValue("""
                {"contentid":"910002","contenttypeid":"39","title":"가까운 식당",
                "addr1":"서울 종로구","mapx":"126.98","mapy":"37.57"}
                """, Kor2KeywordSearchResponse.Item.class));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                fixture.input(), fixture.context(), empty
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class);
        org.mockito.Mockito.verifyNoInteractions(fixture.routes());
    }

    @Test
    @DisplayName("등록 창 안 식사의 동일 결과와 추가 경로 조회 생략")
    void preservesRepairedResult() throws Exception {

        Fixture fixture = fixture();
        var repaired = fixture.service().recover(fixture.input(), fixture.context(), fixture.candidates());
        org.mockito.Mockito.clearInvocations(fixture.routes());
        assertThat(fixture.service().recover(repaired, fixture.context(), fixture.candidates())).isEqualTo(repaired);
        org.mockito.Mockito.verifyNoInteractions(fixture.routes());
    }

    @Test
    @DisplayName("식당 후보 최대 3곳의 경로 확인 상한")
    void limitsRestaurantRecoveryToThreeCandidates() throws Exception {

        Fixture fixture = fixture();
        ObjectMapper mapper = new ObjectMapper();
        for (int id = 910003; id <= 910005; id++) {
            fixture.candidates().record(mapper.readValue("""
                    {"contentid":"%s","contenttypeid":"39","title":"식당 %s",
                    "addr1":"서울 종로구","mapx":"126.98","mapy":"37.57"}
                    """.formatted(id, id), Kor2KeywordSearchResponse.Item.class));
            fixture.candidates().recordRestaurantDetail("tour:" + id, mapper.readValue("""
                    {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{
                    "contentid":"%s","contenttypeid":"39","firstmenu":"불고기","treatmenu":"불고기"}]}}}}
                    """.formatted(id), Kor2RestaurantIntroResponse.class));
        }
        when(fixture.routes().lookup(anyString(), any(), any(), any()))
                .thenReturn(new TravelMinutesResolver.SegmentMinutes(Optional.of(90), null));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                fixture.input(), fixture.context(), fixture.candidates()
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class);
        org.mockito.Mockito.verify(fixture.routes(), org.mockito.Mockito.times(6))
                .lookup(anyString(), any(), any(), any());
    }

    @Test
    @DisplayName("앞 장소 종료만으로 등록 점심 창을 넘는 일정 거부")
    void rejectsLunchWhenPreviousPlaceAlreadyExceedsWindow() throws Exception {

        Fixture fixture = fixture();
        var latePrevious = slot(ScheduleType.ACTIVITY, CourseType.ATTRACTION, "앞 관광지", 12, 0, 13, 0, null);
        var input = new CreatePlanAiResponse(List.of(new CreatePlanAiResponse.PlanDayDetail(
                1, LocalDate.of(2026, 10, 15), List.of(latePrevious,
                        fixture.input().planDays().getFirst().schedules().get(1), fixture.next())
        )));
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> fixture.service().recover(
                input, fixture.context(), fixture.candidates()
        )).isInstanceOf(com.planb.global.config.exception.domain.BaseException.class)
                .hasMessageContaining("이번에 확인한 식당 후보");
        assertThat(input.planDays().getFirst().schedules().getFirst().endTime()).isEqualTo(LocalTime.of(13, 0));
    }

    private Fixture fixture() throws Exception {

        PlanPlaceResolver resolver = new PlanPlaceResolver();
        TravelMinutesResolver routes = mock(TravelMinutesResolver.class);
        when(routes.lookup(anyString(), any(), any(), any()))
                .thenReturn(new TravelMinutesResolver.SegmentMinutes(Optional.of(10), null));
        MealWindowRecoveryService service = new MealWindowRecoveryService(
                resolver,
                new ScheduleNormalizer(resolver),
                routes
        );
        ObjectMapper mapper = new ObjectMapper();
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        candidates.record(mapper.readValue("""
                {"contentid":"910002","contenttypeid":"39","title":"가까운 식당",
                "addr1":"서울 종로구","mapx":"126.98","mapy":"37.57"}
                """, Kor2KeywordSearchResponse.Item.class));
        candidates.recordRestaurantDetail("tour:910002", mapper.readValue("""
                {"response":{"header":{"resultCode":"0000"},"body":{"items":{"item":[{"contentid":"910002",
                "contenttypeid":"39","firstmenu":"불고기","treatmenu":"불고기"}]}}}}
                """, Kor2RestaurantIntroResponse.class));
        CreateTravelRequest request = mock(CreateTravelRequest.class);
        when(request.transportation()).thenReturn(Transportation.CAR);
        TravelPlanContext context = new TravelPlanContext(request, List.of(new TravelHealthContext(
                "여행자",
                List.of(),
                WalkType.MINIMAL,
                new TravelHealthContext.MealInfoContext(null, LocalTime.NOON, null),
                List.of(),
                List.of()
        )));
        var previous = slot(ScheduleType.ACTIVITY, CourseType.ATTRACTION, "앞 관광지", 10, 50, 11, 50, null);
        var lunch = slot(ScheduleType.LUNCH, CourseType.RESTAURANT, "먼 식당", 12, 45, 13, 45,
                new CreatePlanAiResponse.RestaurantDetail(
                        "비빔밥",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ));
        var next = slot(ScheduleType.ACTIVITY, CourseType.ATTRACTION, "뒤 관광지", 13, 50, 14, 50, null);
        var input = new CreatePlanAiResponse(List.of(new CreatePlanAiResponse.PlanDayDetail(
                1,
                LocalDate.of(2026, 10, 15),
                List.of(previous, lunch, next)
        )));

        return new Fixture(
                service,
                routes,
                candidates,
                context,
                input,
                previous,
                next
        );
    }

    private record Fixture(
            MealWindowRecoveryService service,
            TravelMinutesResolver routes,
            PlaceCandidateContext candidates,
            TravelPlanContext context,
            CreatePlanAiResponse input,
            CreatePlanAiResponse.PlanScheduleDetail previous,
            CreatePlanAiResponse.PlanScheduleDetail next
    ) { }

    private CreatePlanAiResponse.PlanScheduleDetail slot(
            ScheduleType type,
            CourseType course,
            String name,
            int startHour,
            int startMinute,
            int endHour,
            int endMinute,
            CreatePlanAiResponse.RestaurantDetail restaurant
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                type,
                course,
                LocalTime.of(startHour, startMinute),
                LocalTime.of(endHour, endMinute),
                name,
                "서울 종로구",
                "126.97",
                "37.56",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                restaurant,
                null
        );
    }
}
