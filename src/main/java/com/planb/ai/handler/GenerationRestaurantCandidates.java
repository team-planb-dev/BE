package com.planb.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.policy.MealSlotPolicy;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;

import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 생성 요청의 음식점 상세 준비
 */
public class GenerationRestaurantCandidates {

    private static final Pattern SIMPLE_HOURS = Pattern.compile(
            "^([0-2]?[0-9]:[0-5][0-9])\\s*[~-]\\s*([0-2]?[0-9]:[0-5][0-9])$"
    );

    private final List<TravelHealthContext> healthContexts;
    private final List<String> excludedFoods;

    private final String locationDo;
    private final String locationSigungu;
    private final Set<String> attemptedSearches = new LinkedHashSet<>();
    private int detailAttempts;
    private boolean regionalAttempted;

    private final TourismTool tourismTool;
    private final PlaceCandidateContext candidates;
    private final Map<List<String>, List<RestaurantCandidate>> searches = new LinkedHashMap<>();
    private final Map<String, RestaurantCandidate> prepared = new LinkedHashMap<>();

    public GenerationRestaurantCandidates(
            TourismTool tourismTool,
            PlaceCandidateContext candidates
    ) {

        this(tourismTool, candidates, List.of());
    }

    public GenerationRestaurantCandidates(
            TourismTool tourismTool,
            PlaceCandidateContext candidates,
            List<TravelHealthContext> healthContexts
    ) {

        this(
                tourismTool,
                candidates,
                healthContexts,
                null,
                null
        );
    }

    public GenerationRestaurantCandidates(
            TourismTool tourismTool,
            PlaceCandidateContext candidates,
            List<TravelHealthContext> healthContexts,
            String locationDo,
            String locationSigungu
    ) {

        this.locationDo = locationDo;
        this.locationSigungu = locationSigungu;
        this.tourismTool = tourismTool;
        this.candidates = candidates;
        this.healthContexts = List.copyOf(healthContexts);
        this.excludedFoods = healthContexts
                .stream()
                .filter(Objects::nonNull)
                .filter(health -> health.foodInfos() != null)
                .flatMap(health -> health
                        .foodInfos()
                        .stream())
                .filter(Objects::nonNull)
                .filter(food -> food.foodType() == FoodType.ALLERGY || food.foodType() == FoodType.AVOID)
                .map(TravelHealthContext.FoodInfoContext::foodName)
                .filter(name -> name != null && !name.isBlank())
                .map(GenerationRestaurantCandidates::normalized)
                .flatMap(food -> "새우".equals(food)
                        ? List
                                .of("새우", "대하", "쉬림프", "감바스", "해물", "짬뽕", "해천")
                                .stream()
                        : List
                                .of(food)
                                .stream())
                .distinct()
                .toList();
    }

    public void clear() {

        searches.clear();
        prepared.clear();
        attemptedSearches.clear();
        detailAttempts = 0;
        regionalAttempted = false;
    }

    public List<RestaurantCandidate> search(
            String keyword,
            String locationDo,
            String locationSigungu
    ) {

        if (keyword == null || keyword.isBlank() || locationDo == null || locationSigungu == null) {
            return List.of();
        }

        if (this.locationDo != null && (!this.locationDo.equals(locationDo)
                || !this.locationSigungu.equals(locationSigungu))) {
            return List.of();
        }

        List<String> key = List.of(normalized(keyword), locationDo, locationSigungu);
        if (searches.containsKey(key)) {
            return searches
                    .get(key)
                    .stream()
                    .map(this::withMealEligibility)
                    .toList();
        }
        if (attemptedSearches.size() >= 8 || !attemptedSearches.add(key.getFirst())) {
            return List.of();
        }

        List<RestaurantCandidate> result = prepare(
                tourismTool.searchRestaurantsByLocation(
                        keyword.strip(),
                        locationDo,
                        locationSigungu
                ),
                4
        );
        searches.put(key, result);
        return result;
    }

