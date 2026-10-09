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
}
