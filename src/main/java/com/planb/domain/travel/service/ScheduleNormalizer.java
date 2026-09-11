package com.planb.domain.travel.service;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 확정된 장소 위에서 시간, 식사, 복약을 결정적으로 계산하는 단계.
 *
 * AI 응답 여부와 무관하게 같은 입력이면 같은 결과를 돌려주며,
 * 같은 일정에 여러 번 적용해도 결과가 달라지지 않아야 한다.
 * 이동시간이 확정된 뒤 시간표를 다시 맞추기 위해 호출이 반복되기 때문이다.
 */
@Component
@RequiredArgsConstructor
public class ScheduleNormalizer {

    // 복약 일정 계산 시 하루 중 식사 슬롯을 찾기 위한 scheduleType 집합
    private static final Set<ScheduleType> MEAL_SCHEDULE_TYPES = Set.of(
            ScheduleType.BREAKFAST,
            ScheduleType.LUNCH,
            ScheduleType.DINNER
    );

    private static final long MEAL_TIME_TOLERANCE_MINUTES = 30;

    private final PlanPlaceResolver planPlaceResolver;

    public CreatePlanAiResponse normalizeScheduleTimes(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        return new CreatePlanAiResponse(
                response
                        .planDays()
                        .stream()
                        .map(day -> normalizeScheduleTimes(day, healthContexts))
                        .toList());
    }

    private CreatePlanAiResponse.PlanDayDetail normalizeScheduleTimes(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

        LocalTime previousPlaceEnd = null;
        LocalTime previousMealEnd = null;
        int movableStartIndex = 0;

        for (CreatePlanAiResponse.PlanScheduleDetail schedule : day.schedules()) {
            if (schedule == null || schedule.startTime() == null || schedule.stayMinutes() == null
                    || schedule.stayMinutes() <= 0 || !planPlaceResolver.requiresPlace(schedule)) {
                schedules.add(schedule);

                continue;
            }

            LocalTime startTime = schedule.startTime();

            MealTimeRange mealTimeRange = mealTimeRange(
                    schedule.scheduleType(),
                    healthContexts
            );

            if (previousPlaceEnd != null && schedule.travelMinutes() != null
                    && schedule.travelMinutes() >= 0) {

                LocalTime earliestStart = previousPlaceEnd
                        .plusMinutes(schedule.travelMinutes());

                if (mealTimeRange != null && earliestStart.isAfter(mealTimeRange.latest())) {
                    long shiftMinutes = Duration
                            .between(
                                    mealTimeRange.latest(),
                                    earliestStart
                            )
                            .toMinutes();

                    shiftPreviousPlaces(
                            schedules,
                            movableStartIndex,
                            shiftMinutes,
                            previousMealEnd
                    );

                    previousPlaceEnd = previousPlaceEnd.minusMinutes(shiftMinutes);
                    earliestStart = previousPlaceEnd.plusMinutes(schedule.travelMinutes());
                }

                if (earliestStart.isAfter(startTime)) {
                    startTime = earliestStart;
                }
            }

            if (mealTimeRange != null) {
                if (startTime.isBefore(mealTimeRange.earliest())) {
                    startTime = mealTimeRange.earliest();
                }

                if (startTime.isAfter(mealTimeRange.latest())) {
                    startTime = mealTimeRange.latest();
                }
            }

            LocalTime endTime = startTime
                    .plusMinutes(schedule.stayMinutes());

            schedules.add(withScheduleTimes(
                    schedule,
                    startTime,
                    endTime));

            previousPlaceEnd = endTime;

            if (mealTimeRange != null) {
                previousMealEnd = endTime;
                movableStartIndex = schedules.size();
            }
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                schedules);
    }

    private MealTimeRange mealTimeRange(
            ScheduleType scheduleType,
            List<TravelHealthContext> healthContexts
    ) {

        if (!MEAL_SCHEDULE_TYPES.contains(scheduleType)) {
            return null;
        }

        List<LocalTime> configuredTimes = healthContexts
                .stream()
                .map(TravelHealthContext::mealInfo)
                .map(mealInfo -> configuredMealTime(mealInfo, scheduleType))
                .filter(Objects::nonNull)
                .toList();

        if (configuredTimes.isEmpty()) {
            return null;
        }

        LocalTime earliest = configuredTimes
                .stream()
                .map(time -> time.minusMinutes(MEAL_TIME_TOLERANCE_MINUTES))
                .max(LocalTime::compareTo)
                .orElseThrow();

        LocalTime latest = configuredTimes
                .stream()
                .map(time -> time.plusMinutes(MEAL_TIME_TOLERANCE_MINUTES))
                .min(LocalTime::compareTo)
                .orElseThrow();

        if (earliest.isAfter(latest)) {
            throw invalidPlace("여행자 식사시간 허용 범위 불일치");
        }

        return new MealTimeRange(
                earliest,
                latest
        );
    }

