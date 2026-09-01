package com.planb.domain.travel.dto.response;

import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record GetAiPlanResponse(
        String planName,
        TravelStyle travelStyle,
        TravelTheme travelTheme,
        List<DiseaseType> diseaseTypes,
        List<LocalTime> medicationTimes,
        List<PlanDayDetail> planDays
) {

    public static GetAiPlanResponse from(
            PlanQueryResponse plan,
            TravelConditionQueryResponse travelCondition,
            List<HealthSummaryQueryResponse> healthSummaries,
            List<LocalTime> medicationTimes,
            List<PlanDayQueryResponse> planDays,
            List<PlanSchedule> planSchedules,
            List<RestaurantDetailQueryResponse> restaurantDetails
    ) {

        Map<Long, List<PlanSchedule>> scheduleMap =
                planSchedules.stream()
                        .collect(
                                Collectors.groupingBy(
                                        schedule ->
                                                schedule.getPlanDay()
                                                        .getId()
                                )
                        );

        Map<Long, RestaurantDetailQueryResponse> restaurantMap =
                restaurantDetails.stream()
                        .collect(
                                Collectors.toMap(
                                        RestaurantDetailQueryResponse::planScheduleId,
                                        Function.identity()
                                )
                        );

        List<DiseaseType> diseaseTypes =
                healthSummaries.stream()
                        .map(
                                HealthSummaryQueryResponse::diseaseType
                        )
                        .filter(
                                Objects::nonNull
                        )
                        .distinct()
                        .toList();

        List<PlanDayDetail> planDayDetails =
                planDays.stream()
                        .map(planDay ->
                                PlanDayDetail.from(
                                        planDay,
                                        scheduleMap.getOrDefault(
                                                planDay.planDayId(),
                                                List.of()
                                        ),
                                        restaurantMap
                                )
                        )
                        .toList();

        return new GetAiPlanResponse(
                plan.planName(),
                travelCondition.travelStyle(),
                travelCondition.travelTheme(),
                diseaseTypes,
                medicationTimes,
                planDayDetails
        );
    }

    public record PlanDayDetail(
            Integer dayNumber,
            LocalDate date,
            List<PlanScheduleDetail> schedules
    ) {

        public static PlanDayDetail from(
                PlanDayQueryResponse planDay,
                List<PlanSchedule> planSchedules,
                Map<Long, RestaurantDetailQueryResponse> restaurantMap
        ) {

            List<PlanScheduleDetail> scheduleDetails =
                    planSchedules.stream()
                            .map(schedule ->
                                    PlanScheduleDetail.from(
                                            schedule,
                                            restaurantMap.get(
                                                    schedule.getId()
                                            )
                                    )
                            )
                            .toList();

            return new PlanDayDetail(
                    planDay.dayNumber(),
                    planDay.localdate(),
                    scheduleDetails
            );
        }
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
            RestaurantDetail restaurantDetail
    ) {

        public static PlanScheduleDetail from(
                PlanSchedule planSchedule,
                RestaurantDetailQueryResponse restaurant
        ) {

            return new PlanScheduleDetail(
                    planSchedule.getScheduleType(),
                    planSchedule.getCourseType(),
                    planSchedule.getStartTime(),
                    planSchedule.getEndTime(),
                    planSchedule.getLocationName(),
                    planSchedule.getLocation(),
                    planSchedule.getLongitude(),
                    planSchedule.getLatitude(),
                    planSchedule.getImageUrl(),
                    planSchedule.getThumbNailImageUrl(),
                    planSchedule.getStayMinutes(),
                    planSchedule.getTravelMinutes(),
                    planSchedule.getTags(),
                    MedicationSchedule.from(
                            planSchedule
                    ),
                    RestaurantDetail.from(
                            restaurant
                    )
            );
        }
    }

    public record MedicationSchedule(
            Integer intervalMinutes,
            String description
    ) {

        public static MedicationSchedule from(
                PlanSchedule planSchedule
        ) {

            if (planSchedule.getMedicationIntervalMinutes() == null
                    && planSchedule.getMedicationDescription() == null) {

                return null;
            }

            return new MedicationSchedule(
                    planSchedule.getMedicationIntervalMinutes(),
                    planSchedule.getMedicationDescription()
            );
        }
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

        public static RestaurantDetail from(
                RestaurantDetailQueryResponse restaurant
        ) {

            if (restaurant == null) {
                return null;
            }

            return new RestaurantDetail(
                    restaurant.menuName(),
                    restaurant.carbohydrate(),
                    restaurant.sodium(),
                    restaurant.fat(),
                    restaurant.openTime(),
                    restaurant.address(),
                    restaurant.longitude(),
                    restaurant.latitude(),
                    restaurant.imageUrl()
            );
        }
    }
}