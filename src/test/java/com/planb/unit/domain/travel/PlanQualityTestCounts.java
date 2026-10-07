package com.planb.unit.domain.travel;

import com.planb.integration.domain.travel.PlanQualityCounts;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanQualityTestCounts {

    private static final JsonMapper MAPPER = JsonMapper
            .builder()
            .build();

    // 기준선 raw 응답과 같은 구조의 1박 2일 일정
    private static final String PLAN = """
            {"planDays": [
              {"dayNumber": 1, "schedules": [
                {"courseType": "MUST_HAVE", "locationName": "경복궁", "candidateId": "kakao:1",
                 "travelMinutes": 20, "tags": ["MUST_VISIT", "HISTORY_CULTURE"]},
                {"courseType": "RESTAURANT", "locationName": "해물칼국수집", "candidateId": "tour:10",
                 "travelMinutes": 10, "tags": [],
                 "restaurantDetail": {"menuName": "해물 칼국수", "carbohydrate": 40.1}},
                {"courseType": "ATTRACTION", "locationName": "창덕궁", "candidateId": "tour:2",
                 "travelMinutes": 15, "tags": ["HISTORY_CULTURE"]},
                {"courseType": "MEDICATION", "locationName": null, "candidateId": null,
                 "travelMinutes": null, "tags": ["MEDICATION_SCHEDULE"]}
              ]},
              {"dayNumber": 2, "schedules": [
                {"courseType": "CAFE_REST", "locationName": "종로 카페", "candidateId": "kakao:3",
                 "travelMinutes": 5, "tags": ["REST_POINT"]},
                {"courseType": "ATTRACTION", "locationName": "창덕궁", "candidateId": "tour:2",
                 "travelMinutes": 25, "tags": []},
                {"courseType": "RESTAURANT", "locationName": "삼계탕집", "candidateId": "tour:11",
                 "travelMinutes": 7, "tags": [],
                 "restaurantDetail": {"menuName": "삼계탕", "carbohydrate": null}}
              ]}
            ]}
            """;

    @Test
    @DisplayName("기준선 응답에서 지정 장소·중복·알레르기·카페·태그·이동시간·영양 채움 계수")
    void countsQualityFromPlan() {

        JsonNode plan = MAPPER.readTree(PLAN);

        PlanQualityCounts counts = PlanQualityCounts.count(
                plan,
                "경복궁",
                List.of(
                        "새우",
                        "해물"
                )
        );

        assertTrue(counts.plannedPlaceIncluded());
        assertEquals(1, counts.mustHaveSlots());
        assertEquals(1, counts.crossDayDuplicatePlaces());
        assertEquals(1, counts.allergenHits());
        assertEquals(1, counts.cafeRestSlots());
        assertEquals(3, counts.attractionTags());
        assertEquals(82, counts.totalTravelMinutes());
        assertEquals(2, counts.restaurantSlots());
        assertEquals(1, counts.nutritionFilled());
    }

    @Test
    @DisplayName("지정 장소는 공백 정규화 후 완전 일치만 포함으로 계수")
    void plannedPlaceRequiresExactNormalizedName() {

        JsonNode plan = MAPPER.readTree(PLAN);

        assertTrue(PlanQualityCounts
                .count(
                        plan,
                        "경 복 궁",
                        List.of()
                )
                .plannedPlaceIncluded());

        // 기준선 C3처럼 경포대 지정이 경포해수욕장으로 대체된 경우의 미포함 판정
        assertFalse(PlanQualityCounts
                .count(
                        plan,
                        "경복",
                        List.of()
                )
                .plannedPlaceIncluded());
    }

    @Test
    @DisplayName("TourAPI 제목의 여행 시군 접두어와 괄호 설명을 걷어낸 지정 장소 포함 계수")
    void plannedPlaceAcceptsTourApiTitleFormat() {

        // 2026-10-07 R0 재측정 raw 응답의 실제 장소명 형식
        JsonNode plan = MAPPER.readTree("""
                {"planDays": [
                  {"dayNumber": 1, "schedules": [
                    {"courseType": "ATTRACTION", "locationName": "경주 불국사 [유네스코 세계유산]",
                     "candidateId": "tour:126166", "travelMinutes": 20, "tags": []}
                  ]}
                ]}
                """);

        assertTrue(PlanQualityCounts
                .count(
                        plan,
                        "불국사",
                        "경주시",
                        List.of()
                )
                .plannedPlaceIncluded());

        // 다른 시군 접두어는 같은 이름이어도 미포함
        assertFalse(PlanQualityCounts
                .count(
                        plan,
                        "불국사",
                        "강릉시",
                        List.of()
                )
                .plannedPlaceIncluded());
    }

    @Test
    @DisplayName("메뉴명은 공백·괄호·구분자 정규화 후 상세 메뉴 항목에 포함되면 출처 일치")
    void menuMatchesNormalizedDetailMenu() {

        assertTrue(PlanQualityCounts.menuMatches(
                "영양솥밥",
                "교동쌈밥 한정식 (영양솥밥, 생선구이)",
                null
        ));

        assertTrue(PlanQualityCounts.menuMatches(
                "삼계탕",
                null,
                "삼계탕+전복삼계탕"
        ));

        assertFalse(PlanQualityCounts.menuMatches(
                "불고기",
                "삼계탕",
                "전복죽"
        ));

        assertFalse(PlanQualityCounts.menuMatches(
                "불고기",
                null,
                null
        ));
    }
}
