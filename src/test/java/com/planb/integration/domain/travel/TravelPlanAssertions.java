package com.planb.integration.domain.travel;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

final class TravelPlanAssertions {

    // 식중 복약 정의에 따라 겹침 판정에서 제외하는 식사 슬롯
    private static final Set<String> MEAL_SCHEDULE_TYPES = Set.of(
            "BREAKFAST",
            "LUNCH",
            "DINNER"
    );

    private TravelPlanAssertions() {
    }

    static void assertPlan(
            JsonNode plan,
            LocalDate startDate,
            boolean candidateIdsExpected
    ) {

        assertThat(plan
                        .path("planDays")
                .isArray())
                .isTrue();
        assertThat(plan
                        .path("planDays")
                .size())
                .isEqualTo(2);

        Set<String> places = new HashSet<>();
        Set<String> menus = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();

        for (JsonNode day : plan.path("planDays")) {
            int number = day
                    .path("dayNumber")
                    .asInt();

            assertThat(number)
                    .isBetween(1, 2);
            assertThat(numbers.add(number))
                    .isTrue();
            assertThat(day
                            .path("date")
                    .asText())
                    .isEqualTo(startDate
                            .plusDays(number - 1)
                    .toString());
            assertThat(day
                            .path("schedules")
                    .isArray())
                    .isTrue();
            assertThat(day
                            .path("schedules")
                    .size())
                    .isPositive();

            for (JsonNode slot : day.path("schedules")) {
                String type = code(slot.path("courseType"));
                String scheduleType = code(slot.path("scheduleType"));
                LocalTime start = LocalTime.parse(slot
                                .path("startTime")
                        .asText());
                LocalTime end = LocalTime.parse(slot
                                .path("endTime")
                        .asText());

                assertThat(end)
                        .isAfter(start);
                assertThat(slot
                                .path("tags")
                        .isArray())
                        .isTrue();
                assertThat(slot
                                .path("stayMinutes")
                        .isNumber())
                        .isTrue();

                if ("MEDICATION".equals(type)) {
                    assertThat(scheduleType)
                            .isEqualTo("CHECK_IN");
                    assertThat(slot
                                    .path("medication")
                            .isObject())
                            .isTrue();
                    assertThat(slot
                                    .path("medication")
                            .path("description")
                            .asText())
                            .isNotBlank();
                    assertThat(codes(slot.path("tags")))
                            .contains("MEDICATION_SCHEDULE");
                    continue;
                }

                if ("TRANSPORTATION".equals(type)) {
                    assertThat(scheduleType)
                            .isEqualTo("ACTIVITY");
                    assertThat(slot
                                    .path("medication")
                            .isNull() || slot
                                    .path("medication")
                            .isMissingNode())
                            .isTrue();
                    continue;
                }

                assertThat(slot
                                .path("medication")
                        .isNull() || slot
                                .path("medication")
                        .isMissingNode())
                        .isTrue();

                assertThat(slot
                                .path("locationName")
                        .asText())
                        .isNotBlank();
                assertThat(slot
                                .path("location")
                        .asText())
                        .isNotBlank();
                assertThat(places.add(slot
                                        .path("locationName")
                        .asText()
                        .strip()))
                        .isTrue();
                assertThat(slot
                                .path("stayMinutes")
                        .asInt())
                        .isEqualTo((int) Duration
                                .between(start, end)
                        .toMinutes());
                assertThat(slot
                                .path("travelMinutes")
                        .isNumber())
                        .isTrue();
                assertThat(slot
                                .path("travelMinutes")
                        .asInt())
                        .isNotNegative();

                // 이번 검색 후보가 아닌 보존 날짜 슬롯의 candidateId 부재
                // 값이 있는 경우 검색 원본 식별자 형식 필수
                if (candidateIdsExpected && !slot
                        .path("candidateId")
                        .asText()
                        .isEmpty()) {
                    assertThat(slot
                                    .path("candidateId")
                            .asText())
                            .matches("(tour|kakao):.+");
                }

                if ("RESTAURANT".equals(type) || "LOCAL_FOOD".equals(type)) {
                    assertThat(scheduleType)
                            .isIn(
                            "BREAKFAST",
                            "LUNCH",
                            "DINNER"
                    );
                    JsonNode restaurant = slot.path("restaurantDetail");

                    assertThat(restaurant.isObject())
                            .isTrue();
                    assertThat(restaurant
                                    .path("menuName")
                            .asText())
                            .isNotBlank();
                    assertThat(menus.add(restaurant
                                            .path("menuName")
                            .asText()
                            .strip()))
                            .isTrue();
                    assertThat(restaurant
                                    .path("address")
                            .asText())
                            .isNotBlank();
                    assertCoordinates(restaurant);
                    assertThat(restaurant.path("address"))
                            .isEqualTo(slot.path("location"));
                } else {
                    assertThat(type)
                            .isIn(
                            "ATTRACTION",
                            "CAFE_REST",
                            "PARK_WALK",
                            "MUST_HAVE"
                    );
                    assertThat(scheduleType)
                            .isEqualTo("ACTIVITY");
                    assertThat(slot
                                    .path("restaurantDetail")
                            .isNull())
                            .isTrue();
                }
            }
        }

        if (plan.has("tags")) {
            Set<String> expected = new HashSet<>();

            for (JsonNode day : plan.path("planDays")) {
                for (JsonNode slot : day.path("schedules")) {
                    expected.addAll(codes(slot.path("tags")));
                }
            }

            assertThat(codes(plan.path("tags")))
                    .isEqualTo(expected);
        }
    }

