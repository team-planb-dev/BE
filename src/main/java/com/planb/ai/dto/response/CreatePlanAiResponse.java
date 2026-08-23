package com.planb.ai.dto.response;

import com.planb.domain.travel.entity.constant.PlaceType;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record CreatePlanAiResponse(
        String planName,
        String description,
        List<PlanDayDetail> planDays
) {

    public record PlanDayDetail(
            Integer dayNumber,
            LocalDate date,
            List<PlanScheduleDetail> schedules
    ) {
    }

    public record PlanScheduleDetail(
            ScheduleType scheduleType,
            LocalTime startTime,
            LocalTime endTime,
            String locationName,
            String location,
            PlaceType placeType,
            Integer stayMinutes,
            Integer travelMinutes,
            List<String> cautions,
            MedicationSchedule medication
    ) {
    }

    public record MedicationSchedule(
            Integer intervalMinutes,
            String description
    ) {
    }
}