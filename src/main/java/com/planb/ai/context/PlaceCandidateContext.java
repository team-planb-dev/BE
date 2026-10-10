package com.planb.ai.context;

import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class PlaceCandidateContext {

    // TourAPI 후보의 candidateId 접두사와 외부 API용 contentId 구분
    private static final String TOUR_PREFIX = "tour:";

    // 관광지·음식점 구분용 TourAPI contentTypeId
    private static final String ATTRACTION_CONTENT_TYPE_ID = "12";

    private static final String RESTAURANT_CONTENT_TYPE_ID = "39";

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();

    // 사용자 지정 장소로 고정한 관광지 후보 ID
    private final Set<String> pinnedIds = ConcurrentHashMap.newKeySet();

    // 검색 음식점 ID별 상세 메뉴 원본
    private final Map<String, List<String>> restaurantMenus = new ConcurrentHashMap<>();

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

    public void clear() {

        candidates.clear();
        pinnedIds.clear();
        restaurantMenus.clear();
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
                .flatMap(menu -> Arrays.stream(menu.split("[,/;|\\n]|(?i)<br\\s*/?>")))
                .map(String::strip)
                .filter(menu -> !menu.isBlank())
                .distinct()
                .toList();

        restaurantMenus.put(id, menus);
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
            return "음식점 메뉴 원본 불일치: " + candidateId + " / 선택 가능 메뉴: " + menus;
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
