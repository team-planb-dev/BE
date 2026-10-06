package com.planb.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.CourseType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * 하루 관광 장소 개수와 초과분 정리 규칙
 */
public final class TouristPlaceCountPolicy {

    private static final int MINIMAL_WALK_COUNT = 2;

    private static final int DEFAULT_COUNT = 3;

    private TouristPlaceCountPolicy() {
    }

    /**
     * 하루 관광 장소 개수 위반, MUST_HAVE만으로 기준을 넘는 날은 그 수를 최대 개수로 인정
     */
    public record Violation(
            int dayNumber,
            int minimumCount,
            int maximumCount,
            int actualCount
    ) {

        public int shortage() {

            return Math.max(
                    0,
                    minimumCount - actualCount
            );
        }
    }

    /**
     * AI 단계 검증과 최종 검증이 함께 쓰는 날짜별 관광 장소 개수 판정
     */
    public static List<Violation> violations(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            Set<Integer> densityReductionDays
    ) {

        int expectedCount = expectedCount(healthContexts);

        if (expectedCount == 0 || response == null || response.planDays() == null) {
            return List.of();
        }

        List<Violation> violations = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null) {
                continue;
            }

            List<CreatePlanAiResponse.PlanScheduleDetail> schedules = day.schedules() == null
                    ? List.of()
                    : day.schedules();

            int actualCount = (int) schedules
                    .stream()
                    .filter(TouristPlaceCountPolicy::isTouristPlace)
                    .count();

            int mustHaveCount = (int) schedules
                    .stream()
                    .filter(Objects::nonNull)
                    .filter(schedule -> schedule.courseType() == CourseType.MUST_HAVE)
                    .count();

            int minimumCount = minimumCount(
                    healthContexts,
                    densityReductionDays != null && densityReductionDays.contains(day.dayNumber())
            );

            int maximumCount = Math.max(
                    expectedCount,
                    mustHaveCount
            );

            if (actualCount < minimumCount || actualCount > maximumCount) {
                violations.add(new Violation(
                        day.dayNumber(),
                        minimumCount,
                        maximumCount,
                        actualCount
                ));
            }
        }

        return violations;
    }

    /**
     * 동행인의 걷기 수준에 따른 하루 관광 장소 개수, 동행인 미선택 시 0개
     */
    public static int expectedCount(List<TravelHealthContext> healthContexts) {

        if (healthContexts == null || healthContexts.isEmpty()) {
            return 0;
        }

        boolean hasMinimalTraveler = healthContexts
                .stream()
                .anyMatch(context -> context.walkType() == WalkType.MINIMAL);

        return hasMinimalTraveler
                ? MINIMAL_WALK_COUNT
                : DEFAULT_COUNT;
    }

    /**
     * 밀도 감소 허용 여부를 반영한 하루 관광 장소 최소 개수
     */
    public static int minimumCount(
            List<TravelHealthContext> healthContexts,
            boolean densityReductionAllowed
    ) {

        int expectedCount = expectedCount(healthContexts);

        if (!densityReductionAllowed || expectedCount == 0) {
            return expectedCount;
        }

        return Math.min(
                MINIMAL_WALK_COUNT,
                expectedCount
        );
    }

    /**
     * 기준 개수를 넘는 관광 장소의 초과분 제거, 일정 미존재 시 원본 유지
     */
    public static CreatePlanAiResponse trimExcess(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        int expectedCount = expectedCount(healthContexts);

        if (expectedCount <= 0) {
            return response;
        }

        return new CreatePlanAiResponse(
                response
                        .planDays()
                        .stream()
                        .map(day -> trimDay(
                                day,
                                expectedCount
                        ))
                        .toList()
        );
    }

    // 하루치 관광 장소 초과분 제거
    // 제거 지점 바로 뒤 슬롯의 이동시간은 이전 구간 기준이라 무효화, 후속 이동시간 확정 단계가 재조회
    private static CreatePlanAiResponse.PlanDayDetail trimDay(
            CreatePlanAiResponse.PlanDayDetail day,
            int expectedCount
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules = day.schedules();

        List<Integer> touristIndexes = IntStream
                .range(0, schedules.size())
                .filter(index -> isTouristPlace(schedules.get(index)))
                .boxed()
                .toList();

        int excess = touristIndexes.size() - expectedCount;

        if (excess <= 0) {
            return day;
        }

        // MUST_HAVE 보존과 뒤쪽 ATTRACTION 우선 제거
        Set<Integer> removeIndexes = new HashSet<>();

        for (int cursor = touristIndexes.size() - 1;
                cursor >= 0 && removeIndexes.size() < excess;
                cursor--) {

            int index = touristIndexes.get(cursor);

            if (schedules
                    .get(index)
                    .courseType() == CourseType.MUST_HAVE) {
                continue;
            }

            removeIndexes.add(index);
        }

        if (removeIndexes.isEmpty()) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> kept = new ArrayList<>();
        boolean previousRemoved = false;

        for (int index = 0; index < schedules.size(); index++) {
            if (removeIndexes.contains(index)) {
                previousRemoved = true;

                continue;
            }

            CreatePlanAiResponse.PlanScheduleDetail schedule = schedules.get(index);

            // 복약·이동 슬롯은 장소 구간이 아니므로 다음 장소 슬롯까지 무효화 대상 유지
            if (schedule == null
                    || schedule.courseType() == CourseType.MEDICATION
                    || schedule.courseType() == CourseType.TRANSPORTATION) {
                kept.add(schedule);

                continue;
            }

            kept.add(previousRemoved
                    ? withoutTravelMinutes(schedule)
                    : schedule);

            previousRemoved = false;
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                kept
        );
    }

    private static CreatePlanAiResponse.PlanScheduleDetail withoutTravelMinutes(
            CreatePlanAiResponse.PlanScheduleDetail schedule
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                null,
                schedule.tags(),
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId()
        );
    }

    // validateTouristPlaceCounts와 동일한 관광 장소 집계 기준
    private static boolean isTouristPlace(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule != null
                && (schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.MUST_HAVE);
    }
}
