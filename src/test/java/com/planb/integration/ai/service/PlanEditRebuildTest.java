package com.planb.integration.ai.service;

import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.service.PlanService;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;


class PlanEditRebuildTest extends IntegrationTest {
    @Autowired
    private PlanService planService;

    @Autowired
    private KakaoMapServiceHandler kakaoMapServiceHandler;

    @Test
    @DisplayName("실제 OpenAI 편집 - 1일차 재구성 및 2일차 전체 필드 보존")
    void rebuildsFirstDayAndPreservesSecondDay() {

        LocalDate date = LocalDate.now().plusDays(7);

        GetAiPlanResponse.PlanScheduleDetail first = existingPlace("부산 해운대해수욕장");

        GetAiPlanResponse.PlanScheduleDetail second = existingPlace("부산 송정해수욕장");

        GetAiPlanResponse original = new GetAiPlanResponse(
                "부산 재구성 테스트",
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List.of(),
                List.of(),
                Set.of(),
                List.of(
                new GetAiPlanResponse.PlanDayDetail(1, date, List.of(first)),
                new GetAiPlanResponse.PlanDayDetail(2, date.plusDays(1), List.of(second)))
        );

        CreateTravelRequest request = new CreateTravelRequest(
                "부산 재구성 테스트",
                "부산",
                "해운대구",
                date,
                DateType.ONE_NIGHT_TWO_DAYS,
                Transportation.CAR,
                first.locationName(),
                List.of(),
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List.of(),
                List.of()
        );

        EditPlanAiResponse result = planService.makeEditPlanByAi(new PlanEditContext(
                request,
                List.of(),
                original,
                "1일차 일정을 관광지 위주로 통째로 다시 짜주세요. 2일차는 그대로 유지해주세요."
        ));

        assertThat(result.processable()).isTrue();

        assertThat(result.planDays()).hasSize(2);

        assertThat(result.planDays().getFirst().schedules())
                .filteredOn(slot -> slot.courseType() == CourseType.ATTRACTION
                        || slot.courseType() == CourseType.PARK_WALK || slot.courseType() == CourseType.MUST_HAVE)
                .anySatisfy(slot -> {
                    assertThat(slot.locationName()).isNotEqualTo(first.locationName());

                    assertThat(slot.candidateId()).isNotBlank();
                });

        assertThat(result.planDays().get(1)).usingRecursiveComparison()
                .ignoringFields("schedules.candidateId")
                .isEqualTo(original.planDays().get(1));

        System.out.println("재구성 결과: " + result);
    }

    // AI 최초 생성 없이 실제 검색 좌표를 사용하는 고정 장소 일정
    private GetAiPlanResponse.PlanScheduleDetail existingPlace(String keyword) {

        KakaoPlaceSearchResponse response = kakaoMapServiceHandler.searchPlace(keyword).block();

        assertThat(response).isNotNull();

        assertThat(response.documents()).isNotEmpty();

        KakaoPlaceSearchResponse.Document place = response.documents().getFirst();

        assertThat(place.category_group_code()).isEqualTo("AT4");

        String address = place.road_address_name() == null || place.road_address_name().isBlank()
                ? place.address_name() : place.road_address_name();

        return new GetAiPlanResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(10, 0),
                LocalTime.of(11, 0),
                place.place_name(),
                address,
                place.x(),
                place.y(),
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                null
        );
    }
}
