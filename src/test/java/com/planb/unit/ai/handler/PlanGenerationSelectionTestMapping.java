package com.planb.unit.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.CreatePlanSelection;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.handler.PlanGenerationSelectionMapper;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class PlanGenerationSelectionTestMapping {

    @Test
    @DisplayName("검색하지 않은 candidateId는 장소 사실값 없이 최종 검증에서 거부")
    void rejectsUnknownCandidateId() {

        CreatePlanSelection selection = new CreatePlanSelection(List.of(
                new CreatePlanSelection.PlanDaySelection(
                        1,
                        LocalDate.of(2030, 1, 1),
                        List.of(new CreatePlanSelection.ScheduleSelection(
                                ScheduleType.ACTIVITY,
                                CourseType.ATTRACTION,
                                LocalTime.of(9, 0),
                                LocalTime.of(10, 0),
                                60,
                                Set.of(),
                                null,
                                "tour:unknown"
                        ))
                )
        ));

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        CreatePlanAiResponse.PlanScheduleDetail slot = new PlanGenerationSelectionMapper()
                .toResponse(selection, candidates)
                .planDays()
                .getFirst()
                .schedules()
                .getFirst();

        assertThat(slot.locationName()).isNull();
        assertThat(slot.longitude()).isNull();

        PlanPlaceResolver.Validation validation = new PlanPlaceResolver()
                .validate(
                        slot,
                        candidates,
                        Set.of(),
                        Set.of()
                );

        assertThat(validation.reason()).contains("검색하지 않은 candidateId");
    }

    @Test
    @DisplayName("선택한 candidateId의 장소 정보만 생성 일정에 반영")
    void mapsSelectedCandidateFromOriginalSearchResult() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new Kor2KeywordSearchResponse.Item(
                "강원특별자치도 춘천시",
                "중앙로 1",
                null,
                "123",
                "12",
                null,
                "https://example.test/place.jpg",
                "https://example.test/thumb.jpg",
                null,
                "127.7",
                "37.8",
                null,
                null,
                null,
                "춘천 명소",
                null,
                null,
                null,
                null,
                null
        ));

        CreatePlanSelection selection = new CreatePlanSelection(List.of(
                new CreatePlanSelection.PlanDaySelection(
                        1,
                        LocalDate.of(2030, 1, 1),
                        List.of(new CreatePlanSelection.ScheduleSelection(
                                ScheduleType.ACTIVITY,
                                CourseType.ATTRACTION,
                                LocalTime.of(9, 0),
                                LocalTime.of(10, 0),
                                60,
                                Set.of(),
                                null,
                                "tour:123"
                        ))
                )
        ));

        CreatePlanAiResponse result = new PlanGenerationSelectionMapper()
                .toResponse(selection, candidates);

        CreatePlanAiResponse.PlanScheduleDetail slot = result
                .planDays()
                .getFirst()
                .schedules()
                .getFirst();

        assertThat(slot.candidateId()).isEqualTo("tour:123");
        assertThat(slot.locationName()).isEqualTo("춘천 명소");
        assertThat(slot.location()).isEqualTo("강원특별자치도 춘천시 중앙로 1");
        assertThat(slot.longitude()).isEqualTo("127.7");
        assertThat(slot.latitude()).isEqualTo("37.8");
        assertThat(slot.imageUrl()).isEqualTo("https://example.test/place.jpg");
        assertThat(slot.thumbNailImageUrl()).isEqualTo("https://example.test/thumb.jpg");
    }
    @ParameterizedTest
    @EnumSource(value = CourseType.class, names = {"ATTRACTION", "RESTAURANT"})
    @DisplayName("폐기 슬롯의 표준명과 최종 식사 표준명의 분리")
    void bindsStandardNameToSelectedPlaceAndMenu(CourseType discardedType) {

        PlaceCandidateContext candidates = new PlaceCandidateContext();
        candidates.record(new PlaceCandidateContext.Candidate(
                "tour:meal",
                "39",
                null,
                null,
                "확정 식당",
                "춘천시 중앙로",
                "127.7",
                "37.8",
                null,
                null
        ));
        candidates.record(new PlaceCandidateContext.Candidate(
                "tour:discarded",
                discardedType == CourseType.ATTRACTION ? "12" : "39",
                null,
                null,
                "폐기 장소",
                "춘천시 중앙로",
                "127.7",
                "37.8",
                null,
                null
        ));
        CreatePlanSelection selection = new CreatePlanSelection(
                List.of(new CreatePlanSelection.PlanDaySelection(
                        1,
                        LocalDate.of(2030, 1, 1),
                        List.of(
                                mealSelection("tour:meal", CourseType.RESTAURANT, "국수"),
                                mealSelection("tour:discarded", discardedType, "다른 음식"),
                                mealSelection("tour:meal", CourseType.RESTAURANT, "중복 음식")
                        )
                ))
        );

        new PlanGenerationSelectionMapper().toResponse(selection, candidates);

        assertThat(candidates.standardFoodName("tour:meal", "막국수"))
                .isEqualTo("국수");
        assertThat(candidates.standardFoodName("tour:discarded", "막국수"))
                .isEqualTo(discardedType == CourseType.ATTRACTION ? null : "다른 음식");
    }

    private CreatePlanSelection.ScheduleSelection mealSelection(
            String candidateId,
            CourseType courseType,
            String standardFoodName
    ) {

        return new CreatePlanSelection.ScheduleSelection(
                ScheduleType.LUNCH,
                courseType,
                LocalTime.NOON,
                LocalTime.of(13, 0),
                60,
                Set.of(),
                new CreatePlanSelection.RestaurantSelection("막국수", standardFoodName),
                candidateId
        );
    }

}
