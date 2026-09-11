package com.planb.ai.context;

import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class PlaceCandidateContext {

    // TourAPI 후보의 candidateId 접두사. 외부 API는 접두사 없는 contentId만 받는다.
    private static final String TOUR_PREFIX = "tour:";

    private final Map<String, Candidate> candidates = new ConcurrentHashMap<>();

    // candidateId를 TourAPI가 받는 contentId로 되돌린다. 접두사가 없으면 그대로 둔다.
    public static String contentId(String candidateId) {

        return candidateId != null && candidateId.startsWith(TOUR_PREFIX)
                ? candidateId.substring(TOUR_PREFIX.length())
                : candidateId;
    }

    public Candidate record(Kor2KeywordSearchResponse.Item item) {

        Candidate candidate = new Candidate(
                TOUR_PREFIX + item.contentid(),
                item.contenttypeid(),
                null,
                item.title(),
                String.join(" ", text(item.addr1()), text(item.addr2())).trim(),
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

    public Candidate find(String id) {

        return id == null ? null : candidates.get(id);
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
            String categoryName,
            String name,
            String address,
            String longitude,
            String latitude,
            String imageUrl,
            String thumbnailUrl
    ) { }
}
