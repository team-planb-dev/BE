package com.planb.ai.context;

import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceCandidateContext {

    // TourAPI 후보의 candidateId 접두사와 외부 API용 contentId 구분
    private static final String TOUR_PREFIX = "tour:";

    // 관광지·음식점 구분용 TourAPI contentTypeId
    private static final String ATTRACTION_CONTENT_TYPE_ID = "12";

    private static final String RESTAURANT_CONTENT_TYPE_ID = "39";

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();

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