    private void shiftPreviousPlaces(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            int fromIndex,
            long shiftMinutes,
            LocalTime previousMealEnd
    ) {

        int firstPlaceIndex = -1;

        for (int index = fromIndex; index < schedules.size(); index++) {
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            if (schedule != null && planPlaceResolver.requiresPlace(schedule)) {
                firstPlaceIndex = index;
                break;
            }
        }

        if (firstPlaceIndex < 0) {
            throw invalidPlace(
                    "식사시간을 만족할 수 없는 일정: 앞당길 장소가 없음"
                            + " / fromIndex=" + fromIndex
                            + " / shiftMinutes=" + shiftMinutes
                            + " / scheduleSize=" + schedules.size()
            );
        }

        CreatePlanAiResponse.PlanScheduleDetail firstPlace = schedules.get(firstPlaceIndex);

        LocalTime shiftedFirstStart = firstPlace.startTime().minusMinutes(shiftMinutes);

        if (shiftedFirstStart.isAfter(firstPlace.startTime())) {
            throw invalidPlace(
                    "식사시간을 만족할 수 없는 일정: 앞당긴 시작시간이 원래 시작시간보다 늦음"
                            + " / locationName=" + firstPlace.locationName()
                            + " / originalStart=" + firstPlace.startTime()
                            + " / shiftedStart=" + shiftedFirstStart
                            + " / shiftMinutes=" + shiftMinutes
            );
        }

        if (previousMealEnd != null) {
            LocalTime earliestStart = previousMealEnd.plusMinutes(
                    firstPlace.travelMinutes() == null
                            ? 0
                            : firstPlace.travelMinutes()
            );

            if (shiftedFirstStart.isBefore(earliestStart)) {
                throw invalidPlace(
                        "식사시간을 만족할 수 없는 일정: 이전 식사 종료 시각 이전으로 앞당겨짐"
                                + " / locationName=" + firstPlace.locationName()
                                + " / shiftedStart=" + shiftedFirstStart
                                + " / earliestStart=" + earliestStart
                                + " / previousMealEnd=" + previousMealEnd
                                + " / travelMinutes=" + firstPlace.travelMinutes()
                                + " / shiftMinutes=" + shiftMinutes
                );
            }
        }

        for (int index = firstPlaceIndex; index < schedules.size(); index++) {
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            if (schedule == null || !planPlaceResolver.requiresPlace(schedule)) {
                continue;
            }

            schedules.set(
                    index,
                    withScheduleTimes(
                            schedule,
                            schedule.startTime().minusMinutes(shiftMinutes),
                            schedule.endTime().minusMinutes(shiftMinutes)
                    )
            );
        }
    }

    private record MealTimeRange(
            LocalTime earliest,
            LocalTime latest
    ) {
    }