    static void assertMealMedication(
            JsonNode plan,
            LocalTime fallbackLunchTime
    ) {

        for (JsonNode day : plan.path("planDays")) {
            LocalTime actualMealEndTime = null;

            List<JsonNode> medications = new ArrayList<>();
            List<JsonNode> placeSlots = new ArrayList<>();

            for (JsonNode slot : day.path("schedules")) {
                if (actualMealEndTime == null
                        && "LUNCH".equals(code(slot.path("scheduleType")))) {
                    actualMealEndTime = LocalTime.parse(
                            slot
                                    .path("endTime")
                                    .asText()
                    );
                }

                if ("MEDICATION".equals(code(slot.path("courseType")))) {
                    medications.add(slot);

                    continue;
                }

                if (!MEAL_SCHEDULE_TYPES.contains(code(slot.path("scheduleType")))) {
                    placeSlots.add(slot);
                }
            }

            // 식사 종료를 기준으로 한 식후 복약
            // 식사 슬롯 부재 시 설정 시각에 기본 60분을 더한 기준
            LocalTime mealBased = actualMealEndTime == null
                    ? fallbackLunchTime.plusMinutes(90)
                    : actualMealEndTime.plusMinutes(30);

            // 장소 시간대에 겹치는 복약의 장소 종료 후 배치
            // 배치 결과의 기준시각 이상 유지
            boolean overlapsPlace = placeSlots
                    .stream()
                    .anyMatch(slot -> covers(slot, mealBased));

            assertThat(medications)
                    .anySatisfy(slot -> {
                LocalTime medicationTime = LocalTime.parse(slot
                                .path("startTime")
                        .asText());

                if (overlapsPlace) {
                    assertThat(medicationTime)
                            .isAfterOrEqualTo(mealBased);

                    assertThat(placeSlots)
                            .noneMatch(place -> covers(place, medicationTime));
                } else {
                    assertThat(medicationTime)
                            .isEqualTo(mealBased);
                }

                assertThat(slot
                                .path("medication")
                        .path("intervalMinutes")
                        .asInt())
                        .isEqualTo(30);
            });
        }
    }

    // 종료시각을 제외한 장소 슬롯의 시각 포함 여부
    private static boolean covers(
            JsonNode placeSlot,
            LocalTime time
    ) {

        JsonNode startTime = placeSlot.path("startTime");
        JsonNode endTime = placeSlot.path("endTime");

        if (startTime.isMissingNode() || endTime.isMissingNode()
                || startTime.isNull() || endTime.isNull()) {
            return false;
        }

        LocalTime start = LocalTime.parse(startTime.asText());
        LocalTime end = LocalTime.parse(endTime.asText());

        return !time.isBefore(start) && time.isBefore(end);
    }

    static void assertSameDays(JsonNode expected, JsonNode actual) {

        assertThat(actual.size())
                .isEqualTo(expected.size());

        for (JsonNode day : expected) {
            JsonNode found = null;

            for (JsonNode candidate : actual) {
                if (candidate
                        .path("dayNumber")
                        .asInt() == day
                                .path("dayNumber")
                        .asInt()) {
                    found = candidate;
                    break;
                }
            }

            assertThat(found)
                    .isNotNull();
            assertThat(found.path("date"))
                    .isEqualTo(day.path("date"));
            assertThat(snapshots(found.path("schedules")))
                    .containsExactlyInAnyOrderElementsOf(snapshots(day.path("schedules")));
        }
    }

    /**
     * 날짜별 첫 장소의 이동시간을 제외한 보존 날짜 비교
     */
    static void assertSameDaysIgnoringInboundTravel(
            JsonNode expected,
            JsonNode actual
    ) {

        assertSameDays(withoutInboundTravel(expected), withoutInboundTravel(actual));
    }

    private static JsonNode withoutInboundTravel(JsonNode days) {

        ArrayNode result = JsonNodeFactory.instance.arrayNode();

        for (JsonNode day : days) {
            ObjectNode copy = (ObjectNode) day.deepCopy();

            for (JsonNode slot : copy.path("schedules")) {
                JsonNode locationName = slot.path("locationName");

                if (locationName.isString() && !locationName
                        .asString()
                        .isBlank()) {
                    ((ObjectNode) slot).remove("travelMinutes");

                    break;
                }
            }

            result.add(copy);
        }

        return result;
    }

    static Set<String> codes(JsonNode tags) {

        Set<String> result = new HashSet<>();

        for (JsonNode tag : tags) {
            result.add(code(tag));
        }

        return result;
    }

    static String code(JsonNode value) {

        return value.isObject() ? value
                .path("code")
                .asText() : value.asText();
    }

    private static void assertCoordinates(JsonNode restaurant) {

        assertThat(restaurant
                        .path("longitude")
                .asText())
                .isNotBlank();
        assertThat(restaurant
                        .path("latitude")
                .asText())
                .isNotBlank();
        double x = Double.parseDouble(restaurant
                        .path("longitude")
                .asText());
        double y = Double.parseDouble(restaurant
                        .path("latitude")
                .asText());

        assertThat(Double.isFinite(x) && x != 0 && Math.abs(x) <= 180)
                .isTrue();
        assertThat(Double.isFinite(y) && y != 0 && Math.abs(y) <= 90)
                .isTrue();
    }

    private static List<JsonNode> snapshots(JsonNode schedules) {

        List<JsonNode> result = new ArrayList<>();

        for (JsonNode slot : schedules) {
            ObjectNode copy = (ObjectNode) slot.deepCopy();
            copy.remove("candidateId");
            List<String> tags = codes(slot.path("tags"))
                    .stream()
                    .sorted()
                    .toList();

            copy.putArray("tags");

            for (String tag : tags) {
                copy
                        .withArray("tags")
                        .add(tag);
            }

            result.add(copy);
        }

        return result;
    }
}
