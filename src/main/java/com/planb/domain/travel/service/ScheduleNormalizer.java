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

    // 설정 식사시각에는 종료시각이 없다. 식후 복약의 기준을 만들기 위한 기본 식사 소요시간.
    private static final long DEFAULT_MEAL_MINUTES = 60;

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

            LocalTime earliestStart = null;

            if (previousPlaceEnd != null && schedule.travelMinutes() != null
                    && schedule.travelMinutes() >= 0) {

                earliestStart = previousPlaceEnd
                        .plusMinutes(schedule.travelMinutes());

                if (mealTimeRange != null && earliestStart.isAfter(mealTimeRange.latest())) {
                    long shiftMinutes = Duration
                            .between(
                                    mealTimeRange.latest(),
                                    earliestStart
                            )
                            .toMinutes();

                    // 앞 장소를 당겨 식사시간을 맞출 수 있을 때만 당긴다.
                    // 당길 수 없어도 일정 생성을 실패시키지 않고 가능한 가장 이른 시각에 배치한다.
                    if (tryShiftPreviousPlaces(
                            schedules,
                            movableStartIndex,
                            shiftMinutes,
                            previousMealEnd
                    )) {
                        previousPlaceEnd = previousPlaceEnd.minusMinutes(shiftMinutes);
                        earliestStart = previousPlaceEnd.plusMinutes(schedule.travelMinutes());
                    }
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

                // 식사시간 창으로 당긴 결과가 이동시간을 무시하게 되면 물리적 제약을 우선한다.
                if (earliestStart != null && startTime.isBefore(earliestStart)) {
                    startTime = earliestStart;
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

    // 식사시간을 맞추기 위해 직전 식사 이후의 장소들을 앞당긴다.
    // 앞당길 수 없는 조건이면 아무것도 바꾸지 않고 false를 돌려준다. 실패는 호출부가 판단한다.
    private boolean tryShiftPreviousPlaces(
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
            return false;
        }

        CreatePlanAiResponse.PlanScheduleDetail firstPlace = schedules.get(firstPlaceIndex);

        LocalTime shiftedFirstStart = firstPlace.startTime().minusMinutes(shiftMinutes);

        // 자정을 넘겨 되감긴 경우
        if (shiftedFirstStart.isAfter(firstPlace.startTime())) {
            return false;
        }

        if (previousMealEnd != null) {
            LocalTime earliestStart = previousMealEnd.plusMinutes(
                    firstPlace.travelMinutes() == null
                            ? 0
                            : firstPlace.travelMinutes()
            );

            if (shiftedFirstStart.isBefore(earliestStart)) {
                return false;
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

        return true;
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

    // 식사시간 판정 대상인 슬롯인지 여부. 카페 같은 비식사 슬롯은 판정하지 않는다.
    public boolean mealSlot(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule != null && MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType());
    }

    /**
     * 이 슬롯이 여행자들이 설정한 식사시간을 실제로 만족하는지 판단한다.
     *
     * 식사시간을 설정한 여행자가 한 명도 없으면 "반영했다"고 말할 근거가 없으므로 false다.
     * MEAL_TIME_APPLIED 태그는 이 결과로만 결정한다.
     */
    public boolean mealTimeSatisfied(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            List<TravelHealthContext> healthContexts
    ) {

        if (schedule == null || schedule.startTime() == null || healthContexts == null
                || !MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType())) {
            return false;
        }

        List<LocalTime> configuredTimes = healthContexts
                .stream()
                .map(TravelHealthContext::mealInfo)
                .map(mealInfo -> configuredMealTime(mealInfo, schedule.scheduleType()))
                .filter(Objects::nonNull)
                .toList();

        if (configuredTimes.isEmpty()) {
            return false;
        }

        return configuredTimes
                .stream()
                .allMatch(configuredTime -> Math.abs(
                        Duration
                                .between(
                                        configuredTime,
                                        schedule.startTime()
                                )
                                .toMinutes()
                ) <= MEAL_TIME_TOLERANCE_MINUTES);
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

        Map<ScheduleType, MealWindow> dayMealTimes =
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

    // 그 날짜 실제 식사 일정(BREAKFAST/LUNCH/DINNER)의 시작·종료시간
    private Map<ScheduleType, MealWindow> dayMealTimes(
            List<CreatePlanAiResponse.PlanScheduleDetail> nonMedicationSchedules
    ) {

        return nonMedicationSchedules.stream()
                .filter(schedule -> MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType()))
                .filter(schedule -> schedule.startTime() != null)
                .collect(
                        Collectors.toMap(
                                CreatePlanAiResponse.PlanScheduleDetail::scheduleType,
                                ScheduleNormalizer::mealWindow,
                                (first, second) -> first
                        )
                );
    }

    // 식사 슬롯의 시간대. 종료시각이 비어 있으면 기본 소요시간으로 채운다.
    private static MealWindow mealWindow(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return new MealWindow(
                schedule.startTime(),
                schedule.endTime() == null
                        ? schedule.startTime().plusMinutes(DEFAULT_MEAL_MINUTES)
                        : schedule.endTime()
        );
    }

    /**
     * 복약 기준이 되는 식사 시간대.
     *
     * 식전은 시작시각, 식후는 종료시각을 기준으로 삼아야 뜻이 맞는다.
     * 두 값을 함께 들고 다녀야 mealTiming마다 올바른 쪽을 고를 수 있다.
     */
    private record MealWindow(

            LocalTime start,
            LocalTime end
    ) {
    }

    // 한 여행자의 모든 복약 정보를 그 날짜의 복약 슬롯 목록으로 변환
    private List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedulesFor(
            TravelHealthContext healthContext,
            Map<ScheduleType, MealWindow> dayMealTimes
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
            Map<ScheduleType, MealWindow> dayMealTimes
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
            Map<ScheduleType, MealWindow> dayMealTimes
    ) {

        if (rule.mealTiming() == MealTiming.REGARDLESS_OF_MEAL) {
            return medicationSchedule(
                    medicationInfo.medicationTime(),
                    null,
                    medicationInfo.drugName() + " 복용"
            );
        }

        MealWindow mealWindow =
                mealTimeFor(healthContext, rule.relatedMeal(), dayMealTimes);

        if (mealWindow == null) {
            throw invalidPlace("복약 기준 식사시간 누락");
        }

        LocalTime medicationTime =
                applyMealTiming(mealWindow, rule.mealTiming(), rule.intervalMinutes());

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
    private MealWindow mealTimeFor(
            TravelHealthContext healthContext,
            RelatedMeal relatedMeal,
            Map<ScheduleType, MealWindow> dayMealTimes
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

        MealWindow actual = dayMealTimes.get(scheduleType);

        if (actual != null) {
            return actual;
        }

        return configuredMealTime == null
                ? null
                : new MealWindow(
                        configuredMealTime,
                        configuredMealTime.plusMinutes(DEFAULT_MEAL_MINUTES)
                );
    }

    // mealTiming/intervalMinutes를 식사 시간대에 적용한 실제 복약시각.
    // 식후는 식사가 끝난 뒤를 뜻하므로 종료시각을 기준으로 잡는다.
    private LocalTime applyMealTiming(
            MealWindow mealWindow,
            MealTiming mealTiming,
            Integer intervalMinutes
    ) {

        int minutes = intervalMinutes == null ? 0 : intervalMinutes;

        return switch (mealTiming) {
            case BEFORE_MEAL -> mealWindow.start().minusMinutes(minutes);

            case AFTER_MEAL -> mealWindow.end().plusMinutes(minutes);

            case DURING_MEAL, REGARDLESS_OF_MEAL -> mealWindow.start();
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
