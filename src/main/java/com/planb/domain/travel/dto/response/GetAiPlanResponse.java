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
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record GetAiPlanResponse(
        @Schema(description = "여행 일정 이름")
        String planName,
        @Schema(description = "일정에 적용된 여행 스타일")
        TravelStyle travelStyle,
        @Schema(description = "일정에 적용된 여행 테마")
        TravelTheme travelTheme,
        @Schema(description = "선택한 동행인의 질환 유형 목록입니다. 공유 조회에서는 빈 목록입니다.")
        List<DiseaseType> diseaseTypes,
        @Schema(description = "선택한 동행인의 복약 시각 목록입니다. 공유 조회에서는 빈 목록입니다.")
        List<LocalTime> medicationTimes,
        @Schema(description = "전체 일정에 적용된 추천 근거 태그")
        Set<RecommendationTag> tags,
        @Schema(description = "날짜별 확정 일정")
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
                plan.tags(),
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
            @Schema(description = "식사, 활동, 체크인 또는 체크아웃 등 일정 유형", example = "ACTIVITY")
            ScheduleType scheduleType,
            @Schema(description = "관광지, 식당, 복약 또는 이동 등 세부 코스 유형", example = "ATTRACTION")
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
            @Schema(description = "이전 장소에서 현재 장소까지의 이동 시간(분)", example = "20")
            Integer travelMinutes,
            @Schema(description = "이 일정을 추천한 근거 태그")
            Set<RecommendationTag> tags,
            @Schema(description = "복약 일정 상세입니다. 복약 일정이 아니면 null입니다.")
            MedicationSchedule medication,
            @Schema(description = "식당 상세입니다. 식당 일정이 아니면 null입니다.")
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
            @Schema(description = "기준 식사와 복약 사이의 간격(분)", example = "30")
            Integer intervalMinutes,
            @Schema(description = "복약 대상과 시점 안내")
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