    private CreatePlanAiResponse.PlanScheduleDetail withScheduleTimes(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            LocalTime startTime,
            LocalTime endTime
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                startTime,
                endTime,
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                schedule.travelMinutes(),
                schedule.tags(),
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId());
    }

    public void validateMealTimes(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return;
        }

        for (CreatePlanAiResponse.PlanDayDetail planDay : response.planDays()) {
            if (planDay == null || planDay.schedules() == null) {
                continue;
            }

            for (CreatePlanAiResponse.PlanScheduleDetail schedule : planDay.schedules()) {
                if (schedule == null || schedule.startTime() == null
                        || !MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType())) {
                    continue;
                }

                for (TravelHealthContext healthContext : healthContexts) {
                    LocalTime configuredMealTime = configuredMealTime(
                            healthContext.mealInfo(),
                            schedule.scheduleType()
                    );

                    if (configuredMealTime == null) {
                        continue;
                    }

                    long difference = Math.abs(
                            Duration.between(
                                    configuredMealTime,
                                    schedule.startTime()
                            ).toMinutes()
                    );

                    if (difference > MEAL_TIME_TOLERANCE_MINUTES) {
                        throw invalidPlace("식사시간 허용 범위 초과");
                    }
                }
            }
        }
    }

    private LocalTime configuredMealTime(
            TravelHealthContext.MealInfoContext mealInfo,
            ScheduleType scheduleType
    ) {

        if (mealInfo == null || !mealInfo.applied()) {
            return null;
        }

        return switch (scheduleType) {
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

    // AI 복약 일정을 제거하고 healthContexts 기준으로 Java가 다시 생성
    public CreatePlanAiResponse ensureMedicationSchedules(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        List<CreatePlanAiResponse.PlanDayDetail> fixedPlanDays =
                response.planDays()
                        .stream()
                        .map(planDay -> fixDayMedicationSchedules(planDay, healthContexts))
                        .toList();

        return new CreatePlanAiResponse(fixedPlanDays);
    }

    // 하루치 AI MEDICATION 슬롯을 제거하고 실제 식사시간 또는 등록 식사시간 기준으로 교체
    private CreatePlanAiResponse.PlanDayDetail fixDayMedicationSchedules(
            CreatePlanAiResponse.PlanDayDetail planDay,
            List<TravelHealthContext> healthContexts
    ) {

        List<CreatePlanAiResponse.PlanScheduleDetail> nonMedicationSchedules =
                planDay.schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() != CourseType.MEDICATION)
                        .toList();

        Map<ScheduleType, LocalTime> dayMealTimes =
                dayMealTimes(nonMedicationSchedules);

        List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedules =
                healthContexts.stream()
                        .flatMap(healthContext -> medicationSchedulesFor(healthContext, dayMealTimes).stream())
                        .collect(Collectors.groupingBy(CreatePlanAiResponse.PlanScheduleDetail::startTime))
                        .values()
                        .stream()
                        .map(this::mergeSameTimeMedicationSchedules)
                        .toList();

        List<CreatePlanAiResponse.PlanScheduleDetail> mergedSchedules =
                new ArrayList<>(nonMedicationSchedules);

        mergedSchedules.addAll(medicationSchedules);

        mergedSchedules.sort(Comparator.comparing(CreatePlanAiResponse.PlanScheduleDetail::startTime));

        return new CreatePlanAiResponse.PlanDayDetail(
                planDay.dayNumber(),
                planDay.date(),
                mergedSchedules
        );
    }

    // 그 날짜 실제 식사 일정(BREAKFAST/LUNCH/DINNER)의 시작시간
    private Map<ScheduleType, LocalTime> dayMealTimes(
            List<CreatePlanAiResponse.PlanScheduleDetail> nonMedicationSchedules
    ) {

        return nonMedicationSchedules.stream()
                .filter(schedule -> MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType()))
                .filter(schedule -> schedule.startTime() != null)
                .collect(
                        Collectors.toMap(
                                CreatePlanAiResponse.PlanScheduleDetail::scheduleType,
                                CreatePlanAiResponse.PlanScheduleDetail::startTime,
                                (first, second) -> first
                        )
                );
    }

    // 한 여행자의 모든 복약 정보를 그 날짜의 복약 슬롯 목록으로 변환
    private List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedulesFor(
            TravelHealthContext healthContext,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        return healthContext.medicationInfos()
                .stream()
                .flatMap(medicationInfo ->
                        medicationSchedulesFor(healthContext, medicationInfo, dayMealTimes).stream()
                )
                .toList();
    }

    // 복약 기준(medicationBasis)에 따라 한 복약 정보에서 그 날짜의 복약 슬롯(들)을 계산
    private List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedulesFor(
            TravelHealthContext healthContext,
            TravelHealthContext.MedicationInfoContext medicationInfo,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        boolean usesMealRules =
                (medicationInfo.medicationBasis() == MedicationBasis.WITH_MEAL
                        || medicationInfo.medicationBasis() == MedicationBasis.UNKNOWN)
                        && !medicationInfo.mealMedicationRules().isEmpty();

        if (!usesMealRules) {
            return List.of(
                    medicationSchedule(
                            medicationInfo.medicationTime(),
                            null,
                            medicationInfo.drugName() + " 복용"
                    )
            );
        }

        return medicationInfo.mealMedicationRules()
                .stream()
                .map(rule -> medicationScheduleForRule(healthContext, medicationInfo, rule, dayMealTimes))
                .toList();
    }

    // 식사 연동 복약 규칙 하나를 실제 시각의 복약 슬롯으로 변환
    private CreatePlanAiResponse.PlanScheduleDetail medicationScheduleForRule(
            TravelHealthContext healthContext,
            TravelHealthContext.MedicationInfoContext medicationInfo,
            TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        if (rule.mealTiming() == MealTiming.REGARDLESS_OF_MEAL) {
            return medicationSchedule(
                    medicationInfo.medicationTime(),
                    null,
                    medicationInfo.drugName() + " 복용"
            );
        }

        LocalTime mealTime =
                mealTimeFor(healthContext, rule.relatedMeal(), dayMealTimes);

        if (mealTime == null) {
            throw invalidPlace("복약 기준 식사시간 누락");
        }

        LocalTime medicationTime =
                applyMealTiming(mealTime, rule.mealTiming(), rule.intervalMinutes());

        String description =
                medicationInfo.drugName()
                        + " " + rule.relatedMeal().getCodeName()
                        + " " + rule.mealTiming().getCodeName()
                        + (rule.intervalMinutes() != null && rule.intervalMinutes() > 0
                                ? " " + rule.intervalMinutes() + "분"
                                : "");

        return medicationSchedule(
                medicationTime,
                rule.intervalMinutes(),
                description
        );
    }

    // relatedMeal에 해당하는 그 날짜의 실제 식사시간, 그 날 식사 일정이 없으면
    // 여행자가 등록한 기준 식사시간(mealInfo)으로 대체
    private LocalTime mealTimeFor(
            TravelHealthContext healthContext,
            RelatedMeal relatedMeal,
            Map<ScheduleType, LocalTime> dayMealTimes
    ) {

        ScheduleType scheduleType =
                switch (relatedMeal) {
                    case BREAKFAST -> ScheduleType.BREAKFAST;

                    case LUNCH -> ScheduleType.LUNCH;

                    case DINNER -> ScheduleType.DINNER;
                };

        TravelHealthContext.MealInfoContext mealInfo =
                healthContext.mealInfo();

        LocalTime configuredMealTime =
                mealInfo == null
                        ? null
                        : switch (relatedMeal) {
                            case BREAKFAST -> mealInfo.breakfastTime();

                            case LUNCH -> mealInfo.lunchTime();

                            case DINNER -> mealInfo.dinnerTime();
                        };

        return dayMealTimes.getOrDefault(scheduleType, configuredMealTime);
    }

    // mealTiming/intervalMinutes를 기준시간에 적용한 실제 복약시각
    private LocalTime applyMealTiming(
            LocalTime mealTime,
            MealTiming mealTiming,
            Integer intervalMinutes
    ) {

        int minutes = intervalMinutes == null ? 0 : intervalMinutes;

        return switch (mealTiming) {
            case BEFORE_MEAL -> mealTime.minusMinutes(minutes);

            case AFTER_MEAL -> mealTime.plusMinutes(minutes);

            case DURING_MEAL, REGARDLESS_OF_MEAL -> mealTime;
        };
    }

    // MEDICATION CourseType 슬롯 하나 생성 (체류시간 10분 고정)
    private CreatePlanAiResponse.PlanScheduleDetail medicationSchedule(
            LocalTime startTime,
            Integer intervalMinutes,
            String description
    ) {

        if (startTime == null) {
            throw invalidPlace("복약 기준시간 누락");
        }

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.CHECK_IN,
                CourseType.MEDICATION,
                startTime,
                startTime.plusMinutes(10),
                null,
                null,
                null,
                null,
                null,
                null,
                10,
                null,
                Set.of(),
                new CreatePlanAiResponse.MedicationSchedule(
                        intervalMinutes,
                        description
                ),
                null
        );
    }

    // 같은 시각에 겹치는 여러 복약 슬롯(다른 여행자·다른 약)을 하나로 병합
    private CreatePlanAiResponse.PlanScheduleDetail mergeSameTimeMedicationSchedules(
            List<CreatePlanAiResponse.PlanScheduleDetail> sameTimeSchedules
    ) {

        if (sameTimeSchedules.size() == 1) {
            return sameTimeSchedules.get(0);
        }

        String mergedDescription =
                sameTimeSchedules.stream()
                        .map(schedule -> schedule.medication().description())
                        .collect(Collectors.joining(", "));

        Integer representativeInterval =
                sameTimeSchedules.stream()
                        .map(schedule -> schedule.medication().intervalMinutes())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        return medicationSchedule(
                sameTimeSchedules.get(0).startTime(),
                representativeInterval,
                mergedDescription
        );
    }

    private BaseException invalidPlace(String reason) {

        return new BaseException(PlanEditExceptionEnum.INVALID_AI_PLACE, new Object[]{reason});
    }
}
