package com.planb.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 등록 식사 슬롯의 보충과 필수 검증 기준
 * 첫날 아침·마지막 날 저녁은 필수 검증에서 제외
 */
public final class MealSlotPolicy {

    // 검증과 보충에 공통으로 사용하는 날짜별 식사 목록
    public static final List<ScheduleType> MEAL_SCHEDULE_TYPES = List.of(
            ScheduleType.BREAKFAST,
            ScheduleType.LUNCH,
            ScheduleType.DINNER
    );

    private MealSlotPolicy() {
    }

    /**
     * 하루에 누락된 등록 식사 전체 조회
     * 동행인 미선택 시 빈 목록
     */
    public static List<ScheduleType> missingMeals(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts
    ) {

        if (day == null
                || day.schedules() == null
                || healthContexts == null
                || healthContexts.isEmpty()) {
            return List.of();
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = day
                .schedules()
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.startTime() != null)
                .toList();

        if (schedules.isEmpty()) {
            return List.of();
        }

        List<ScheduleType> missing = new ArrayList<>();

        for (ScheduleType mealType : MEAL_SCHEDULE_TYPES) {
            if (configuredMealTime(mealType, healthContexts) == null) {
                continue;
            }

            boolean hasMealSlot = schedules
                    .stream()
                    .anyMatch(schedule -> schedule.scheduleType() == mealType);

            if (!hasMealSlot) {
                missing.add(mealType);
            }
        }

        return missing;
    }

    /**
     * 첫날 아침·마지막 날 저녁을 제외한 필수 식사 누락
     */
    public static List<ScheduleType> requiredMissingMeals(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts,
            int totalDays
    ) {

        return missingMeals(day, healthContexts)
                .stream()
                .filter(mealType -> !isExempt(
                        mealType,
                        day,
                        totalDays
                ))
                .toList();
    }

    private static boolean isExempt(
            ScheduleType mealType,
            CreatePlanAiResponse.PlanDayDetail day,
            int totalDays
    ) {

        return switch (mealType) {
            case BREAKFAST -> Objects.equals(day.dayNumber(), 1);

            case DINNER -> Objects.equals(day.dayNumber(), totalDays);

            default -> false;
        };
    }

    /**
     * 동행인의 등록 시각 중 가장 이른 식사시각
     */
    public static LocalTime configuredMealTime(
            ScheduleType mealType,
            List<TravelHealthContext> healthContexts
    ) {

        if (healthContexts == null) {
            return null;
        }

        return healthContexts
                .stream()
                .filter(Objects::nonNull)
                .map(TravelHealthContext::mealInfo)
                .map(mealInfo -> configuredMealTime(mealInfo, mealType))
                .filter(Objects::nonNull)
                .min(LocalTime::compareTo)
                .orElse(null);
    }

    private static LocalTime configuredMealTime(
            TravelHealthContext.MealInfoContext mealInfo,
            ScheduleType mealType
    ) {

        if (mealInfo == null || !mealInfo.applied()) {
            return null;
        }

        return switch (mealType) {
            case BREAKFAST -> mealInfo.breakfastApplied()
                    ? mealInfo.breakfastTime()
                    : null;

            case LUNCH -> mealInfo.lunchApplied()
                    ? mealInfo.lunchTime()
                    : null;

            case DINNER -> mealInfo.dinnerApplied()
                    ? mealInfo.dinnerTime()
                    : null;

            default -> null;
        };
    }

}
