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
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 확정된 장소를 기준으로 한 시간·식사·복약 정규화
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

    // 설정 식사시각에는 종료시각이 없다. 식후 복약의 기준을 만들기 위한 기본 식사 소요시간
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
                        .map(day -> normalizeScheduleTimes(
                                day,
                                healthContexts
                        ))
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
            if (!normalizedPlace(schedule)) {
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

                    // 식사시간 충족이 가능한 경우에만 앞 장소 이동
                    // 앞 장소 이동 불가 시 일정 생성 유지와 가장 이른 시각 배치
                    boolean shifted = tryShiftPreviousPlaces(
                            schedules,
                            movableStartIndex,
                            shiftMinutes,
                            previousMealEnd
                    );

                    if (!shifted) {
                        shifted = tryShiftPreviousMeal(
                                schedules,
                                movableStartIndex,
                                shiftMinutes,
                                healthContexts
                        );
                    }

                    if (shifted) {
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

                // 식사시간보다 이동시간의 물리적 제약 우선
                if (earliestStart != null && startTime.isBefore(earliestStart)) {
                    startTime = earliestStart;
                }
            }

            LocalTime endTime = startTime
                    .plusMinutes(schedule.stayMinutes());

            schedules.add(withScheduleTimes(
                            schedule,
                            startTime,
                            endTime
                    ));

            previousPlaceEnd = endTime;

            if (mealTimeRange != null) {
                previousMealEnd = endTime;
                movableStartIndex = schedules.size();
            }
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                schedules
        );
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

    // 식사시간 충족을 위한 직전 식사 이후 장소 이동
    // 앞 장소 종료·이동시간 이후, 원래 시각 이전 범위에서 마지막 장소 종료를 shiftMinutes만큼 앞당김
    // 가장 이른 배치로 가능 여부 판정 후 뒤에서부터 필요한 만큼만 당김, 불가 시 변경 없이 false
    private boolean tryShiftPreviousPlaces(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            int fromIndex,
            long shiftMinutes,
            LocalTime previousMealEnd
    ) {

        List<Integer> places = new ArrayList<>();

        for (int index = fromIndex; index < schedules.size(); index++) {
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            if (!normalizedPlace(schedule)) {
                continue;
            }

            if (schedule.travelMinutes() != null && schedule.travelMinutes() < 0) {
                return false;
            }

            places.add(index);
        }

        if (places.isEmpty()) {
            return false;
        }

        CreatePlanAiResponse.PlanScheduleDetail last = schedules.get(places.getLast());

        LocalTime targetEnd = last
                .endTime()
                .minusMinutes(shiftMinutes);

        // 자정을 넘겨 되감긴 경우
        if (targetEnd.isAfter(last.endTime())) {
            return false;
        }

        // 가장 이른 배치의 마지막 장소 종료로 가능 여부 판정
        LocalTime earliestEnd = previousMealEnd;

        List<LocalTime> earliestStarts = new ArrayList<>();

        for (int index : places) {
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            LocalTime start = schedule
                    .startTime()
                    .minusMinutes(shiftMinutes);

            if (start.isAfter(schedule.startTime())) {
                return false;
            }

            if (earliestEnd != null) {
                LocalTime earliest = earliestEnd.plusMinutes(travelOf(schedule));

                if (start.isBefore(earliest)) {
                    start = earliest;
                }
            }

            if (start.isAfter(schedule.startTime())) {
                start = schedule.startTime();
            }

            earliestStarts.add(start);

            earliestEnd = start.plusMinutes(schedule.stayMinutes());
        }

        if (earliestEnd.isAfter(targetEnd)) {
            return false;
        }

        // 뒤 장소부터 다음 장소 시작·이동시간을 지키는 가장 늦은 시각으로 배치
        List<CreatePlanAiResponse.PlanScheduleDetail> moved = new ArrayList<>(schedules);

        LocalTime nextStart = null;
        int nextTravel = 0;

        for (int position = places.size() - 1; position >= 0; position--) {
            int index = places.get(position);
            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            LocalTime end = nextStart == null
                    ? targetEnd
                    : nextStart.minusMinutes(nextTravel);

            LocalTime start = end.minusMinutes(schedule.stayMinutes());

            if (start.isAfter(schedule.startTime())) {
                start = schedule.startTime();
            }

            // 이동시간 누락 등으로 앞 일정과 겹치는 배치 거부
            if (start.isBefore(earliestStarts.get(position))) {
                return false;
            }

            moved.set(
                    index,
                    withScheduleTimes(
                            schedule,
                            start,
                            start.plusMinutes(schedule.stayMinutes())
                    )
            );

            nextStart = start;
            nextTravel = travelOf(schedule);
        }

        schedules.clear();
        schedules.addAll(moved);

        return true;
    }

    private static int travelOf(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule.travelMinutes() == null
                ? 0
                : schedule.travelMinutes();
    }

    // 허용 창·앞 장소 이동시간을 유지한 직전 식사의 최소 앞당김
    // 사이 장소의 여유 압축과 함께 충족되는 가장 작은 앞당김 선택
    private boolean tryShiftPreviousMeal(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            int fromIndex,
            long shiftMinutes,
            List<TravelHealthContext> healthContexts
    ) {

        if (fromIndex == 0 || shiftMinutes <= 0) {
            return false;
        }

        int mealIndex = fromIndex - 1;
        CreatePlanAiResponse.PlanScheduleDetail meal = schedules.get(mealIndex);
        MealTimeRange range = mealTimeRange(meal.scheduleType(), healthContexts);

        if (range == null || !normalizedPlace(meal)) {
            return false;
        }

        boolean placesBetween = schedules
                .subList(fromIndex, schedules.size())
                .stream()
                .anyMatch(this::normalizedPlace);

        Optional<CreatePlanAiResponse.PlanScheduleDetail> precedingPlace = schedules
                .subList(0, mealIndex)
                .stream()
                .filter(this::normalizedPlace)
                .reduce((first, second) -> second);

        // 사이 장소 부재 시 식사 자체의 shiftMinutes 앞당김
        long firstShift = placesBetween ? 1 : shiftMinutes;
        long lastShift = placesBetween ? MEAL_TIME_TOLERANCE_MINUTES * 2 : shiftMinutes;

        for (long mealShift = firstShift; mealShift <= lastShift; mealShift++) {
            if (meal
                    .startTime()
                    .toSecondOfDay() < mealShift * 60) {
                return false;
            }

            LocalTime shiftedMealStart = meal
                    .startTime()
                    .minusMinutes(mealShift);

            if (shiftedMealStart.isBefore(range.earliest())) {
                return false;
            }

            if (shiftedMealStart.isAfter(range.latest())) {
                continue;
            }

            if (precedingPlace.isPresent()) {
                LocalTime end = precedingPlace
                        .get()
                        .endTime();
                int travel = travelOf(meal);

                if (end == null || travel < 0
                        || end.toSecondOfDay() + travel * 60L > shiftedMealStart.toSecondOfDay()) {
                    return false;
                }
            }

            CreatePlanAiResponse.PlanScheduleDetail shiftedMeal = withScheduleTimes(
                    meal,
                    shiftedMealStart,
                    shiftedMealStart.plusMinutes(meal.stayMinutes())
            );

            // 직전 식사가 아침·점심이라 앞당길수록 연동 복약의 자정 초과 위험만 증가
            if (!mealMedicationFitsDay(shiftedMeal, healthContexts)) {
                return false;
            }

            List<CreatePlanAiResponse.PlanScheduleDetail> shifted = new ArrayList<>(schedules);
            shifted.set(mealIndex, shiftedMeal);

            if (!placesBetween || tryShiftPreviousPlaces(
                    shifted,
                    fromIndex,
                    shiftMinutes,
                    shiftedMeal.endTime()
            )) {
                schedules.clear();
                schedules.addAll(shifted);

                return true;
            }
        }

        return false;
    }

    // 앞당긴 식사의 연동 복약 전체 구간에 대한 자정 미초과
    private boolean mealMedicationFitsDay(
            CreatePlanAiResponse.PlanScheduleDetail meal,
            List<TravelHealthContext> healthContexts
    ) {

        long mealStartSeconds = meal
                .startTime()
                .toSecondOfDay();

        for (TravelHealthContext context : healthContexts) {
            for (TravelHealthContext.MedicationInfoContext medication : context.medicationInfos()) {
                if (medication.medicationBasis() != MedicationBasis.WITH_MEAL
                        && medication.medicationBasis() != MedicationBasis.UNKNOWN) {
                    continue;
                }

                for (TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext rule
                        : medication.mealMedicationRules()) {
                    if (rule.mealTiming() == MealTiming.REGARDLESS_OF_MEAL
                            || scheduleType(rule.relatedMeal()) != meal.scheduleType()) {
                        continue;
                    }

                    long intervalSeconds = rule.intervalMinutes() == null ? 0 : rule.intervalMinutes() * 60L;
                    long medicationStart = switch (rule.mealTiming()) {
                        case BEFORE_MEAL -> mealStartSeconds - intervalSeconds;
                        case AFTER_MEAL -> mealStartSeconds
                                + meal.stayMinutes() * 60L + intervalSeconds;
                        case DURING_MEAL -> mealStartSeconds;
                        case REGARDLESS_OF_MEAL -> throw new IllegalStateException("식사 무관 복약");
                    };

                    if (medicationStart < 0 || medicationStart + 10 * 60 >= 24 * 60 * 60) {
                        return false;
                    }
                }
            }
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
                schedule.candidateId()
        );
    }

    // 카페 등 비식사 슬롯을 제외한 식사시간 판정 대상
    public boolean mealSlot(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule != null && MEAL_SCHEDULE_TYPES.contains(schedule.scheduleType());
    }

    /**
     * 여행자의 등록 식사시각 충족 여부
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
                response
                        .planDays()
                        .stream()
                        .map(planDay -> fixDayMedicationSchedules(planDay, healthContexts))
                        .toList();

        return new CreatePlanAiResponse(fixedPlanDays);
    }

    // 최종 식사 기준 복약 알림 배치, 이동·장소 체류 중첩 허용
    public CreatePlanAiResponse finalizeMedicationSchedules(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        return ensureMedicationSchedules(response, healthContexts);
    }

    // 시간표 정규화 대상 장소 슬롯
    private boolean normalizedPlace(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule != null
                && schedule.startTime() != null
                && schedule.stayMinutes() != null
                && schedule.stayMinutes() > 0
                && planPlaceResolver.requiresPlace(schedule);
    }

    // 식사 기준의 일정 유형
    private static ScheduleType scheduleType(RelatedMeal relatedMeal) {

        return switch (relatedMeal) {
            case BREAKFAST -> ScheduleType.BREAKFAST;

            case LUNCH -> ScheduleType.LUNCH;

            case DINNER -> ScheduleType.DINNER;
        };
    }

    // 하루치 AI MEDICATION 슬롯을 제거하고 실제 식사시간 또는 등록 식사시간 기준으로 교체
    private CreatePlanAiResponse.PlanDayDetail fixDayMedicationSchedules(
            CreatePlanAiResponse.PlanDayDetail planDay,
            List<TravelHealthContext> healthContexts
    ) {

        List<CreatePlanAiResponse.PlanScheduleDetail> nonMedicationSchedules =
                planDay
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() != CourseType.MEDICATION)
                        .toList();

        Map<ScheduleType, MealWindow> dayMealTimes =
                dayMealTimes(nonMedicationSchedules);

        List<CreatePlanAiResponse.PlanScheduleDetail> medicationSchedules =
                healthContexts
                        .stream()
                        .flatMap(healthContext -> medicationSchedulesFor(
                                healthContext,
                                dayMealTimes
                        )
                                .stream())
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

        return nonMedicationSchedules
                .stream()
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

    // 종료시각 누락 시 기본 소요시간을 적용한 식사 슬롯 시간대
    private static MealWindow mealWindow(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return new MealWindow(
                schedule.startTime(),
                schedule.endTime() == null
                        ? schedule
                                .startTime()
                                .plusMinutes(DEFAULT_MEAL_MINUTES)
                        : schedule.endTime()
        );
    }

    /**
     * 식전·식후 복약의 기준 식사 시간대
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

        return healthContext
                .medicationInfos()
                .stream()
                .flatMap(medicationInfo ->
                        medicationSchedulesFor(
                                healthContext,
                                medicationInfo,
                                dayMealTimes
                        )
                                .stream()
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
                        && !medicationInfo
                                .mealMedicationRules()
                                .isEmpty();

        if (!usesMealRules) {
            return List.of(
                    medicationSchedule(
                            medicationInfo.medicationTime(),
                            null,
                            medicationInfo.drugName() + " 복용"
                    )
            );
        }

        return medicationInfo
                .mealMedicationRules()
                .stream()
                .map(rule -> medicationScheduleForRule(
                        healthContext,
                        medicationInfo,
                        rule,
                        dayMealTimes
                ))
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
                mealTimeFor(
                        healthContext,
                        rule.relatedMeal(),
                        dayMealTimes
                );

        if (mealWindow == null) {
            throw invalidPlace("복약 기준 식사시간 누락");
        }

        LocalTime medicationTime =
                applyMealTiming(
                        mealWindow,
                        rule.mealTiming(),
                        rule.intervalMinutes()
                );

        String description =
                medicationInfo.drugName()
                        + " " + rule
                                .relatedMeal()
                                .getCodeName()
                        + " " + rule
                                .mealTiming()
                                .getCodeName()
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

        ScheduleType scheduleType = scheduleType(relatedMeal);

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

    // mealTiming/intervalMinutes를 식사 시간대에 적용한 실제 복약시각
    // 식후 복약의 식사 종료시각 기준
    private LocalTime applyMealTiming(
            MealWindow mealWindow,
            MealTiming mealTiming,
            Integer intervalMinutes
    ) {

        int minutes = intervalMinutes == null ? 0 : intervalMinutes;

        return switch (mealTiming) {
            case BEFORE_MEAL -> mealWindow
                    .start()
                    .minusMinutes(minutes);

            case AFTER_MEAL -> mealWindow
                    .end()
                    .plusMinutes(minutes);

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
                sameTimeSchedules
                        .stream()
                        .map(schedule -> schedule
                                .medication()
                                .description())
                        .collect(Collectors.joining(", "));

        Integer representativeInterval =
                sameTimeSchedules
                        .stream()
                        .map(schedule -> schedule
                                .medication()
                                .intervalMinutes())
                        .filter(Objects::nonNull)
                        .findFirst()
                        .orElse(null);

        return medicationSchedule(
                sameTimeSchedules
                        .get(0)
                        .startTime(),
                representativeInterval,
                mergedDescription
        );
    }

    private BaseException invalidPlace(String reason) {

        return new BaseException(PlanEditExceptionEnum.INVALID_AI_PLACE, new Object[]{reason});
    }
}
