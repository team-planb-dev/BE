package com.planb.ai.dto.response;

import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

public record CreatePlanSelection(
        List<PlanDaySelection> planDays
) {

    public record PlanDaySelection(
            Integer dayNumber,
            LocalDate date,
            List<ScheduleSelection> schedules
    ) {
    }

    public record ScheduleSelection(
            ScheduleType scheduleType,
            CourseType courseType,
            LocalTime startTime,
            LocalTime endTime,
            Integer stayMinutes,
            Set<RecommendationTag> tags,
            RestaurantSelection restaurantDetail,
            String candidateId
    ) {
    }

    public record RestaurantSelection(
            String menuName,
            String standardFoodName
    ) {
    }
}
