package com.planb.integration.domain.travel;

import tools.jackson.databind.JsonNode;
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

    private TravelPlanAssertions() {
    }

    static void assertPlan(
            JsonNode plan,
            LocalDate startDate,
            boolean candidateIdsExpected
    ) {

        assertThat(plan.path("planDays")
                .isArray())
                .isTrue();
        assertThat(plan.path("planDays")
                .size())
                .isEqualTo(2);

        Set<String> places = new HashSet<>();
        Set<String> menus = new HashSet<>();
        Set<Integer> numbers = new HashSet<>();

        for (JsonNode day : plan.path("planDays")) {
            int number = day.path("dayNumber")
                    .asInt();

            assertThat(number)
                    .isBetween(1, 2);
            assertThat(numbers.add(number))
                    .isTrue();
            assertThat(day.path("date")
                    .asText())
                    .isEqualTo(startDate.plusDays(number - 1)
                    .toString());
            assertThat(day.path("schedules")
                    .isArray())
                    .isTrue();
            assertThat(day.path("schedules")
                    .size())
                    .isPositive();

            for (JsonNode slot : day.path("schedules")) {
                String type = code(slot.path("courseType"));
                String scheduleType = code(slot.path("scheduleType"));
                LocalTime start = LocalTime.parse(slot.path("startTime")
                        .asText());
                LocalTime end = LocalTime.parse(slot.path("endTime")
                        .asText());

                assertThat(end)
                        .isAfter(start);
                assertThat(slot.path("tags")
                        .isArray())
                        .isTrue();
                assertThat(slot.path("stayMinutes")
                        .isNumber())
                        .isTrue();

                if ("MEDICATION".equals(type)) {
                    assertThat(scheduleType)
                            .isEqualTo("CHECK_IN");
                    assertThat(slot.path("medication")
                            .isObject())
                            .isTrue();
                    assertThat(slot.path("medication")
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
                    assertThat(slot.path("medication")
                            .isNull() || slot.path("medication")
                            .isMissingNode())
                            .isTrue();
                    continue;
                }

                assertThat(slot.path("medication")
                        .isNull() || slot.path("medication")
                        .isMissingNode())
                        .isTrue();

                assertThat(slot.path("locationName")
                        .asText())
                        .isNotBlank();
                assertThat(slot.path("location")
                        .asText())
                        .isNotBlank();
                assertThat(places.add(slot.path("locationName")
                        .asText()
                        .strip()))
                        .isTrue();
                assertThat(slot.path("stayMinutes")
                        .asInt())
                        .isEqualTo((int) Duration.between(start, end)
                        .toMinutes());
                assertThat(slot.path("travelMinutes")
                        .isNumber())
                        .isTrue();
                assertThat(slot.path("travelMinutes")
                        .asInt())
                        .isNotNegative();

                if (candidateIdsExpected) {
                    assertThat(slot.path("candidateId")
                            .asText())
                            .matches("(tour|kakao):.+");
                }

                if ("RESTAURANT".equals(type) || "LOCAL_FOOD".equals(type)) {
                    assertThat(scheduleType)
                            .isIn("BREAKFAST", "LUNCH", "DINNER");
                    JsonNode restaurant = slot.path("restaurantDetail");

                    assertThat(restaurant.isObject())
                            .isTrue();
                    assertThat(restaurant.path("menuName")
                            .asText())
                            .isNotBlank();
                    assertThat(menus.add(restaurant.path("menuName")
                            .asText()
                            .strip()))
                            .isTrue();
                    assertThat(restaurant.path("address")
                            .asText())
                            .isNotBlank();
                    assertCoordinates(restaurant);
                    assertThat(restaurant.path("address"))
                            .isEqualTo(slot.path("location"));
                } else {
                    assertThat(type)
                            .isIn("ATTRACTION", "CAFE_REST", "PARK_WALK", "MUST_HAVE");
                    assertThat(scheduleType)
                            .isEqualTo("ACTIVITY");
                    assertThat(slot.path("restaurantDetail")
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
            LocalTime actualMealTime = null;

            List<JsonNode> medications = new ArrayList<>();

            for (JsonNode slot : day.path("schedules")) {
                if (actualMealTime == null
                        && "LUNCH".equals(code(slot.path("scheduleType")))) {
                    actualMealTime = LocalTime.parse(
                            slot.path("startTime")
                                    .asText()
                    );
                }

                if ("MEDICATION".equals(code(slot.path("courseType")))) {
                    medications.add(slot);
                }
            }

            LocalTime expected = (actualMealTime == null
                    ? fallbackLunchTime
                    : actualMealTime)
                    .plusMinutes(30);

            assertThat(medications)
                    .anySatisfy(slot -> {
                assertThat(LocalTime.parse(slot.path("startTime")
                        .asText()))
                        .isEqualTo(expected);
                assertThat(slot.path("medication")
                        .path("intervalMinutes")
                        .asInt())
                        .isEqualTo(30);
            });
        }
    }

    static void assertSameDays(JsonNode expected, JsonNode actual) {

        assertThat(actual.size())
                .isEqualTo(expected.size());

        for (JsonNode day : expected) {
            JsonNode found = null;

            for (JsonNode candidate : actual) {
                if (candidate.path("dayNumber")
                        .asInt() == day.path("dayNumber")
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

    static Set<String> codes(JsonNode tags) {

        Set<String> result = new HashSet<>();

        for (JsonNode tag : tags) {
            result.add(code(tag));
        }

        return result;
    }

    static String code(JsonNode value) {

        return value.isObject() ? value.path("code")
                .asText() : value.asText();
    }

    private static void assertCoordinates(JsonNode restaurant) {

        assertThat(restaurant.path("longitude")
                .asText())
                .isNotBlank();
        assertThat(restaurant.path("latitude")
                .asText())
                .isNotBlank();
        double x = Double.parseDouble(restaurant.path("longitude")
                .asText());
        double y = Double.parseDouble(restaurant.path("latitude")
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
                copy.withArray("tags")
                        .add(tag);
            }

            result.add(copy);
        }

        return result;
    }
}
