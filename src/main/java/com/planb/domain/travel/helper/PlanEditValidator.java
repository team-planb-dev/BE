package com.planb.domain.travel.helper;

import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import org.springframework.stereotype.Component;

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

        Set<Integer> days = context.currentPlan().planDays().stream()
                .map(day -> day.dayNumber()).collect(Collectors.toSet());

        if (!days.containsAll(scope.rebuildDayNumbers())) {
            throw failure("전체 재구성 대상 날짜 확인 필요");
        }

        return Set.copyOf(scope.rebuildDayNumbers());
    }

    public boolean rebuilt(
            PlanEditContext context,
            CreatePlanAiResponse.PlanDayDetail edited
    ) {

        List<GetAiPlanResponse.PlanScheduleDetail> before =
                context.currentPlan().planDays().stream()
                        .filter(day -> Objects.equals(day.dayNumber(), edited.dayNumber()))
                        .flatMap(day -> day.schedules().stream())
                        .filter(slot -> isPlace(slot.courseType())).toList();

        // 장소명 표현·순서 변경 또는 슬롯 삭제만으로 성공 처리 방지
        return edited.schedules().stream().filter(slot -> isPlace(slot.courseType()))
                .filter(slot -> !normalize(slot.locationName()).isBlank())
                .anyMatch(slot -> before.stream().noneMatch(old ->
                        normalize(old.locationName()).equals(normalize(slot.locationName()))
                                || sameCoordinates(old.longitude(), old.latitude(), slot.longitude(), slot.latitude())));
    }

    public boolean sameDay(
            PlanEditContext context,
            Integer dayNumber,
            CreatePlanAiResponse response
    ) {

        if (response == null || response.planDays() == null || response.planDays().size() != 1) {
            return false;
        }

        CreatePlanAiResponse.PlanDayDetail day = response.planDays().getFirst();

        return day != null && Objects.equals(day.dayNumber(), dayNumber) && day.schedules() != null
                && !day.schedules().isEmpty() && context.currentPlan().planDays().stream()
                .anyMatch(old -> Objects.equals(old.dayNumber(), dayNumber) && Objects.equals(old.date(), day.date()));
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
                .map(day -> day == null ? "null" : "dayNumber=" + day.dayNumber()
                        + ", date=" + day.date()
                        + ", schedules=" + (day.schedules() == null ? "null" : day.schedules().size()))
                .collect(Collectors.joining("; "));

        if (response.planDays().size() != 1) {
            return Optional.of("기대 " + expected + "; 날짜 개수=" + response.planDays().size()
                    + "; 실제 [" + actual + "]");
        }

        if (!sameDay(context, dayNumber, new CreatePlanAiResponse(response.planDays()))) {
            return Optional.of("기대 " + expected + "; 날짜 불일치 또는 빈 슬롯; 실제 [" + actual + "]");
        }

        if (response.failureReason() == null || !response.failureReason().isBlank()) {
            return Optional.of("기대 " + expected + "; 성공 응답의 failureReason은 빈 문자열 필요");
        }

        return Optional.empty();
    }

    public BaseException failure(String reason) {

        return new BaseException(PlanEditExceptionEnum.EDIT_NOT_APPLIED, new Object[]{reason});
    }

    private boolean isPlace(CourseType type) {

        return type != null && type != CourseType.MEDICATION && type != CourseType.TRANSPORTATION;
    }

    private boolean sameCoordinates(String oldX, String oldY, String newX, String newY) {

        try {
            return Math.abs(Double.parseDouble(oldX) - Double.parseDouble(newX)) < 0.0001
                    && Math.abs(Double.parseDouble(oldY) - Double.parseDouble(newY)) < 0.0001;
        } catch (NullPointerException | NumberFormatException exception) {
            return false;
        }
    }

    private String normalize(String name) {

        return name == null ? "" : name.replaceAll("\\s+", "");
    }
}
