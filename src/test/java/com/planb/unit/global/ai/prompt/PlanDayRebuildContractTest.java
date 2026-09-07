package com.planb.unit.global.ai.prompt;

import com.planb.ai.context.PlanEditContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.prompt.RebuildPlanDayPrompt;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.helper.PlanEditValidationHelper;
import com.planb.global.config.ai.AiOutputConverterConfig;
import com.planb.global.config.app.AppConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class PlanDayRebuildContractTest {

    private final LocalDate date = LocalDate.of(2026, 9, 14);

    private final PlanEditValidationHelper helper = new PlanEditValidationHelper();

    @Test
    @DisplayName("후보 부재 응답을 JSON 파싱 오류와 구분하여 실패 사유 보존")
    void preservesExplicitCandidateFailure() {

        RebuildPlanDayResponse response = new AiOutputConverterConfig()
                .rebuildPlanDayResponseConverter()
                .convert("""
                        {"rebuilt":false,"failureReason":"다른 구체적 후보 3곳 검색 결과 없음","planDays":[]}
                        """);

        String reason = helper
                .rebuildFailure(
                        context(),
                        1,
                        response)
                .orElseThrow();

        assertFalse(response.rebuilt());

        assertTrue(reason.contains("AI 재구성 불가"));

        assertTrue(reason.contains("구체적 후보 3곳"));
    }

    @Test
    @DisplayName("두 날짜 반환 시 기대 날짜와 실제 날짜 개수 기록")
    void reportsMultipleReturnedDays() {

        RebuildPlanDayResponse response = new RebuildPlanDayResponse(
                true,
                "",
                List.of(
                        day(1),
                        day(2)));

        String reason = helper
                .rebuildFailure(
                        context(),
                        1,
                        response)
                .orElseThrow();

        assertTrue(reason.contains("날짜 개수=2"));

        assertTrue(reason.contains("date=2026-09-14"));

        assertTrue(reason.contains("dayNumber=2"));
    }

    @Test
    @DisplayName("빈 슬롯과 잘못된 일차의 실제 응답 구조 기록")
    void reportsEmptySchedulesAndWrongDay() {

        RebuildPlanDayResponse empty = new RebuildPlanDayResponse(
                true,
                "",
                List.of(
                        new CreatePlanAiResponse.PlanDayDetail(
                                1,
                                date,
                                List.of())));

        assertTrue(
                helper
                        .rebuildFailure(
                                context(),
                                1,
                                empty)
                        .orElseThrow()
                        .contains("schedules=0"));

        assertTrue(
                helper
                        .rebuildFailure(
                                context(),
                                1,
                                new RebuildPlanDayResponse(
                                        true,
                                        "",
                                        List.of(day(2))))
                        .orElseThrow()
                        .contains("dayNumber=2"));
    }

    @Test
    @DisplayName("대상 날짜 하나의 정상 응답은 구조 검증 통과")
    void acceptsSingleTargetDay() {

        assertTrue(
                helper
                        .rebuildFailure(
                                context(),
                                1,
                                new RebuildPlanDayResponse(
                                        true,
                                        "",
                                        List.of(day(1))))
                        .isEmpty());
    }

    @Test
    @DisplayName("날짜 재구성 프롬프트는 전체 편집 지시 없이 대상 날짜와 실패 계약 제공")
    void promptHasIndependentDayContract() throws Exception {

        RebuildPlanDayPrompt prompt = new RebuildPlanDayPrompt(
                context(),
                new CreatePlanAiResponse(
                        List.of(
                                new CreatePlanAiResponse.PlanDayDetail(
                                        1,
                                        date,
                                        List.of()))),
                1,
                "이전 응답 날짜 오류",
                new AppConfig().objectMapper());

        assertFalse(prompt.system().contains("[STEP 2. 변경 대상과 유지 대상 분리]"));

        assertTrue(prompt.system().contains("rebuilt=false"));

        assertTrue(prompt.system().contains("구체적인 장소명 또는 음식명만"));

        assertEquals(
                1,
                new AppConfig()
                        .objectMapper()
                        .readTree(prompt.user())
                        .get("targetDay")
                        .get("dayNumber")
                        .asInt());
    }

    private CreatePlanAiResponse.PlanDayDetail day(int number) {

        return new CreatePlanAiResponse.PlanDayDetail(
                number,
                date.plusDays(number - 1),
                List.of(mock(CreatePlanAiResponse.PlanScheduleDetail.class)));
    }

    private PlanEditContext context() {

        return new PlanEditContext(
                null,
                List.of(),
                new GetAiPlanResponse(
                        "테스트",
                        null,
                        null,
                        List.of(),
                        List.of(),
                        Set.of(),
                        List.of(
                                new GetAiPlanResponse.PlanDayDetail(
                                        1,
                                        date,
                                        List.of()))),
                "1일차 통째로 재구성");
    }
}
