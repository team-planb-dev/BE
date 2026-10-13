package com.planb.unit.domain.travel.service;

import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.health.entity.constant.MealTiming;
import com.planb.domain.health.entity.constant.MedicationBasis;
import com.planb.domain.health.entity.constant.RelatedMeal;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.domain.travel.service.ScheduleNormalizer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 직전 식사 조정의 시간·복약 계약
 */
class MealTimeRecoveryContractTest {

    private final ScheduleNormalizer normalizer =
            new ScheduleNormalizer(new PlanPlaceResolver());

    @ParameterizedTest
    @CsvSource({"1, 12", "2, 11"})
    void recoversLateLunchByMovingPreviousMealWithinWindow(
            int day,
            int minutes
    ) {

        CreatePlanAiResponse input = fixture(day);
        CreatePlanAiResponse result = finish(input);

        assertEquals(
                LocalTime.of(8, 0)
                        .minusMinutes(minutes),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(slot -> slot.scheduleType() == ScheduleType.BREAKFAST)
                        .findFirst()
                        .orElseThrow()
                        .startTime()
        );

        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
        assertMedicationContract(result);
        assertEquals(result, finish(result));
        List<String> originalNames = input
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
        List<String> resultNames = result
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.courseType() != CourseType.MEDICATION)
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
        assertEquals(originalNames, resultNames);
        List<CreatePlanAiResponse.PlanScheduleDetail> resultingPlaces = result
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.courseType() != CourseType.MEDICATION)
                .toList();
        List<CreatePlanAiResponse.PlanScheduleDetail> originalPlaces = input
                .planDays()
                .getFirst()
                .schedules();
        for (int index = 0; index < originalPlaces.size(); index++) {
            assertEquals(
                    originalPlaces
                            .get(index)
                            .stayMinutes(),
                    resultingPlaces
                            .get(index)
                            .stayMinutes()
            );
            assertEquals(
                    originalPlaces
                            .get(index)
                            .travelMinutes(),
                    resultingPlaces
                            .get(index)
                            .travelMinutes()
            );
            CreatePlanAiResponse.PlanScheduleDetail current = resultingPlaces.get(index);
            assertEquals(
                    current.stayMinutes().longValue(),
                    Duration
                            .between(current.startTime(), current.endTime())
                            .toMinutes()
            );
            if (index > 0 && current.travelMinutes() != null) {
                LocalTime earliest = resultingPlaces
                        .get(index - 1)
                        .endTime()
                        .plusMinutes(current.travelMinutes());
                assertFalse(current.startTime().isBefore(earliest));
            }
        }
        for (CreatePlanAiResponse.PlanScheduleDetail meal : resultingPlaces) {
            LocalTime configured = switch (meal.scheduleType()) {
                case BREAKFAST -> LocalTime.of(8, 0);
                case LUNCH -> LocalTime.of(12, 0);
                case DINNER -> LocalTime.of(18, 0);
                default -> null;
            };
            if (configured != null) {
                assertFalse(meal.startTime().isBefore(configured.minusMinutes(30)));
                assertFalse(meal.startTime().isAfter(configured.plusMinutes(30)));
            }
        }
    }

    @Test
    void recoversAdjacentMealsWithoutChangingTravelTime() {

        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        null
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, contexts());

        assertEquals(
                LocalTime.of(7, 40),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
        assertEquals(200, lunch(result).travelMinutes());
        assertEquals(
                LocalTime.of(8, 0),
                input
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
    }

    @Test
    void preservesPreviousMealWhenIncomingPlacePreventsEarlierStart() {

        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "앞 장소",
                        "07:00",
                        40,
                        null
                ),
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        20
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, contexts());

        assertEquals(
                LocalTime.of(8, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .get(1)
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 50), lunch(result).startTime());
        assertEquals(
                LocalTime.of(7, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
    }

    @Test
    void respectsEveryCompanionsMealWindow() {

        TravelHealthContext laterBreakfast = new TravelHealthContext(
                "늦은 아침 동행인",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 20),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        );
        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        null
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, List.of(contexts().getFirst(), laterBreakfast));

        assertEquals(
                LocalTime.of(8, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 50), lunch(result).startTime());
    }

    @Test
    void shiftsPreviousMealOnlyByMissingSlack() {

        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "08:00",
                        90,
                        null
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "관광지",
                        "09:35",
                        90,
                        0
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "12:00",
                        50,
                        95
                )
        ));
        CreatePlanAiResponse result = normalizer
                .normalizeScheduleTimes(input, contexts());

        assertEquals(
                LocalTime.of(7, 55),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .getFirst()
                        .startTime()
        );
        assertEquals(
                LocalTime.of(9, 25),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .get(1)
                        .startTime()
        );
        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
    }

    @Test
    void refusesRecoveryThatMovesBeforeMealMedicationToPreviousDay() {

        List<TravelHealthContext> earlyMeals = List.of(new TravelHealthContext(
                "이른 식사 여행자",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(0, 45),
                        LocalTime.of(4, 40),
                        null
                ),
                List.of(),
                List.of(new TravelHealthContext.MedicationInfoContext(
                        "식전 복약",
                        MedicationBasis.WITH_MEAL,
                        null,
                        Set.of(new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                RelatedMeal.BREAKFAST,
                                MealTiming.BEFORE_MEAL,
                                30
                        ))
                ))
        ));
        CreatePlanAiResponse input = response(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침",
                        "00:45",
                        90,
                        null
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심",
                        "04:40",
                        50,
                        200
                )
        ));
        CreatePlanAiResponse normalized = normalizer
                .normalizeScheduleTimes(input, earlyMeals);
        CreatePlanAiResponse result = normalizer
                .finalizeMedicationSchedules(normalized, earlyMeals);

        assertEquals(LocalTime.of(5, 35), lunch(result).startTime());
        assertEquals(
                LocalTime.of(0, 15),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow()
                        .startTime()
        );
    }

    @Test
    void preservesFixedMedicationWithoutRelatedMealDuringRecovery() {

        TravelHealthContext traveler = contexts()
                .getFirst();
        List<TravelHealthContext> fixedMedication = List.of(new TravelHealthContext(
                traveler.travelerName(),
                traveler.diseaseTypes(),
                traveler.walkType(),
                traveler.mealInfo(),
                traveler.foodInfos(),
                List.of(new TravelHealthContext.MedicationInfoContext(
                        "고정 복약",
                        MedicationBasis.WITH_MEAL,
                        LocalTime.of(14, 0),
                        Set.of(new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                null,
                                MealTiming.REGARDLESS_OF_MEAL,
                                null
                        ))
                ))
        ));
        CreatePlanAiResponse normalized = normalizer
                .normalizeScheduleTimes(fixture(1), fixedMedication);
        CreatePlanAiResponse result = normalizer
                .finalizeMedicationSchedules(normalized, fixedMedication);

        assertEquals(LocalTime.of(12, 30), lunch(result).startTime());
        assertEquals(
                LocalTime.of(14, 0),
                result
                        .planDays()
                        .getFirst()
                        .schedules()
                        .stream()
                        .filter(schedule -> schedule.courseType() == CourseType.MEDICATION)
                        .findFirst()
                        .orElseThrow()
                        .startTime()
        );
    }

    private CreatePlanAiResponse response(List<CreatePlanAiResponse.PlanScheduleDetail> schedules) {

        return new CreatePlanAiResponse(List.of(
                new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.of(2026, 10, 19),
                        schedules
                )
        ));
    }

    @Test
    @DisplayName("실측 C6 1회차: 장소 사이 여유 압축과 직전 식사 앞당김으로 점심 허용 창 충족")
    void recoversLunchByCompactingSlackBeforeShiftingBreakfast() {

        CreatePlanAiResponse input = c6LiveDay();

        CreatePlanAiResponse result = finish(input);

        LocalTime breakfast = meal(
                result,
                ScheduleType.BREAKFAST
        )
                .startTime();

        assertEquals(
                LocalTime.of(7, 30),
                breakfast
        );
        assertEquals(
                LocalTime.of(8, 57),
                startOf(
                        result,
                        "경복궁"
                )
        );
        assertEquals(
                LocalTime.of(10, 59),
                startOf(
                        result,
                        "결"
                )
        );
        assertEquals(
                LocalTime.of(12, 30),
                lunch(result)
                        .startTime()
        );
        assertTravelGapsKept(result);
        assertMedicationContract(result);
        assertEquals(
                result,
                finish(result)
        );
        assertEquals(
                names(input),
                names(result)
        );
    }

    @Test
    @DisplayName("실측 C3 1회차: 고정 선택으로 점심 창 충족 불가한 장거리 오전 일정은 기존 배치 유지")
    void keepsLateLunchWhenLongMorningTripCannotFit() {

        CreatePlanAiResponse result = finish(dayOf(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "금천칼국수",
                        "08:00:00",
                        50,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오대산 소금강계곡",
                        "10:00:00",
                        90,
                        109
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "강릉불고기 초당점",
                        "12:00:00",
                        50,
                        87
                )
        )));

        assertEquals(
                LocalTime.of(8, 0),
                meal(
                        result,
                        ScheduleType.BREAKFAST
                )
                        .startTime()
        );
        assertEquals(
                LocalTime.of(13, 36),
                lunch(result)
                        .startTime()
        );
        assertTravelGapsKept(result);
    }

    @Test
    @DisplayName("장소 사이 여유 압축만으로 충족되면 직전 식사 유지")
    void compactsSlackWithoutShiftingBreakfast() {

        CreatePlanAiResponse result = finish(dayOf(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침 식당",
                        "08:00:00",
                        50,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 A",
                        "09:00:00",
                        60,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 B",
                        "10:40:00",
                        90,
                        10
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심 식당",
                        "12:50:00",
                        50,
                        40
                )
        )));

        assertEquals(
                LocalTime.of(8, 0),
                meal(
                        result,
                        ScheduleType.BREAKFAST
                )
                        .startTime()
        );
        assertEquals(
                LocalTime.of(9, 0),
                startOf(
                        result,
                        "오전 관광지 A"
                )
        );
        assertEquals(
                LocalTime.of(10, 20),
                startOf(
                        result,
                        "오전 관광지 B"
                )
        );
        assertEquals(
                LocalTime.of(12, 30),
                lunch(result)
                        .startTime()
        );
        assertTravelGapsKept(result);
    }

    @Test
    @DisplayName("뒤 장소 여유만으로 충족되면 앞 장소는 원래 시각 유지")
    void movesOnlyPlacesNeededForLunch() {

        CreatePlanAiResponse result = finish(dayOf(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침 식당",
                        "08:00:00",
                        50,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 A",
                        "10:00:00",
                        60,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.CAFE_REST,
                        "카페",
                        "12:00:00",
                        30,
                        10
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심 식당",
                        "13:00:00",
                        50,
                        30
                )
        )));

        assertEquals(
                LocalTime.of(10, 0),
                startOf(
                        result,
                        "오전 관광지 A"
                )
        );
        assertEquals(
                LocalTime.of(11, 30),
                startOf(
                        result,
                        "카페"
                )
        );
        assertEquals(
                LocalTime.of(12, 30),
                lunch(result)
                        .startTime()
        );
        assertTravelGapsKept(result);
    }

    @Test
    @DisplayName("여유와 직전 식사 앞당김으로 1분이 모자라면 충족 위장 없이 이동 우선 배치")
    void keepsOneMinuteLateLunchWhenNoRoomRemains() {

        CreatePlanAiResponse result = finish(dayOf(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침 식당",
                        "07:30:00",
                        50,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 A",
                        "08:30:00",
                        60,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 B",
                        "09:40:00",
                        120,
                        10
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심 식당",
                        "12:31:00",
                        50,
                        51
                )
        )));

        assertEquals(
                LocalTime.of(7, 30),
                meal(
                        result,
                        ScheduleType.BREAKFAST
                )
                        .startTime()
        );
        assertEquals(
                LocalTime.of(12, 31),
                lunch(result)
                        .startTime()
        );
        assertTravelGapsKept(result);
    }

    @Test
    @DisplayName("동행인 교집합으로 좁아진 점심 창은 직전 식사 창 밖 앞당김 없이 미충족 유지")
    void keepsLunchLateWhenCompanionWindowIsNarrower() {

        List<TravelHealthContext> companions = new ArrayList<>(contexts());

        companions.add(new TravelHealthContext(
                "동행인 2",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 0),
                        LocalTime.of(11, 50),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of()
        ));

        CreatePlanAiResponse result = finish(
                c6LiveDay(),
                companions
        );

        assertEquals(
                LocalTime.of(8, 0),
                meal(
                        result,
                        ScheduleType.BREAKFAST
                )
                        .startTime()
        );
        assertEquals(
                LocalTime.of(13, 31),
                lunch(result)
                        .startTime()
        );
        assertTravelGapsKept(result);
    }

    @Test
    @DisplayName("이동시간 누락으로 겹친 오전 장소가 있어도 직전 식사와 겹치게 당기지 않음")
    void doesNotPullPlaceIntoBreakfastWhenTravelIsMissing() {

        CreatePlanAiResponse result = finish(dayOf(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.RESTAURANT,
                        "아침 식당",
                        "07:55:00",
                        50,
                        10
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 A",
                        "09:00:00",
                        60,
                        0
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 B",
                        "09:30:00",
                        30,
                        null
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        "오전 관광지 C",
                        "12:00:00",
                        60,
                        10
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "점심 식당",
                        "13:00:00",
                        50,
                        0
                )
        )));

        // 장소 압축은 겹침 때문에 거부, 직전 식사 15분 앞당김으로 회복
        assertEquals(
                LocalTime.of(7, 40),
                meal(
                        result,
                        ScheduleType.BREAKFAST
                )
                        .startTime()
        );
        assertEquals(
                LocalTime.of(8, 30),
                startOf(
                        result,
                        "오전 관광지 A"
                )
        );
        assertEquals(
                LocalTime.of(12, 30),
                lunch(result)
                        .startTime()
        );
        assertFalse(startOf(
                result,
                "오전 관광지 A"
        )
                .isBefore(meal(
                        result,
                        ScheduleType.BREAKFAST
                )
                        .endTime()));
    }

    // 실측 C6 1회차 1일차 정규화 입력의 장소·체류·이동시간
    private CreatePlanAiResponse c6LiveDay() {

        return dayOf(List.of(
                slot(
                        ScheduleType.BREAKFAST,
                        CourseType.LOCAL_FOOD,
                        "동우설렁탕 본점",
                        "08:00:00",
                        50,
                        32
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.MUST_HAVE,
                        "경복궁",
                        "09:30:00",
                        110,
                        37
                ),
                slot(
                        ScheduleType.ACTIVITY,
                        CourseType.CAFE_REST,
                        "결",
                        "12:00:00",
                        40,
                        12
                ),
                slot(
                        ScheduleType.LUNCH,
                        CourseType.RESTAURANT,
                        "광양불고기",
                        "12:30:00",
                        60,
                        51
                )
        ));
    }

    // 오전 일정 뒤 고정 오후 관광지와 저녁
    private CreatePlanAiResponse dayOf(List<CreatePlanAiResponse.PlanScheduleDetail> morning) {

        List<CreatePlanAiResponse.PlanScheduleDetail> slots = new ArrayList<>(morning);

        slots.add(slot(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                "오후 관광지",
                "15:00:00",
                60,
                30
        ));
        slots.add(slot(
                ScheduleType.DINNER,
                CourseType.RESTAURANT,
                "저녁 식당",
                "18:00:00",
                60,
                20
        ));

        return new CreatePlanAiResponse(List.of(new CreatePlanAiResponse.PlanDayDetail(
                1,
                LocalDate.of(
                        2026,
                        10,
                        19
                ),
                slots
        )));
    }

    // 장소 슬롯의 체류시간 유지와 앞 장소 종료·이동시간 이후 시작
    private void assertTravelGapsKept(CreatePlanAiResponse response) {

        LocalTime previousEnd = null;

        for (CreatePlanAiResponse.PlanScheduleDetail slot : response
                .planDays()
                .getFirst()
                .schedules()) {
            if (slot.courseType() == CourseType.MEDICATION || slot.courseType() == CourseType.TRANSPORTATION) {
                continue;
            }

            assertEquals(
                    slot.stayMinutes().longValue(),
                    Duration
                            .between(
                                    slot.startTime(),
                                    slot.endTime()
                            )
                            .toMinutes(),
                    slot.locationName()
            );

            if (previousEnd != null && slot.travelMinutes() != null) {
                assertFalse(
                        slot
                                .startTime()
                                .isBefore(previousEnd.plusMinutes(slot.travelMinutes())),
                        slot.locationName()
                );
            }

            previousEnd = slot.endTime();
        }
    }

    private LocalTime startOf(
            CreatePlanAiResponse response,
            String name
    ) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> name.equals(slot.locationName()))
                .findFirst()
                .orElseThrow()
                .startTime();
    }

    private CreatePlanAiResponse.PlanScheduleDetail meal(
            CreatePlanAiResponse response,
            ScheduleType type
    ) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.scheduleType() == type)
                .findFirst()
                .orElseThrow();
    }

    private List<String> names(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.courseType() != CourseType.MEDICATION)
                .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                .toList();
    }

    private void assertMedicationContract(CreatePlanAiResponse response) {

        List<CreatePlanAiResponse.PlanScheduleDetail> slots = response
                .planDays()
                .getFirst()
                .schedules();
        List<CreatePlanAiResponse.PlanScheduleDetail> meds = slots
                .stream()
                .filter(slot -> slot.courseType() == CourseType.MEDICATION)
                .toList();
        assertEquals(6, meds.size());

        for (ScheduleType mealType : List.of(ScheduleType.BREAKFAST, ScheduleType.LUNCH, ScheduleType.DINNER)) {
            CreatePlanAiResponse.PlanScheduleDetail meal = slots
                    .stream()
                    .filter(slot -> slot.scheduleType() == mealType)
                    .findFirst()
                    .orElseThrow();
            assertTrue(meds
                    .stream()
                    .anyMatch(med -> med
                            .startTime()
                            .equals(meal
                                    .startTime()
                                    .minusMinutes(30))));
            assertTrue(meds
                    .stream()
                    .anyMatch(med -> med
                            .startTime()
                            .equals(meal
                                    .endTime()
                                    .plusMinutes(30))));
        }

        for (CreatePlanAiResponse.PlanScheduleDetail med : meds) {
            assertEquals(
                    med
                            .startTime()
                            .plusMinutes(10),
                    med.endTime()
            );

        }
    }

    private CreatePlanAiResponse finish(CreatePlanAiResponse input) {

        return finish(
                input,
                contexts()
        );
    }

    private CreatePlanAiResponse finish(
            CreatePlanAiResponse input,
            List<TravelHealthContext> healthContexts
    ) {

        CreatePlanAiResponse normalized = normalizer
                .normalizeScheduleTimes(
                        input,
                        healthContexts
                );
        return normalizer
                .finalizeMedicationSchedules(
                        normalized,
                        healthContexts
                );
    }

    private CreatePlanAiResponse.PlanScheduleDetail lunch(CreatePlanAiResponse response) {

        return response
                .planDays()
                .getFirst()
                .schedules()
                .stream()
                .filter(slot -> slot.scheduleType() == ScheduleType.LUNCH)
                .findFirst()
                .orElseThrow();
    }

    private List<TravelHealthContext> contexts() {

        Set<TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext> rules =
                java.util.Arrays
                        .stream(RelatedMeal.values())
                        .flatMap(meal -> List.of(MealTiming.BEFORE_MEAL, MealTiming.AFTER_MEAL)
                                .stream()
                                .map(timing -> new TravelHealthContext.MedicationInfoContext.MealMedicationRuleContext(
                                        meal,
                                        timing,
                                        30
                                )))
                        .collect(java.util.stream.Collectors.toSet());
        return List.of(new TravelHealthContext(
                "합성 여행자",
                List.of(),
                null,
                new TravelHealthContext.MealInfoContext(
                        LocalTime.of(8, 0),
                        LocalTime.of(12, 0),
                        LocalTime.of(18, 0)
                ),
                List.of(),
                List.of(new TravelHealthContext.MedicationInfoContext(
                        "테스트 복약",
                        MedicationBasis.WITH_MEAL,
                        null,
                        rules
                ))
        ));
    }

    private CreatePlanAiResponse.PlanScheduleDetail slot(
            ScheduleType type,
            CourseType course,
            String name,
            String time,
            int stay,
            Integer travel
    ) {

        LocalTime start = LocalTime.parse(time);
        return new CreatePlanAiResponse.PlanScheduleDetail(
                type,
                course,
                start,
                start.plusMinutes(stay),
                name,
                "서울",
                "127",
                "37",
                null,
                null,
                stay,
                travel,
                Set.of(),
                null,
                null
        );
    }

    private CreatePlanAiResponse fixture(int day) {

        if (day == 1) {
            return new CreatePlanAiResponse(List.of(
                    new CreatePlanAiResponse.PlanDayDetail(
                            day,
                            LocalDate.of(2026, 10, 19),
                            List.of(
                                slot(
                                        ScheduleType.BREAKFAST,
                                        CourseType.LOCAL_FOOD,
                                        "구수옥설렁탕",
                                        "08:00:00",
                                        50,
                                        50
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.MUST_HAVE,
                                        "경복궁",
                                        "09:48:00",
                                        100,
                                        58
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.CAFE_REST,
                                        "테라로사 광화문점",
                                        "11:34:00",
                                        30,
                                        6
                                ),
                                slot(
                                        ScheduleType.LUNCH,
                                        CourseType.RESTAURANT,
                                        "연희동칼국수 본점",
                                        "12:42:00",
                                        50,
                                        38
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "덕수궁 돌담길",
                                        "14:12:00",
                                        60,
                                        33
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "창덕궁",
                                        "15:41:00",
                                        100,
                                        29
                                ),
                                slot(
                                        ScheduleType.DINNER,
                                        CourseType.RESTAURANT,
                                        "고려삼계탕",
                                        "18:00:00",
                                        50,
                                        30
                                )
                            )
                    )
            ));
        }
        if (day == 2) {
            return new CreatePlanAiResponse(List.of(
                    new CreatePlanAiResponse.PlanDayDetail(
                            day,
                            LocalDate.of(2026, 10, 20),
                            List.of(
                                slot(
                                        ScheduleType.BREAKFAST,
                                        CourseType.RESTAURANT,
                                        "가산물갈비&백년불고기",
                                        "08:00:00",
                                        50,
                                        48
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "경희궁공원",
                                        "09:44:00",
                                        80,
                                        54
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.CAFE_REST,
                                        "통인동커피공방 위켄드",
                                        "11:28:00",
                                        30,
                                        24
                                ),
                                slot(
                                        ScheduleType.LUNCH,
                                        CourseType.RESTAURANT,
                                        "청아라 생선구이",
                                        "12:41:00",
                                        50,
                                        43
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "롯데월드 아쿠아리움",
                                        "14:40:00",
                                        60,
                                        69
                                ),
                                slot(
                                        ScheduleType.ACTIVITY,
                                        CourseType.ATTRACTION,
                                        "덕수궁 대한문",
                                        "16:25:00",
                                        60,
                                        45
                                ),
                                slot(
                                        ScheduleType.DINNER,
                                        CourseType.RESTAURANT,
                                        "광양불고기",
                                        "18:10:00",
                                        50,
                                        45
                                )
                            )
                    )
            ));
        }
        throw new IllegalArgumentException("날짜 범위");
    }
}
