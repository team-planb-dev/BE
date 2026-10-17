package com.planb.ai.context;

import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class PlaceCandidateContext {

    // TourAPI 후보의 candidateId 접두사와 외부 API용 contentId 구분
    private static final String TOUR_PREFIX = "tour:";

    // 관광지·음식점 구분용 TourAPI contentTypeId
    private static final String ATTRACTION_CONTENT_TYPE_ID = "12";

    private static final String RESTAURANT_CONTENT_TYPE_ID = "39";

    private static final int MAX_STORED_OPENING_HOURS = 255;

    private static final Pattern MENU_BOUNDARIES = Pattern.compile("[()]|[,/;|\\r\\n]|(?i:<br\\s*/?>)");

    private static final JsonMapper MENU_MAPPER = JsonMapper
            .builder()
            .build();

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();

    private final Map<String, String> standardFoodNames = new ConcurrentHashMap<>();

    // 사용자 지정 장소로 고정한 관광지 후보 ID
    private final Set<String> pinnedIds = ConcurrentHashMap.newKeySet();

    // 검색 음식점 ID별 상세 메뉴 원본
    private final Map<String, List<String>> restaurantMenus = new ConcurrentHashMap<>();

    private final Map<String, String> restaurantOpeningHours = new ConcurrentHashMap<>();

    // 보충 조회의 정상 종료 여부, 메뉴 출처 확인 결과와 별도 보관
    private final Set<String> completedRestaurantLookups = ConcurrentHashMap.newKeySet();

    // TourAPI 요청용 contentId 복원, 접두사가 없으면 원본 유지
    public static String contentId(String candidateId) {

        return candidateId != null && candidateId.startsWith(TOUR_PREFIX)
                ? candidateId.substring(TOUR_PREFIX.length())
                : candidateId;
    }

    public Candidate record(Kor2KeywordSearchResponse.Item item) {

        Candidate candidate = new Candidate(
                TOUR_PREFIX + item.contentid(),
                item.contenttypeid(),
                item.lclsSystm2(),
                null,
                item.title(),
                String
                        .join(
                        " ",
                        text(item.addr1()),
                        text(item.addr2())
                )
                        .trim(),
                item.mapx(),
                item.mapy(),
                item.firstimage(),
                item.firstimage2()
        );

        candidates.put(candidate.candidateId(), candidate);

        return candidate;
    }

    public void record(PlaceWithRouteResult result) {

        if (result != null && result.found() && result.candidateId() != null) {
            candidates.put(result.candidateId(), new Candidate(
                    result.candidateId(),
                    result.categoryCode(),
                    null,
                    result.categoryName(),
                    result.placeName(),
                    result.address(),
                    result.longitude(),
                    result.latitude(),
                    null,
                    null
            ));
        }
    }

    /**
     * 이번 호출에서 검색한 관광지 후보
     */
    public List<Candidate> attractionCandidates() {

        return byType(ATTRACTION_CONTENT_TYPE_ID);
    }

    /**
     * 이번 호출에서 검색한 음식점 후보
     */
    public List<Candidate> restaurantCandidates() {

        return byType(RESTAURANT_CONTENT_TYPE_ID);
    }

    private List<Candidate> byType(String contentTypeId) {

        return candidates
                .values()
                .stream()
                .filter(candidate -> contentTypeId.equals(candidate.type()))
                .sorted(Comparator.comparing(Candidate::candidateId))
                .toList();
    }

    // 장소명·메뉴명의 공백 무시 비교 기준
    public static String normalizeName(String name) {

        return name == null
                ? ""
                : name.replaceAll(
                        "\\s+",
                        ""
                );
    }

    public List<Candidate> searchedCandidates() {

        return candidates
                .values()
                .stream()
                .sorted(Comparator.comparing(Candidate::candidateId))
                .toList();
    }

    public boolean hasRestaurantMenuSource(String candidateId) {

        return restaurantMenus.containsKey(candidateId);
    }

    public Candidate find(String id) {

        return id == null ? null : candidates.get(id);
    }

    /**
     * 이름이 유일한 후보의 조회
     */
    public Candidate findByName(String name) {

        if (name == null || name.isBlank()) {
            return null;
        }

        String target = name.strip();

        List<Candidate> matches = candidates
                .values()
                .stream()
                .filter(candidate -> candidate.name() != null
                        && target.equals(candidate
                                        .name()
                                        .strip()))
                .limit(2)
                .toList();

        return matches.size() == 1 ? matches.getFirst() : null;
    }

    public void recordStandardFoodName(String menuName, String standardFoodName) {

        if (menuName == null || menuName.isBlank()) {
            return;
        }

        if (standardFoodName == null || standardFoodName.isBlank()) {
            standardFoodNames.remove(menuName.strip());
        } else {
            standardFoodNames.put(menuName.strip(), standardFoodName.strip());
        }
    }

    public String standardFoodName(String menuName) {

        return menuName == null ? null : standardFoodNames.get(menuName.strip());
    }

    public List<String> recordedRestaurantMenus(String candidateId) {

        return restaurantMenus.getOrDefault(candidateId, List.of());
    }

    public void clear() {

        candidates.clear();
        standardFoodNames.clear();
        pinnedIds.clear();
        restaurantMenus.clear();
        restaurantOpeningHours.clear();
        completedRestaurantLookups.clear();
    }

    /**
     * 사용자 지정 장소를 관광지 후보로 기록하고 고정
     */
    public Candidate pin(Kor2KeywordSearchResponse.Item item) {

        Candidate candidate = record(item);
        pinnedIds.add(candidate.candidateId());

        return candidate;
    }

    /**
     * 고정한 지정 장소 후보
     */
    public List<Candidate> pinnedCandidates() {

        return pinnedIds
                .stream()
                .map(candidates::get)
                .filter(Objects::nonNull)
                .sorted(Comparator.comparing(Candidate::candidateId))
                .toList();
    }

    /**
     * 검색 음식점과 일치하는 상세 메뉴 기록
     */
    public void recordRestaurantDetail(
            String candidateId,
            Kor2RestaurantIntroResponse response
    ) {

        String id = TOUR_PREFIX + contentId(candidateId);
        Candidate candidate = find(id);

        if (candidate == null || !RESTAURANT_CONTENT_TYPE_ID.equals(candidate.type())
                || response == null || response.resultCode() == null
                || !Set.of("0000", "00").contains(response.resultCode())
                || response.response().body() == null
                || response.response().body().items() == null
                || response.response().body().items().item() == null) {
            return;
        }

        List<Kor2RestaurantIntroResponse.Item> details = response
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(Objects::nonNull)
                .filter(item -> contentId(id).equals(item.contentid()))
                .filter(item -> RESTAURANT_CONTENT_TYPE_ID.equals(item.contenttypeid()))
                .toList();

        if (details.isEmpty()) {
            return;
        }

        List<String> menus = details
                .stream()
                .flatMap(item -> Stream.of(item.firstmenu(), item.treatmenu()))
                .filter(Objects::nonNull)
                .flatMap(menu -> splitRestaurantMenus(menu)
                        .stream())
                .map(String::strip)
                .filter(menu -> !menu.isBlank())
                .distinct()
                .toList();

        restaurantMenus.put(id, menus);

        List<String> hours = details
                .stream()
                .map(Kor2RestaurantIntroResponse.Item::opentimefood)
                .filter(Objects::nonNull)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();

        // 상충 원문과 저장 컬럼 초과 원문의 영업시간 미확정
        String openingHours = hours.size() == 1 ? hours.getFirst() : null;

        if (openingHours != null
                && openingHours.codePointCount(0, openingHours.length()) <= MAX_STORED_OPENING_HOURS) {
            restaurantOpeningHours.put(id, openingHours);
        } else {
            restaurantOpeningHours.remove(id);
        }
    }

    public String restaurantOpeningHours(String candidateId) {

        return restaurantOpeningHours.get(candidateId);
    }

    /**
     * 괄호 내부 표현을 보존한 최상위 메뉴 분리
     */
    private List<String> splitRestaurantMenus(String source) {

        List<String> menus = new ArrayList<>();
        Matcher boundaries = MENU_BOUNDARIES.matcher(source);
        int depth = 0;
        int start = 0;

        while (boundaries.find()) {
            String token = boundaries.group();

            if ("(".equals(token)) {
                depth++;
            } else if (")".equals(token)) {
                if (depth == 0) {
                    return List.of();
                }

                depth--;
            } else if (depth == 0) {
                menus.add(source.substring(start, boundaries.start()));
                start = boundaries.end();
            }
        }

        // 짝이 없는 괄호의 메뉴 경계 미확정
        if (depth != 0) {
            return List.of();
        }

        menus.add(source.substring(start));

        return menus;
    }

    /**
     * 요청 안 상세 메뉴 조회 및 중복 외부 조회 방지
     */
    public List<String> restaurantMenus(
            String candidateId,
            Supplier<Kor2RestaurantIntroResponse> loader
    ) {

        if (!restaurantMenus.containsKey(candidateId) && !completedRestaurantLookups.contains(candidateId)) {
            recordRestaurantDetail(candidateId, loader.get());
            completedRestaurantLookups.add(candidateId);
        }

        return restaurantMenus.getOrDefault(candidateId, List.of());
    }

    /**
     * 검색 음식점의 상세 메뉴 선택 검증
     */
    public String restaurantMenuFailure(
            String candidateId,
            String menuName
    ) {

        Candidate candidate = find(candidateId);

        if (candidate == null) {
            return "음식점 후보 미확인: " + candidateId;
        }

        if (!RESTAURANT_CONTENT_TYPE_ID.equals(candidate.type())) {
            return "음식점 후보 유형 불일치: " + candidateId;
        }

        List<String> menus = restaurantMenus.get(candidateId);

        if (menus == null) {
            return "음식점 상세 메뉴 미확인: " + candidateId + " / getRestaurantDetail 조회 필요";
        }

        if (menus.isEmpty()) {
            return "음식점 상세 메뉴 없음: " + candidateId;
        }

        if (menuName == null || menuName.isBlank() || !menus.contains(menuName.strip())) {
            return "음식점 메뉴 원본 불일치: " + candidateId
                    + " / 선택 메뉴(JSON): " + MENU_MAPPER.writeValueAsString(menuName)
                    + " / 선택 가능 메뉴(JSON 배열): " + MENU_MAPPER.writeValueAsString(menus)
                    + " / 배열의 문자열 하나를 괄호 내부 표현까지 그대로 선택";
        }

        return null;
    }

    private static String text(String value) {

        return value == null ? "" : value;
    }

    public record Candidate(
            String candidateId,
            String type,
            // TourAPI 분류 코드(lclsSystm2), 카카오 후보에는 없음
            String categoryCode,
            String categoryName,
            String name,
            String address,
            String longitude,
            String latitude,
            String imageUrl,
            String thumbnailUrl
    ) { }
}
