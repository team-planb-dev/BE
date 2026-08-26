package com.planb.ai.dto.response;

import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

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
            CourseType courseType,
            LocalTime startTime,
            LocalTime endTime,
            String locationName,
            String location,
            String imageUrl,
            String thumbNailImageUrl,
            Integer stayMinutes,
            Integer travelMinutes,
            Set<RecommendationTag> tags,
            MedicationSchedule medication,
            RestaurantDetail restaurantDetail
    ) {
    }

    public record MedicationSchedule(
            Integer intervalMinutes,
            String description
    ) {
    }

    public record RestaurantDetail(
            String menuName,
            Double carbohydrate,
            Double sodium,
            Double fat,
            String openTime,
            String address,
            String longitude,
            String latitude,
            String imageUrl
    ) {
    }
}