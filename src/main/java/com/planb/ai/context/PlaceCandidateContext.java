package com.planb.ai.context;

import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.handler.GenerationRestaurantCandidates;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class PlaceCandidateContext {

    private GenerationRestaurantCandidates generationRestaurants;

    public void generationRestaurants(GenerationRestaurantCandidates restaurants) {

        this.generationRestaurants = restaurants;
    }

    public GenerationRestaurantCandidates generationRestaurants() {

        return generationRestaurants;
    }

    public void record(Candidate candidate) {

        if (candidate != null) {
            candidates.put(candidate.candidateId(), candidate);
        }
    }

    // TourAPI 후보의 candidateId 접두사와 외부 API용 contentId 구분
    private static final String TOUR_PREFIX = "tour:";

    // 관광지·음식점 구분용 TourAPI contentTypeId
    private static final String ATTRACTION_CONTENT_TYPE_ID = "12";

    private static final String RESTAURANT_CONTENT_TYPE_ID = "39";

    private final Map<MealSelection, String> standardFoodNames = new ConcurrentHashMap<>();

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();

    // 사용자 지정 장소로 고정한 관광지 후보 ID
    private final Set<String> pinnedIds = ConcurrentHashMap.newKeySet();

    // 음식점 후보 ID별 대표 메뉴, 요청 안 상세 재조회 방지
    private final Map<String, Optional<String>> representativeMenus = new ConcurrentHashMap<>();

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

    public void recordStandardFoodName(
            String candidateId,
            String menuName,
            String standardFoodName
    ) {

        if (menuName != null && !menuName.isBlank() && standardFoodName != null && !standardFoodName.isBlank()) {
            standardFoodNames.putIfAbsent(
                    new MealSelection(candidateId, menuName),
                    standardFoodName.strip()
            );
        }
    }

    public String standardFoodName(
            String candidateId,
            String menuName
    ) {

        return standardFoodNames.get(new MealSelection(candidateId, menuName));
    }

    private record MealSelection(
            String candidateId,
            String menuName
    ) {
    }

    public void clearStandardFoodNames() {

        standardFoodNames.clear();
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
        standardFoodNames.clear();
        representativeMenus.clear();
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
     * 음식점 대표 메뉴의 요청 범위 메모이즈, 결과 없음도 기록하고 조회 예외는 기록하지 않음
     */
    public String representativeMenu(
            String candidateId,
            Supplier<String> loader
    ) {

        return representativeMenus
                .computeIfAbsent(
                        candidateId,
                        id -> Optional.ofNullable(loader.get())
                )
                .orElse(null);
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