    public void collectRegional() {

        if (regionalAttempted || locationDo == null) {
            return;
        }
        regionalAttempted = true;
        prepare(tourismTool.searchRestaurantCandidatesByRegion(locationDo, locationSigungu), 8);
    }

    public boolean accepts(
            String candidateId,
            String menuName,
            ScheduleType mealType
    ) {

        RestaurantCandidate candidate = prepared.get(candidateId);
        return candidate != null
                && candidate
                        .menus()
                        .contains(menuName)
                && withMealEligibility(candidate)
                        .eligibleMeals()
                        .contains(mealType);
    }

    public List<String> violations(CreatePlanAiResponse response) {

        if (response == null || response.planDays() == null) {
            return List.of();
        }
        return response
                .planDays()
                .stream()
                .filter(Objects::nonNull)
                .filter(day -> day.schedules() != null)
                .flatMap(day -> day
                        .schedules()
                        .stream())
                .filter(Objects::nonNull)
                .filter(slot -> slot.courseType() == CourseType.RESTAURANT
                        || slot.courseType() == CourseType.LOCAL_FOOD)
                .filter(slot -> slot.restaurantDetail() == null || !accepts(
                        slot.candidateId(),
                        slot.restaurantDetail().menuName(),
                        slot.scheduleType()
                ))
                .map(slot -> "restaurantDetail: 준비된 메뉴와 식사 유형만 선택 - " + slot.candidateId())
                .toList();
    }

    public String menu(
            String candidateId,
            ScheduleType mealType,
            Set<String> usedMenus
    ) {

        RestaurantCandidate candidate = prepared.get(candidateId);
        if (candidate == null || !withMealEligibility(candidate)
                        .eligibleMeals()
                        .contains(mealType)) {
            return null;
        }
        return candidate
                .menus()
                .stream()
                .filter(menu -> !usedMenus.contains(normalized(menu)))
                .findFirst()
                .orElse(null);
    }

    public String hoursOutcome(
            String candidateId,
            ScheduleType mealType,
            LocalTime time
    ) {

        RestaurantCandidate candidate = prepared.get(candidateId);
        Boolean open = openAt(candidate == null ? null : candidate.openingHours(), time);
        if (open == null) {
            return "unknown";
        }
        if (withMealEligibility(candidate)
                .relaxedMeals()
                .contains(mealType)) {
            return "relaxed";
        }
        return open ? "compatible" : "definite_mismatch";
    }

    public String openingHours(String candidateId) {

        RestaurantCandidate candidate = prepared.get(candidateId);
        return candidate == null ? null : candidate.openingHours();
    }

    public void prepare(PlaceCandidateContext.Candidate candidate) {

        if (candidate == null || !"39".equals(candidate.type())) {
            return;
        }
        candidates.record(candidate);
        prepare(candidate.candidateId(), candidate);
    }

