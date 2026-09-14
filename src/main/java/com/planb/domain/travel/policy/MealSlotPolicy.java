package com.planb.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.entity.constant.ScheduleType;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 하루에 있어야 하는 식사 슬롯 규칙.
 *
 * 등록 식사시각을 지나는 하루에는 그 식사 슬롯이 있어야 한다.
 * 하루가 그 시각까지 가지 않으면 요구하지 않는다. 만들 수 없는 일정을 요구하면
 * 재시도가 끝없이 실패하기 때문이다.
 */
public final class MealSlotPolicy {

    private static final List<ScheduleType> MEAL_SCHEDULE_TYPES = List.of(
            ScheduleType.BREAKFAST,
            ScheduleType.LUNCH,
            ScheduleType.DINNER
    );

    private MealSlotPolicy() {
    }

    /**
     * 하루에서 빠진 식사 슬롯을 찾는다.
     *
     * @param day           검사할 하루
     * @param healthContexts 이번 여행에 선택된 동행인, 없으면 규칙을 적용하지 않는다
     * @return 빠진 식사의 ScheduleType 목록, 이른 식사부터
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

        LocalTime dayStart = schedules
                .stream()
                .map(CreatePlanAiResponse.PlanScheduleDetail::startTime)
                .min(LocalTime::compareTo)
                .orElseThrow();

        LocalTime dayEnd = schedules
                .stream()
                .map(MealSlotPolicy::endOf)
                .max(LocalTime::compareTo)
                .orElseThrow();

        List<ScheduleType> missing = new ArrayList<>();

        for (ScheduleType mealType : MEAL_SCHEDULE_TYPES) {
            if (!spansConfiguredMeal(mealType, healthContexts, dayStart, dayEnd)) {
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
     * 동행인이 등록한 식사시각.
     *
     * 여러 명이 서로 다른 시각을 등록했으면 가장 이른 시각을 쓴다.
     * 늦은 쪽에 맞추면 이른 사람의 식사가 등록 시각을 지나버린다.
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

    // 등록 식사시각 중 하나라도 하루 시간대 안에 들어오면 그 식사를 요구한다.
    private static boolean spansConfiguredMeal(
            ScheduleType mealType,
            List<TravelHealthContext> healthContexts,
            LocalTime dayStart,
            LocalTime dayEnd
    ) {

        return healthContexts
                .stream()
                .filter(Objects::nonNull)
                .map(TravelHealthContext::mealInfo)
                .map(mealInfo -> configuredMealTime(mealInfo, mealType))
                .filter(Objects::nonNull)
                .anyMatch(mealTime -> !mealTime.isBefore(dayStart)
                        && !mealTime.isAfter(dayEnd));
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

    private static LocalTime endOf(
            CreatePlanAiResponse.PlanScheduleDetail schedule
    ) {

        return schedule.endTime() == null
                ? schedule.startTime()
                : schedule.endTime();
    }
}
