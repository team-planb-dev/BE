package com.planb.ai.dto.response;

import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

// planName 필드 없음: Plan.planName은 생성 시점 travelName 기반 확정, AI 재생성 불필요
public record CreatePlanAiResponse(
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
            String longitude,
            String latitude,
            String imageUrl,
            String thumbNailImageUrl,
            Integer stayMinutes,
            Integer travelMinutes,
            Set<RecommendationTag> tags,
            MedicationSchedule medication,
            RestaurantDetail restaurantDetail,
            String candidateId
    ) {

        public PlanScheduleDetail(ScheduleType scheduleType, CourseType courseType,
                LocalTime startTime, LocalTime endTime, String locationName, String location,
                String longitude, String latitude, String imageUrl, String thumbNailImageUrl,
                Integer stayMinutes, Integer travelMinutes, Set<RecommendationTag> tags,
                MedicationSchedule medication, RestaurantDetail restaurantDetail) {
            this(scheduleType, courseType, startTime, endTime, locationName, location, longitude,
                    latitude, imageUrl, thumbNailImageUrl, stayMinutes, travelMinutes, tags,
                    medication, restaurantDetail, null);
        }
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
