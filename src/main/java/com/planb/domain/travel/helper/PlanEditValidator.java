package com.planb.domain.travel.helper;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class PlanEditValidator {

    public Set<Integer> rebuildDays(PlanEditScope scope, PlanEditContext context) {

        if (scope == null || scope.rebuildDayNumbers() == null) {
            throw failure("수정 범위 해석 실패");
        }

        return validatedDays(
                scope.rebuildDayNumbers(),
                context,
                "전체 재구성 대상 날짜 확인 필요"
        );
    }

    public Set<Integer> densityReductionDays(
            PlanEditScope scope,
            PlanEditContext context
    ) {

        if (scope == null || scope.densityReductionDayNumbers() == null) {
            throw failure("밀도 감소 범위 해석 실패");
        }

        return validatedDays(
                scope.densityReductionDayNumbers(),
                context,
                "밀도 감소 대상 날짜 확인 필요"
        );
    }

    private Set<Integer> validatedDays(
            List<Integer> requestedDays,
            PlanEditContext context,
            String failureReason
    ) {

        Set<Integer> days = context
                .currentPlan()
                .planDays()
                .stream()
                .map(day -> day.dayNumber())
                .collect(Collectors.toSet());

        if (!days.containsAll(requestedDays)) {
            throw failure(failureReason);
        }

        return Set.copyOf(requestedDays);
    }

    // 날짜 전체 재구성과 별개인 부분 변경 대상
    // 여러 날짜 일정의 날짜 미지정·없는 날짜·없는 장소는 수정 없이 대상 확인 요청
    public List<PlanEditScope.EditTarget> editTargets(
            PlanEditScope scope,
            PlanEditContext context
    ) {

        if (scope == null || scope.editTargets() == null) {
            return List.of();
        }

        List<GetAiPlanResponse.PlanDayDetail> days = context
                .currentPlan()
                .planDays();

        List<PlanEditScope.EditTarget> targets = new ArrayList<>();

        for (PlanEditScope.EditTarget requested : scope.editTargets()) {
            if (requested == null || requested.slot() == null) {
                throw unclear("수정할 일정 종류 미지정");
            }

            Integer dayNumber = targetDay(
                    requested,
                    days
            );

            GetAiPlanResponse.PlanDayDetail day = days
                    .stream()
                    .filter(candidate -> Objects.equals(candidate.dayNumber(), dayNumber))
                    .findFirst()
                    .orElseThrow(() -> unclear("일정에 없는 날짜: " + dayNumber + "일차"));

            PlanEditScope.EditTarget target = new PlanEditScope.EditTarget(
                    dayNumber,
                    requested.slot(),
                    storedPlaceName(
                            requested,
                            day
                    ),
                    requested.menuOnlyRequested()
            );

            if (day
                    .schedules()
                    .stream()
                    .noneMatch(slot -> covers(
                            target,
                            slot.scheduleType(),
                            slot.courseType(),
                            slot.locationName()
                    ))) {
                throw unclear(dayNumber + "일차에 수정할 " + requested.slot() + " 없음");
            }

            targets.add(target);
        }

        return List.copyOf(targets);
    }

    // 구조화 응답의 일차 미지정 표기 0, 하루 일정만 그 날짜로 간주
    private Integer targetDay(
            PlanEditScope.EditTarget requested,
            List<GetAiPlanResponse.PlanDayDetail> days
    ) {

        if (requested.dayNumber() != null && requested.dayNumber() > 0) {
            return requested.dayNumber();
        }

        if (days.size() != 1) {
            throw unclear("수정할 날짜 미지정");
        }

        return days
                .getFirst()
                .dayNumber();
    }

    // 지목 장소명의 저장 장소명 확정, 공백 무시 일치 우선·요청명을 포함한 유일한 저장명 허용
    private String storedPlaceName(
            PlanEditScope.EditTarget requested,
            GetAiPlanResponse.PlanDayDetail day
    ) {

        String requestedName = PlaceCandidateContext.normalizeName(requested.placeName());

        if (requestedName.isEmpty()) {
            return null;
        }

        List<String> names = day
                .schedules()
                .stream()
                .filter(slot -> sameKind(
                        requested.slot(),
                        slot.scheduleType(),
                        slot.courseType()
                ))
                .map(GetAiPlanResponse.PlanScheduleDetail::locationName)
                .filter(Objects::nonNull)
                .toList();

        Optional<String> exact = names
                .stream()
                .filter(name -> PlaceCandidateContext
                        .normalizeName(name)
                        .equals(requestedName))
                .findFirst();

        if (exact.isPresent()) {
            return exact.get();
        }

        // 저장 장소명이 요청 일부를 포함하는 경우만 인정, 짧은 저장명의 오탐 방지
        List<String> partial = requestedName.length() < 2
                ? List.of()
                : names
                        .stream()
                        .filter(name -> PlaceCandidateContext
                                .normalizeName(name)
                                .contains(requestedName))
                        .distinct()
                        .toList();

        if (partial.size() != 1) {
            throw unclear(day.dayNumber() + "일차에서 장소를 특정할 수 없음: " + requested.placeName());
        }

        return partial.getFirst();
    }

    public BaseException unclear(String reason) {

        return new BaseException(PlanEditExceptionEnum.EDIT_TARGET_UNCLEAR, new Object[]{reason});
    }

    // 대상 분류에 속하는 편집 전 슬롯, 장소명 지정이 없으면 MUST_HAVE 제외
    public boolean covers(
            PlanEditScope.EditTarget target,
            ScheduleType scheduleType,
            CourseType courseType,
            String locationName
    ) {

        if (!sameKind(
                target.slot(),
                scheduleType,
                courseType
        )) {
            return false;
        }

        if (target.placeName() == null) {
            return courseType != CourseType.MUST_HAVE;
        }

        return PlaceCandidateContext
                .normalizeName(target.placeName())
                .equals(PlaceCandidateContext.normalizeName(locationName));
    }

    // 대상 분류와 같은 종류의 슬롯
    public boolean sameKind(
            PlanEditScope.EditSlot slot,
            ScheduleType scheduleType,
            CourseType courseType
    ) {

        if (slot == null || courseType == null) {
            return false;
        }

        return switch (slot) {
            case BREAKFAST -> scheduleType == ScheduleType.BREAKFAST && isMeal(courseType);
            case LUNCH -> scheduleType == ScheduleType.LUNCH && isMeal(courseType);
            case DINNER -> scheduleType == ScheduleType.DINNER && isMeal(courseType);
            case ATTRACTION -> courseType == CourseType.ATTRACTION
                    || courseType == CourseType.PARK_WALK
                    || courseType == CourseType.MUST_HAVE;
            case CAFE -> courseType == CourseType.CAFE_REST;
        };
    }

    public boolean rebuilt(
            PlanEditContext context,
            CreatePlanAiResponse.PlanDayDetail edited
    ) {

        List<GetAiPlanResponse.PlanScheduleDetail> before =
                context
                        .currentPlan()
                        .planDays()
                        .stream()
                        .filter(day -> Objects.equals(day.dayNumber(), edited.dayNumber()))
                        .flatMap(day -> day
                                .schedules()
                                .stream())
                        .filter(slot -> isPlace(slot.courseType()))
                        .toList();

        // 장소명 표현·순서 변경 또는 슬롯 삭제만으로 성공 처리 방지
        return edited
                .schedules()
                .stream()
                .filter(slot -> isPlace(slot.courseType()))
                .filter(slot -> !PlaceCandidateContext
                        .normalizeName(slot.locationName())
                        .isBlank())
                .anyMatch(slot -> before
                        .stream()
                        .noneMatch(old -> PlaceCandidateContext
                                .normalizeName(old.locationName())
                                .equals(PlaceCandidateContext.normalizeName(slot.locationName()))
                                || sameCoordinates(
                                        old.longitude(),
                                        old.latitude(),
                                        slot.longitude(),
                                        slot.latitude()
                                )));
    }

    public boolean sameDay(
            PlanEditContext context,
            Integer dayNumber,
            CreatePlanAiResponse response
    ) {

        if (response == null
                || response.planDays() == null
                || response
                        .planDays()
                        .size() != 1) {
            return false;
        }

        CreatePlanAiResponse.PlanDayDetail day = response
                .planDays()
                .getFirst();

        return day != null
                && Objects.equals(day.dayNumber(), dayNumber)
                && day.schedules() != null
                && !day
                        .schedules()
                        .isEmpty()
                && context
                        .currentPlan()
                        .planDays()
                        .stream()
                        .anyMatch(old -> Objects.equals(old.dayNumber(), dayNumber)
                                && Objects.equals(old.date(), day.date()));
    }

    public Optional<String> rebuildFailure(
            PlanEditContext context,
            Integer dayNumber,
            RebuildPlanDayResponse response
    ) {

        String expected = context
                .currentPlan()
                .planDays()
                .stream()
                .filter(day -> Objects.equals(day.dayNumber(), dayNumber))
                .map(day -> "dayNumber=" + dayNumber + ", date=" + day.date())
                .findFirst()
                .orElse("dayNumber=" + dayNumber + ", date=unknown");

        if (response == null) {
            return Optional.of("기대 " + expected + "; 재구성 응답 null");
        }

        if (!response.rebuilt()) {
            String reason = response.failureReason();

            return Optional.of("기대 " + expected + "; AI 재구성 불가: "
                    + (reason == null || reason.isBlank() ? "실패 사유 누락" : reason));
        }

        if (response.planDays() == null) {
            return Optional.of("기대 " + expected + "; planDays null");
        }

        String actual = response
                .planDays()
                .stream()
                .limit(5)
                .map(day -> day == null
                        ? "null"
                        : "dayNumber=" + day.dayNumber()
                                + ", date=" + day.date()
                                + ", schedules=" + (day.schedules() == null
                                        ? "null"
                                        : day
                                                .schedules()
                                                .size()))
                .collect(Collectors.joining("; "));

        if (response
                .planDays()
                .size() != 1) {
            return Optional.of(
                    "기대 " + expected
                            + "; 날짜 개수="
                            + response
                                    .planDays()
                                    .size()
                            + "; 실제 [" + actual + "]"
            );
        }

        if (!sameDay(
                context,
                dayNumber,
                new CreatePlanAiResponse(response.planDays())
        )) {
            return Optional.of("기대 " + expected + "; 날짜 불일치 또는 빈 슬롯; 실제 [" + actual + "]");
        }

        if (response.failureReason() == null || !response
                .failureReason()
                .isBlank()) {
            return Optional.of("기대 " + expected + "; 성공 응답의 failureReason은 빈 문자열 필요");
        }

        return Optional.empty();
    }

    public BaseException failure(String reason) {

        return new BaseException(PlanEditExceptionEnum.EDIT_NOT_APPLIED, new Object[]{reason});
    }

    private boolean isMeal(CourseType type) {

        return type == CourseType.RESTAURANT || type == CourseType.LOCAL_FOOD;
    }

    private boolean isPlace(CourseType type) {

        return type != null && type != CourseType.MEDICATION && type != CourseType.TRANSPORTATION;
    }

    private boolean sameCoordinates(
            String oldX,
            String oldY,
            String newX,
            String newY
    ) {

        try {
            return Math.abs(Double.parseDouble(oldX) - Double.parseDouble(newX)) < 0.0001
                    && Math.abs(Double.parseDouble(oldY) - Double.parseDouble(newY)) < 0.0001;
        } catch (NullPointerException | NumberFormatException exception) {
            return false;
        }
    }
}