    private List<RestaurantCandidate> prepare(
            Kor2KeywordSearchResponse response,
            int limit
    ) {

        if (response == null || response.response() == null || response.response().body() == null
                || response.response().body().items() == null
                || response.response().body().items().item() == null) {
            return List.of();
        }

        Set<String> ids = new LinkedHashSet<>();
        return response
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> "39".equals(item.contenttypeid()))
                .filter(item -> item.contentid() != null && !item.contentid().isBlank())
                .filter(item -> ids.add(item.contentid()))
                .limit(limit)
                .map(item -> prepare("tour:" + item.contentid(), candidates.record(item)))
                .filter(Objects::nonNull)
                .toList()
                .stream()
                .map(this::withMealEligibility)
                .toList();
    }

    private RestaurantCandidate prepare(
            String id,
            PlaceCandidateContext.Candidate place
    ) {

        if (prepared.containsKey(id)) {
            return prepared.get(id);
        }
        if (detailAttempts >= 40) {
            return null;
        }
        detailAttempts++;
        // 조회 실패도 요청 상한에 포함
        prepared.put(id, null);
        String contentId = PlaceCandidateContext.contentId(id);
        Kor2RestaurantIntroResponse detail = tourismTool.getRestaurantDetail(contentId);
        if (detail == null || detail.response() == null || detail.response().body() == null
                || detail.response().body().items() == null
                || detail.response().body().items().item() == null) {
            prepared.put(id, null);
            return null;
        }

        Kor2RestaurantIntroResponse.Item intro = detail
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(Objects::nonNull)
                .filter(row -> contentId.equals(row.contentid()))
                .filter(row -> "39".equals(row.contenttypeid()))
                .findFirst()
                .orElse(null);

        String offered = intro == null ? null : intro.firstmenu();
        if (offered == null || offered.isBlank()) {
            offered = intro == null ? null : intro.treatmenu();
        }
        if (offered == null || offered.isBlank()) {
            prepared.put(id, null);
            return null;
        }

        List<String> menus = Arrays
                .stream(offered.split("[,/;\\n]|(?i)<br\\s*/?>"))
                .map(String::strip)
                .filter(menu -> excludedFoods
                        .stream()
                        .noneMatch(food -> normalized(menu).contains(food)))
                .filter(menu -> !menu.isBlank())
                .distinct()
                .toList();

        RestaurantCandidate result = menus.isEmpty()
                ? null
                : new RestaurantCandidate(
                        place,
                        menus,
                        intro.opentimefood(),
                        List.of(),
                        List.of()
                );
        prepared.put(id, result);
        return result;
    }

    private RestaurantCandidate withMealEligibility(RestaurantCandidate candidate) {

        List<ScheduleType> relaxed = MealSlotPolicy.MEAL_SCHEDULE_TYPES
                .stream()
                .filter(type -> prepared
                        .values()
                        .stream()
                        .filter(Objects::nonNull)
                        .noneMatch(other -> !Boolean.FALSE.equals(openAt(
                                other.openingHours(),
                                MealSlotPolicy.configuredMealTime(type, healthContexts)
                        ))))
                .toList();
        List<ScheduleType> allowed = MealSlotPolicy.MEAL_SCHEDULE_TYPES
                .stream()
                .filter(type -> relaxed.contains(type)
                        || !Boolean.FALSE.equals(openAt(candidate.openingHours(), MealSlotPolicy.configuredMealTime(type, healthContexts))))
                .toList();
        return new RestaurantCandidate(
                candidate.place(),
                candidate.menus(),
                candidate.openingHours(),
                allowed,
                relaxed
        );
    }

    private static Boolean openAt(
            String hours,
            LocalTime time
    ) {

        if (hours == null || time == null) {
            return null;
        }
        var match = SIMPLE_HOURS.matcher(hours.strip());
        if (!match.matches()) {
            return null;
        }
        try {
            LocalTime start = LocalTime.parse(match.group(1).length() == 4 ? "0" + match.group(1) : match.group(1));
            String closing = match.group(2);
            LocalTime end = "24:00".equals(closing)
                    ? LocalTime.MIDNIGHT
                    : LocalTime.parse(closing.length() == 4 ? "0" + closing : closing);
            if (start.equals(end)) {
                return null;
            }
            return start.isBefore(end)
                    ? !time.isBefore(start) && time.isBefore(end)
                    : !time.isBefore(start) || time.isBefore(end);
        } catch (java.time.DateTimeException invalid) {
            return null;
        }
    }

    private static String normalized(String text) {

        return text
                .replaceAll("\\s+", "")
                .toLowerCase(java.util.Locale.ROOT);
    }

    public record RestaurantCandidate(
            PlaceCandidateContext.Candidate place,
            List<String> menus,
            String openingHours,
            List<ScheduleType> eligibleMeals,
            List<ScheduleType> relaxedMeals
    ) {
    }
}
