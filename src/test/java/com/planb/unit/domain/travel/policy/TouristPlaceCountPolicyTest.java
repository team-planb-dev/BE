package com.planb.unit.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TouristPlaceCountPolicyTest {

    @Test
    @DisplayName("동행인이 없는 여행의 관광지 개수 규칙 미적용")
    void noCompanionMeansNoRule() {

        assertThat(TouristPlaceCountPolicy.expectedCount(List.of()))
                .isZero();

        assertThat(TouristPlaceCountPolicy.expectedCount(null))
                .isZero();
    }

    @Test
    @DisplayName("MINIMAL 여행자가 없는 경우 하루 관광지 3개")
    void withoutMinimalTravelerExpectsThree() {

        assertThat(TouristPlaceCountPolicy.expectedCount(List.of(
                healthContext(WalkType.MODERATE),
                healthContext(WalkType.ACTIVE))))
                .isEqualTo(3);
    }

    @Test
    @DisplayName("MINIMAL 여행자가 한 명이라도 있는 경우 하루 관광지 2개")
    void anyMinimalTravelerExpectsTwo() {

        assertThat(TouristPlaceCountPolicy.expectedCount(List.of(
                healthContext(WalkType.ACTIVE),
                healthContext(WalkType.MINIMAL))))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("밀도 감소 대상인 일반 여행자의 관광 장소 최소 개수 2개")
    void densityReductionLowersMinimumCountToTwo() {

        List<TravelHealthContext> healthContexts = List.of(
                healthContext(WalkType.MODERATE)
        );

        assertThat(TouristPlaceCountPolicy.minimumCount(
                healthContexts,
                false
        ))
                .isEqualTo(3);

        assertThat(TouristPlaceCountPolicy.minimumCount(
                healthContexts,
                true
        ))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("밀도 감소 대상인 MINIMAL 여행자의 관광 장소 최소 개수 2개 유지")
    void densityReductionKeepsMinimalCountAtTwo() {

        assertThat(TouristPlaceCountPolicy.minimumCount(
                List.of(healthContext(WalkType.MINIMAL)),
                true
        ))
                .isEqualTo(2);
    }

    @Test
    @DisplayName("관광 장소 초과분을 뒤에서부터 제거해 기준 개수에 맞춤")
    void trimsExcessTouristPlacesFromTail() {

        CreatePlanAiResponse trimmed = TouristPlaceCountPolicy.trimExcess(
                response(day(
                        attraction("가"),
                        attraction("나"),
                        attraction("다"),
                        attraction("라"))),
                List.of(healthContext(WalkType.MODERATE)));

        assertThat(locationNames(trimmed))
                .containsExactly("가", "나", "다");
    }

    @Test
    @DisplayName("초과분 제거 대상에서 사용자가 지정한 MUST_HAVE 슬롯 제외")
    void keepsMustHaveWhileTrimming() {

        CreatePlanAiResponse trimmed = TouristPlaceCountPolicy.trimExcess(
                response(day(
                        attraction("가"),
                        attraction("나"),
                        attraction("다"),
                        mustHave("라"))),
                List.of(healthContext(WalkType.MODERATE)));

        assertThat(locationNames(trimmed))
                .containsExactly("가", "나", "라");
    }

    @Test
    @DisplayName("관광 장소가 기준 개수 이하이면 그대로 유지")
    void keepsScheduleWhenNotExcessive() {

        CreatePlanAiResponse response = response(day(
                attraction("가"),
                attraction("나")));

        assertThat(locationNames(TouristPlaceCountPolicy.trimExcess(
                response,
                List.of(healthContext(WalkType.MODERATE)))))
                .containsExactly("가", "나");
    }

    @Test
    @DisplayName("동행인이 없으면 관광 장소 개수를 건드리지 않음")
    void keepsScheduleWithoutCompanion() {

        CreatePlanAiResponse response = response(day(
                attraction("가"),
                attraction("나"),
                attraction("다"),
                attraction("라")));

        assertThat(locationNames(TouristPlaceCountPolicy.trimExcess(
                response,
                List.of())))
                .containsExactly("가", "나", "다", "라");
    }

    @Test
    @DisplayName("MUST_HAVE만 초과하면 제거할 대상이 없어 그대로 유지")
    void keepsScheduleWhenOnlyMustHaveExceeds() {

        CreatePlanAiResponse response = response(day(
                mustHave("가"),
                mustHave("나"),
                mustHave("다"),
                mustHave("라")));

        assertThat(locationNames(TouristPlaceCountPolicy.trimExcess(
                response,
                List.of(healthContext(WalkType.MODERATE)))))
                .containsExactly("가", "나", "다", "라");
    }

    private List<String> locationNames(CreatePlanAiResponse response) {

        return response
                .planDays()
                .stream()
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
    }

    private CreatePlanAiResponse response(CreatePlanAiResponse.PlanDayDetail day) {

        return new CreatePlanAiResponse(List.of(day));
    }

    private CreatePlanAiResponse.PlanDayDetail day(
            CreatePlanAiResponse.PlanScheduleDetail... schedules
    ) {

        return new CreatePlanAiResponse.PlanDayDetail(
                1,
                LocalDate.of(2026, 9, 20),
                List.of(schedules)
        );
    }

    private CreatePlanAiResponse.PlanScheduleDetail attraction(String locationName) {

        return slot(CourseType.ATTRACTION, locationName);
    }

    private CreatePlanAiResponse.PlanScheduleDetail mustHave(String locationName) {

        return slot(CourseType.MUST_HAVE, locationName);
    }

    private CreatePlanAiResponse.PlanScheduleDetail slot(
            CourseType courseType,
            String locationName
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                courseType,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                locationName,
                "주소",
                "127.0",
                "37.0",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                null,
                null
        );
    }

    private TravelHealthContext healthContext(WalkType walkType) {

        return new TravelHealthContext(
                "동행인",
                List.of(DiseaseType.DIABETES),
                walkType,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );
    }
}
