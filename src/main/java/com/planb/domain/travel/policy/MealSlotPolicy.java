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
 * 판정이 둘로 갈린다. 채울 대상은 등록된 식사 전부이고, 없으면 거부할 대상은
 * 그중 첫날 아침과 마지막 날 저녁을 뺀 것이다. 첫날은 이동 후 늦게 시작하고
 * 마지막 날은 귀가로 일찍 끝나므로 그 두 끼는 채워지면 좋지만 없어도 내보낸다.
 *
 * 하루의 시작·종료 시각은 보지 않는다. 하루 길이는 슬롯 시각에서 파생될 뿐이고,
 * TouristPlaceCountPolicy.trimExcess가 뒤쪽 관광지를 제거하면서 바뀐다.
 * 그 값을 기준으로 삼으면 관광지가 잘릴 때 사용자가 등록한 식사가 조용히 사라진다.
 */
public final class MealSlotPolicy {

    // 검증도 "그 날에 어떤 식사가 있었는가"를 같은 목록으로 판정한다.
    public static final List<ScheduleType> MEAL_SCHEDULE_TYPES = List.of(
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
     * 하루에서 없으면 일정을 거부해야 하는 식사를 찾는다.
     *
     * 채울 대상에서 첫날 아침과 마지막 날 저녁을 뺀 것이다. 첫날은 이동 후에 시작하고
     * 마지막 날은 귀가로 일찍 끝나므로, 그 두 끼는 만들 수 없는 경우가 정상이다.
     * 면제는 거부에만 적용되고 채우는 쪽은 그대로 시도한다.
     *
     * @param day            검사할 하루
     * @param healthContexts 이번 여행에 선택된 동행인, 없으면 규칙을 적용하지 않는다
     * @param totalDays      이번 여행의 전체 일수
     * @return 없으면 거부해야 하는 식사의 ScheduleType 목록, 이른 식사부터
     */
    public static List<ScheduleType> requiredMissingMeals(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts,
            int totalDays
    ) {

        return missingMeals(day, healthContexts)
                .stream()
                .filter(mealType -> !isExempt(mealType, day, totalDays))
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
