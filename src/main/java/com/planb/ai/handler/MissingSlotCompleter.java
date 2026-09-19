package com.planb.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.policy.AttractionTagPolicy;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.domain.travel.policy.TouristPlaceCountPolicy;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * AI가 채우지 못한 관광 슬롯과 식사 슬롯을 이번 호출의 검색 후보로 채운다.
 *
 * 두 가지 모두 후보 목록만 있으면 Java가 결정할 수 있는 값이다.
 * AI 재시도에 맡기면 한 번의 교정으로 두 조건을 동시에 맞춰야 하고,
 * 한쪽을 고치다 다른 쪽을 버리면 재시도가 소진되어 일정 생성 자체가 실패한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MissingSlotCompleter {

    private static final int FILLED_STAY_MINUTES = 60;

    // 채워 넣은 관광지 앞뒤로 남기는 최소 간격. 실제 값은 이후 정규화가 다시 잡는다.
    private static final int FILLED_GAP_MINUTES = 20;

    private static final LocalTime DEFAULT_DAY_START = LocalTime.of(9, 0);

    private final TourismTool tourismTool;

    /**
     * 비어 있는 관광·식사 슬롯을 이번 호출의 검색 후보로 채운다.
     *
     * @param response       채울 일정
     * @param healthContexts 관광지 개수와 식사시각의 기준
     * @param candidates     이번 호출에서 검색한 후보
     * @param usedNames      이 응답 밖에서 이미 쓴 장소명. 날짜 일부만 넘길 때 나머지 날짜를 알려준다.
     *                       변형하지 않으므로 불변 집합을 넘겨도 된다.
     * @param usedMenus      이 응답 밖에서 이미 쓴 메뉴명. 장소명과 같은 이유로 받는다.
     *                       변형하지 않으므로 불변 집합을 넘겨도 된다.
     * @return 채워 넣은 일정
     */
    public CreatePlanAiResponse complete(
            CreatePlanAiResponse response,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        if (response == null || response.planDays() == null) {
            return response;
        }

        // 여행 전체에서 이미 쓴 장소는 다시 고르지 않는다.
        // 호출부가 알려준 응답 밖의 장소와 응답 안의 장소를 합쳐서 본다.
        Set<String> selectedNames = merged(
                usedNames(response),
                usedNames
        );

        // 메뉴도 같은 이유로 본다. 식당 이름이 달라도 대표메뉴가 겹치면
        // 여행자는 같은 음식을 두 끼 먹게 된다.
        Set<String> selectedMenus = merged(
                usedMenus(response),
                usedMenus
        );

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            days.add(
                    fillDay(
                            day,
                            healthContexts,
                            candidates,
                            selectedNames,
                            selectedMenus
                    )
            );
        }

        return new CreatePlanAiResponse(days);
    }

    private Set<String> merged(
            Set<String> fromResponse,
            Set<String> fromCaller
    ) {

        if (fromCaller != null) {
            fromCaller
                    .stream()
                    .map(MissingSlotCompleter::normalized)
                    .filter(Objects::nonNull)
                    .forEach(fromResponse::add);
        }

        return fromResponse;
    }

    private CreatePlanAiResponse.PlanDayDetail fillDay(
            CreatePlanAiResponse.PlanDayDetail day,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        if (day == null || day.schedules() == null) {
            return day;
        }

        List<CreatePlanAiResponse.PlanScheduleDetail> schedules =
                new ArrayList<>(day.schedules());

        addTouristPlaces(schedules, healthContexts, candidates, usedNames);

        addMealSlots(day, schedules, healthContexts, candidates, usedNames, usedMenus);

        schedules.sort(
                Comparator.comparing(
                        CreatePlanAiResponse.PlanScheduleDetail::startTime,
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
        );

        return new CreatePlanAiResponse.PlanDayDetail(
                day.dayNumber(),
                day.date(),
                schedules
        );
    }

    private void addTouristPlaces(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames
    ) {

        int expectedCount = TouristPlaceCountPolicy.expectedCount(healthContexts);

        long actualCount = schedules
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.courseType() == CourseType.ATTRACTION
                        || schedule.courseType() == CourseType.MUST_HAVE)
                .count();

        for (long missing = expectedCount - actualCount; missing > 0; missing--) {
            PlaceCandidateContext.Candidate candidate = nearestUnused(
                    candidates.attractionCandidates(),
                    lastPlace(schedules),
                    usedNames
            );

            if (candidate == null) {
                return;
            }

            LocalTime startTime = nextStartTime(schedules);

            schedules.add(
                    placeSlot(
                            ScheduleType.ACTIVITY,
                            CourseType.ATTRACTION,
                            candidate,
                            startTime,
                            null,
                            // AI가 만들지 않은 슬롯이라 태그도 Java가 정한다.
                            AttractionTagPolicy.tagsOf(candidate.categoryCode())
                    )
            );

            usedNames.add(normalized(candidate.name()));

            log.info(
                    "[SLOT FILL] 관광 슬롯 보정 - locationName: {}, startTime: {}",
                    candidate.name(),
                    startTime
            );
        }
    }

    private void addMealSlots(
            CreatePlanAiResponse.PlanDayDetail day,
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules,
            List<TravelHealthContext> healthContexts,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        List<ScheduleType> missingMeals = MealSlotPolicy.missingMeals(
                new CreatePlanAiResponse.PlanDayDetail(
                        day.dayNumber(),
                        day.date(),
                        schedules
                ),
                healthContexts
        );

        for (ScheduleType mealType : missingMeals) {
            LocalTime mealTime = MealSlotPolicy.configuredMealTime(mealType, healthContexts);

            if (mealTime == null) {
                continue;
            }

            CreatePlanAiResponse.PlanScheduleDetail mealSlot = mealSlot(
                    mealType,
                    mealTime,
                    candidates,
                    usedNames,
                    usedMenus
            );

            if (mealSlot == null) {

                // 여기서 포기하면 바로 뒤 검증이 사용자에게 실패를 던진다.
                // 이유를 남기지 않으면 왜 못 채웠는지 로그로 되짚을 수 없다.
                log.info(
                        "[SLOT FILL] 식사 슬롯 보정 실패 - scheduleType: {}, 대표메뉴를 확인한 음식점 후보 수: {}",
                        mealType,
                        candidates.restaurantCandidates().size()
                );

                return;
            }

            schedules.add(mealSlot);

            usedNames.add(normalized(mealSlot.locationName()));

            usedMenus.add(
                    normalized(
                            mealSlot
                                    .restaurantDetail()
                                    .menuName()
                    )
            );

            log.info(
                    "[SLOT FILL] 식사 슬롯 보정 - scheduleType: {}, locationName: {}, startTime: {}",
                    mealType,
                    mealSlot.locationName(),
                    mealTime
            );
        }
    }

    /**
     * 식사 슬롯 하나를 만든다.
     *
     * 메뉴명은 검색 키워드가 아니라 TourAPI 상세의 대표메뉴를 쓴다.
     * 상세를 얻지 못한 후보는 메뉴를 확정할 수 없으므로 건너뛰고 다음 후보를 본다.
     *
     * 대표메뉴가 이미 쓰인 후보도 건너뛴다. 식당 이름이 달라도 같은 음식이면
     * 여행자에게는 같은 끼니가 두 번 나온 것이다.
     */
    private CreatePlanAiResponse.PlanScheduleDetail mealSlot(
            ScheduleType mealType,
            LocalTime mealTime,
            PlaceCandidateContext candidates,
            Set<String> usedNames,
            Set<String> usedMenus
    ) {

        for (PlaceCandidateContext.Candidate candidate : candidates.restaurantCandidates()) {
            if (usedNames.contains(normalized(candidate.name()))) {
                continue;
            }

            String menuName = representativeMenu(candidate);

            if (menuName == null) {
                continue;
            }

            if (usedMenus.contains(normalized(menuName))) {
                continue;
            }

            // 식사 태그는 메뉴와 영양 정보로 결정되므로 이후 단계가 계산한다.
            return placeSlot(
                    mealType,
                    CourseType.RESTAURANT,
                    candidate,
                    mealTime,
                    new CreatePlanAiResponse.RestaurantDetail(
                            menuName,
                            null,
                            null,
                            null,
                            null,
                            candidate.address(),
                            candidate.longitude(),
                            candidate.latitude(),
                            candidate.imageUrl()
                    ),
                    Set.of()
            );
        }

        return null;
    }

    private String representativeMenu(PlaceCandidateContext.Candidate candidate) {

        Kor2RestaurantIntroResponse intro;

        try {
            intro = tourismTool.getRestaurantDetail(
                    PlaceCandidateContext.contentId(candidate.candidateId())
            );
        } catch (RuntimeException failure) {
            log.info(
                    "[SLOT FILL] 음식점 상세 조회 실패로 후보 제외 - locationName: {}, 원인: {}",
                    candidate.name(),
                    failure.toString()
            );

            return null;
        }

        if (intro == null
                || intro.response() == null
                || intro.response().body() == null
                || intro.response().body().items() == null
                || intro.response().body().items().item() == null) {
            return null;
        }

        return intro
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(Objects::nonNull)
                .map(Kor2RestaurantIntroResponse.Item::firstmenu)
                .filter(menu -> menu != null && !menu.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * 직전 확정 장소에서 직선거리가 가장 가까운 미사용 후보.
     *
     * 목록 순서대로 고르면 하루 동선이 지역 전체로 튄다.
     * 좌표를 모르면 비교할 근거가 없으므로 목록 순서를 따른다.
     */
    private PlaceCandidateContext.Candidate nearestUnused(
            List<PlaceCandidateContext.Candidate> pool,
            CreatePlanAiResponse.PlanScheduleDetail lastPlace,
            Set<String> usedNames
    ) {

        List<PlaceCandidateContext.Candidate> unused = pool
                .stream()
                .filter(candidate -> candidate.name() != null)
                .filter(candidate -> !usedNames.contains(normalized(candidate.name())))
                .toList();

        if (unused.isEmpty()) {
            return null;
        }

        Double originX = number(lastPlace == null ? null : lastPlace.longitude());
        Double originY = number(lastPlace == null ? null : lastPlace.latitude());

        if (originX == null || originY == null) {
            return unused.getFirst();
        }

        return unused
                .stream()
                .min(Comparator.comparingDouble(candidate -> squaredDistance(candidate, originX, originY)))
                .orElse(null);
    }

    // 가까운 순서만 필요하므로 제곱거리로 비교한다. 실제 거리로 바꿔도 순서는 같다.
    private double squaredDistance(
            PlaceCandidateContext.Candidate candidate,
            double originX,
            double originY
    ) {

        Double x = number(candidate.longitude());
        Double y = number(candidate.latitude());

        if (x == null || y == null) {
            return Double.MAX_VALUE;
        }

        return Math.pow(x - originX, 2) + Math.pow(y - originY, 2);
    }

    private CreatePlanAiResponse.PlanScheduleDetail placeSlot(
            ScheduleType scheduleType,
            CourseType courseType,
            PlaceCandidateContext.Candidate candidate,
            LocalTime startTime,
            CreatePlanAiResponse.RestaurantDetail restaurantDetail,
            Set<RecommendationTag> tags
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                scheduleType,
                courseType,
                startTime,
                startTime.plusMinutes(FILLED_STAY_MINUTES),
                candidate.name(),
                candidate.address(),
                candidate.longitude(),
                candidate.latitude(),
                candidate.imageUrl(),
                candidate.thumbnailUrl(),
                FILLED_STAY_MINUTES,
                // 이동시간은 확정 좌표로 뒤에서 조회한다.
                null,
                tags,
                null,
                restaurantDetail,
                candidate.candidateId()
        );
    }

    /**
     * 채워 넣을 슬롯의 시작시각.
     *
     * 하루의 빈틈 중 가장 이른 곳에 넣는다. 마지막 일정 뒤에만 붙이면
     * 저녁 식사 다음으로 밀려 심야 관광이 되어버린다.
     * 낮에 들어갈 틈이 없을 때만 마지막 일정 뒤에 붙인다.
     */
    private LocalTime nextStartTime(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules
    ) {

        List<CreatePlanAiResponse.PlanScheduleDetail> ordered = schedules
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.startTime() != null)
                .sorted(Comparator.comparing(CreatePlanAiResponse.PlanScheduleDetail::startTime))
                .toList();

        if (ordered.isEmpty()) {
            return DEFAULT_DAY_START;
        }

        int neededMinutes = FILLED_STAY_MINUTES + FILLED_GAP_MINUTES * 2;

        for (int index = 0; index < ordered.size() - 1; index++) {
            LocalTime previousEnd = endOf(ordered.get(index));

            LocalTime nextStart = ordered.get(index + 1).startTime();

            if (Duration.between(previousEnd, nextStart).toMinutes() >= neededMinutes) {
                return previousEnd.plusMinutes(FILLED_GAP_MINUTES);
            }
        }

        return endOf(ordered.getLast()).plusMinutes(FILLED_GAP_MINUTES);
    }

    private LocalTime endOf(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule.endTime() == null
                ? schedule.startTime()
                : schedule.endTime();
    }

    private CreatePlanAiResponse.PlanScheduleDetail lastPlace(
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules
    ) {

        return schedules
                .stream()
                .filter(Objects::nonNull)
                .filter(schedule -> schedule.longitude() != null
                        && schedule.latitude() != null)
                .filter(schedule -> schedule.startTime() != null)
                .max(Comparator.comparing(CreatePlanAiResponse.PlanScheduleDetail::startTime))
                .orElse(null);
    }

    // 장소명 비교 기준. 호출부(PlanPlaceResolver)가 strip한 이름을 넣으므로 여기서도 맞춘다.
    private static String normalized(String name) {

        if (name == null) {
            return null;
        }

        String stripped = name.strip();

        return stripped.isEmpty() ? null : stripped;
    }

    private Set<String> usedMenus(CreatePlanAiResponse response) {

        Set<String> menus = new HashSet<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null) {
                continue;
            }

            day.schedules()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(CreatePlanAiResponse.PlanScheduleDetail::restaurantDetail)
                    .filter(Objects::nonNull)
                    .map(CreatePlanAiResponse.RestaurantDetail::menuName)
                    .map(MissingSlotCompleter::normalized)
                    .filter(Objects::nonNull)
                    .forEach(menus::add);
        }

        return menus;
    }

    private Set<String> usedNames(CreatePlanAiResponse response) {

        Set<String> names = new HashSet<>();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            if (day == null || day.schedules() == null) {
                continue;
            }

            day.schedules()
                    .stream()
                    .filter(Objects::nonNull)
                    .map(CreatePlanAiResponse.PlanScheduleDetail::locationName)
                    .map(MissingSlotCompleter::normalized)
                    .filter(Objects::nonNull)
                    .forEach(names::add);
        }

        return names;
    }

    private Double number(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException notANumber) {
            return null;
        }
    }
}
