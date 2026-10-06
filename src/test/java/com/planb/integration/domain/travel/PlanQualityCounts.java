package com.planb.integration.domain.travel;

import tools.jackson.databind.JsonNode;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 생성 응답의 품질 계수
 *
 * TravelPlanAssertions의 구조 invariant 밖에 있는
 * 지정 장소 포함, 날짜 간 중복, 알레르기 키워드, 카페·태그·이동시간·영양 채움 계수
 */
public record PlanQualityCounts(
        boolean plannedPlaceIncluded,
        int mustHaveSlots,
        int crossDayDuplicatePlaces,
        int allergenHits,
        int cafeRestSlots,
        int attractionTags,
        int totalTravelMinutes,
        int restaurantSlots,
        int nutritionFilled
) {

    private static final Set<String> TOURIST_COURSE_TYPES = Set.of(
            "ATTRACTION",
            "MUST_HAVE"
    );

    public static PlanQualityCounts count(
            JsonNode plan,
            String plannedPlace,
            List<String> allergenKeywords
    ) {

        String planned = normalize(plannedPlace);

        boolean plannedPlaceIncluded = false;
        int mustHaveSlots = 0;
        int allergenHits = 0;
        int cafeRestSlots = 0;
        int attractionTags = 0;
        int totalTravelMinutes = 0;
        int restaurantSlots = 0;
        int nutritionFilled = 0;

        Map<String, Set<Integer>> placeDays = new HashMap<>();

        for (JsonNode day : plan.path("planDays")) {
            int dayNumber = day
                    .path("dayNumber")
                    .asInt();

            for (JsonNode slot : day.path("schedules")) {
                String courseType = slot
                        .path("courseType")
                        .asText();
                String locationName = text(slot.path("locationName"));

                if (locationName != null && normalize(locationName).equals(planned)) {
                    plannedPlaceIncluded = true;
                }

                if ("MUST_HAVE".equals(courseType)) {
                    mustHaveSlots++;
                }

                if ("CAFE_REST".equals(courseType)) {
                    cafeRestSlots++;
                }

                if (TOURIST_COURSE_TYPES.contains(courseType)) {
                    attractionTags += slot
                            .path("tags")
                            .size();
                }

                if (slot
                        .path("travelMinutes")
                        .isNumber()) {
                    totalTravelMinutes += slot
                            .path("travelMinutes")
                            .asInt();
                }

                String placeKey = placeKey(slot, locationName);

                if (placeKey != null && !"MEDICATION".equals(courseType)) {
                    placeDays
                            .computeIfAbsent(
                                    placeKey,
                                    key -> new HashSet<>()
                            )
                            .add(dayNumber);
                }

                JsonNode detail = slot.path("restaurantDetail");

                if (detail.isMissingNode() || detail.isNull()) {
                    continue;
                }

                restaurantSlots++;

                if (detail
                        .path("carbohydrate")
                        .isNumber()) {
                    nutritionFilled++;
                }

                if (containsAny(
                        text(detail.path("menuName")),
                        allergenKeywords
                ) || containsAny(
                        locationName,
                        allergenKeywords
                )) {
                    allergenHits++;
                }
            }
        }

        int crossDayDuplicatePlaces = (int) placeDays
                .values()
                .stream()
                .filter(days -> days.size() > 1)
                .count();

        return new PlanQualityCounts(
                plannedPlaceIncluded,
                mustHaveSlots,
                crossDayDuplicatePlaces,
                allergenHits,
                cafeRestSlots,
                attractionTags,
                totalTravelMinutes,
                restaurantSlots,
                nutritionFilled
        );
    }

    /**
     * 메뉴명의 음식점 상세 메뉴 포함 여부
     * 공백 제거, 괄호·쉼표·플러스·슬래시 기준 항목 분리 후 2글자 이상 항목과 양방향 포함 비교
     */
    public static boolean menuMatches(
            String menuName,
            String firstMenu,
            String treatMenu
    ) {

        String menu = normalize(menuName);

        if (menu.isEmpty()) {
            return false;
        }

        return menuItems(firstMenu)
                .stream()
                .anyMatch(item -> item.contains(menu) || menu.contains(item))
                || menuItems(treatMenu)
                .stream()
                .anyMatch(item -> item.contains(menu) || menu.contains(item));
    }

    private static List<String> menuItems(String detailMenu) {

        if (detailMenu == null) {
            return List.of();
        }

        return Arrays
                .stream(detailMenu.split("[(),+/·]"))
                .map(PlanQualityCounts::normalize)
                .filter(item -> item.length() >= 2)
                .toList();
    }

    // 후보 ID 우선, 없으면 정규화한 장소명
    private static String placeKey(
            JsonNode slot,
            String locationName
    ) {

        String candidateId = text(slot.path("candidateId"));

        if (candidateId != null) {
            return candidateId;
        }

        return locationName == null
                ? null
                : normalize(locationName);
    }

    private static boolean containsAny(
            String value,
            List<String> keywords
    ) {

        if (value == null) {
            return false;
        }

        return keywords
                .stream()
                .anyMatch(value::contains);
    }

    private static String text(JsonNode node) {

        return node.isMissingNode() || node.isNull() || node
                .asText()
                .isBlank()
                ? null
                : node.asText();
    }

    private static String normalize(String value) {

        return value == null
                ? ""
                : value.replaceAll("\\s+", "");
    }
}
