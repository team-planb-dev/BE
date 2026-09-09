package com.planb.unit.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.PlanDay;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.repository.PlanScheduleRepository;
import com.planb.domain.travel.service.PlanScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlanScheduleServiceTest {

    @Mock
    private PlanScheduleRepository planScheduleRepository;

    @InjectMocks
    private PlanScheduleService planScheduleService;

    @Test
    @DisplayName("복약 정보가 포함된 PlanSchedule 객체 리스트 생성")
    void makePlanScheduleListWithMedication() {

        PlanDay planDay =
                PlanDay.builder()
                        .dayNumber(1)
                        .build();

        Set<RecommendationTag> tags =
                Set.of(
                        RecommendationTag.MEAL_TIME_APPLIED,
                        RecommendationTag.LOCAL_FOOD,
                        RecommendationTag.SODIUM_REFERENCE
                );

        CreatePlanAiResponse.MedicationSchedule medication =
                new CreatePlanAiResponse.MedicationSchedule(
                        30,
                        "식후 30분 복약"
                );

        CreatePlanAiResponse.PlanScheduleDetail schedule =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        LocalTime.of(12, 0),
                        LocalTime.of(13, 0),
                        "부산돼지국밥",
                        "부산광역시 부산진구",
                        "129.0756",
                        "35.1795",
                        "image-url",
                        "thumbnail-url",
                        60,
                        20,
                        tags,
                        medication,
                        null
                );

        List<PlanSchedule> result =
                planScheduleService.makePlanScheduleList(
                        planDay,
                        List.of(schedule)
                );

        assertEquals(
                1,
                result.size()
        );

        PlanSchedule planSchedule =
                result.get(0);

        assertSame(
                planDay,
                planSchedule.getPlanDay()
        );

        assertEquals(
                ScheduleType.LUNCH,
                planSchedule.getScheduleType()
        );

        assertEquals(
                CourseType.RESTAURANT,
                planSchedule.getCourseType()
        );

        assertEquals(
                LocalTime.of(12, 0),
                planSchedule.getStartTime()
        );

        assertEquals(
                LocalTime.of(13, 0),
                planSchedule.getEndTime()
        );

        assertEquals(
                "부산돼지국밥",
                planSchedule.getLocationName()
        );

        assertEquals(
                "부산광역시 부산진구",
                planSchedule.getLocation()
        );

        assertEquals(
                "129.0756",
                planSchedule.getLongitude()
        );

        assertEquals(
                "35.1795",
                planSchedule.getLatitude()
        );

        assertEquals(
                "image-url",
                planSchedule.getImageUrl()
        );

        assertEquals(
                "thumbnail-url",
                planSchedule.getThumbNailImageUrl()
        );

        assertEquals(
                60,
                planSchedule.getStayMinutes()
        );

        assertEquals(
                20,
                planSchedule.getTravelMinutes()
        );

        assertEquals(
                tags,
                planSchedule.getTags()
        );

        assertEquals(
                30,
                planSchedule.getMedicationIntervalMinutes()
        );

        assertEquals(
                "식후 30분 복약",
                planSchedule.getMedicationDescription()
        );
    }

    @Test
    @DisplayName("복약 정보가 없는 PlanSchedule 객체 리스트 생성")
    void makePlanScheduleListWithoutMedication() {

        PlanDay planDay =
                PlanDay.builder()
                        .dayNumber(1)
                        .build();

        CreatePlanAiResponse.PlanScheduleDetail schedule =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(14, 0),
                        LocalTime.of(16, 0),
                        "해운대해수욕장",
                        "부산광역시 해운대구",
                        null,
                        null,
                        "image-url",
                        "thumbnail-url",
                        120,
                        30,
                        Set.of(
                                RecommendationTag.NATURAL_SCENERY
                        ),
                        null,
                        null
                );

        List<PlanSchedule> result =
                planScheduleService.makePlanScheduleList(
                        planDay,
                        List.of(schedule)
                );

        assertEquals(
                1,
                result.size()
        );

        PlanSchedule planSchedule =
                result.get(0);

        assertNull(
                planSchedule.getLongitude()
        );

        assertNull(
                planSchedule.getLatitude()
        );

        assertNull(
                planSchedule.getMedicationIntervalMinutes()
        );

        assertNull(
                planSchedule.getMedicationDescription()
        );
    }

    @Test
    @DisplayName("PlanSchedule 객체 리스트 일괄 저장")
    void savePlanScheduleAll() {

        List<PlanSchedule> planScheduleList =
                List.of(
                        PlanSchedule.builder()
                                .scheduleType(
                                        ScheduleType.LUNCH
                                )
                                .courseType(
                                        CourseType.RESTAURANT
                                )
                                .build(),
                        PlanSchedule.builder()
                                .scheduleType(
                                        ScheduleType.ACTIVITY
                                )
                                .courseType(
                                        CourseType.ATTRACTION
                                )
                                .build()
                );

        planScheduleService.savePlanScheduleAll(
                planScheduleList
        );

        verify(planScheduleRepository)
                .saveAll(planScheduleList);
    }

    @Test
    @DisplayName("특정 PlanDay 목록에 속한 PlanSchedule 리스트 조회")
    void findAllByPlanDayIn() {

        PlanDay planDay =
                PlanDay.builder()
                        .dayNumber(1)
                        .build();

        List<PlanSchedule> planSchedules =
                List.of(
                        PlanSchedule.builder()
                                .planDay(planDay)
                                .scheduleType(ScheduleType.LUNCH)
                                .courseType(CourseType.RESTAURANT)
                                .build()
                );

        when(
                planScheduleRepository
                        .findAllByPlanDayIn(
                                List.of(planDay)
                        )
        ).thenReturn(
                planSchedules
        );

        List<PlanSchedule> result =
                planScheduleService.findAllByPlanDayIn(
                        List.of(planDay)
                );

        assertEquals(
                planSchedules,
                result
        );
    }

    @Test
    @DisplayName("특정 PlanDay 목록에 속한 PlanSchedule 리스트 일괄 삭제")
    void deleteAllByPlanDayIn() {

        PlanDay planDay =
                PlanDay.builder()
                        .dayNumber(1)
                        .build();

        planScheduleService.deleteAllByPlanDayIn(
                List.of(planDay)
        );

        verify(planScheduleRepository)
                .deleteAllByPlanDayIn(List.of(planDay));
    }
}