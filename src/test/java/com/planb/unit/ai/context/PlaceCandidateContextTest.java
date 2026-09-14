package com.planb.unit.ai.context;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlaceCandidateContextTest {

    @Test
    @DisplayName("이름이 하나뿐인 후보의 좌표 조회")
    void findsCandidateByUniqueName() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(candidate("kakao:1", "첨성대", "129.2", "35.8"));
        candidates.record(candidate("kakao:2", "카페 황남다락", "129.3", "35.9"));

        PlaceCandidateContext.Candidate found = candidates.findByName("첨성대");

        assertEquals("129.2", found.longitude());

        assertEquals("35.8", found.latitude());
    }

    @Test
    @DisplayName("같은 이름 후보가 둘 이상이면 좌표 미확정")
    void ignoresAmbiguousName() {

        // 어느 쪽인지 고를 근거가 없다. 임의로 하나를 고르면 고치려는 오지오코딩과 같은 실수가 된다.
        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(candidate("kakao:1", "고려삼계탕", "126.9", "37.5"));
        candidates.record(candidate("kakao:2", "고려삼계탕", "127.4", "36.6"));

        assertNull(candidates.findByName("고려삼계탕"));
    }

    @Test
    @DisplayName("등록되지 않은 이름의 좌표 미확정")
    void ignoresUnknownName() {

        assertNull(new PlaceCandidateContext().findByName("강릉역"));
    }

    @Test
    @DisplayName("관광지 후보와 음식점 후보를 구분해 조회")
    void separatesCandidatesByContentType() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(tourItem("126508", "12", "경복궁"));
        candidates.record(tourItem("134712", "39", "토속촌삼계탕"));

        assertEquals(
                List.of("경복궁"),
                candidates.attractionCandidates()
                        .stream()
                        .map(PlaceCandidateContext.Candidate::name)
                        .toList()
        );

        assertEquals(
                List.of("토속촌삼계탕"),
                candidates.restaurantCandidates()
                        .stream()
                        .map(PlaceCandidateContext.Candidate::name)
                        .toList()
        );
    }

    private Kor2KeywordSearchResponse.Item tourItem(
            String contentId,
            String contentTypeId,
            String title
    ) {

        return new Kor2KeywordSearchResponse.Item(
                "서울특별시 종로구",
                "",
                null,
                contentId,
                contentTypeId,
                null,
                null,
                null,
                null,
                "126.9",
                "37.5",
                null,
                null,
                null,
                title,
                null,
                null,
                null,
                null,
                null
        );
    }

    private PlaceWithRouteResult candidate(
            String candidateId,
            String name,
            String longitude,
            String latitude
    ) {

        return new PlaceWithRouteResult(
                true,
                name,
                "주소",
                longitude,
                latitude,
                null,
                candidateId,
                "CE7",
                "음식점 > 카페"
        );
    }
}
