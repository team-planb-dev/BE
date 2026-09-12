package com.planb.unit.global.ai.tool;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanTourismToolTest {

    @Test
    @DisplayName("지역 관광지 후보의 candidateId와 장소 identity 유지")
    void recordsRegionalAttractionCandidateIdentity() {

        TourismTool tourismTool = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        Kor2KeywordSearchResponse.Item item = mock(
                Kor2KeywordSearchResponse.Item.class
        );

        when(item.contentid())
                .thenReturn("123");

        when(item.contenttypeid())
                .thenReturn("12");

        when(item.title())
                .thenReturn("경복궁");

        when(item.addr1())
                .thenReturn("서울특별시 종로구 사직로 161");

        when(item.mapx())
                .thenReturn("126.9769");

        when(item.mapy())
                .thenReturn("37.5796");

        when(
                tourismTool
                        .searchAttractionsByRegion(
                                "서울",
                                "종로구"
                        )
        )
                .thenReturn(response(item));

        PlanTourismTool tool = new PlanTourismTool(
                tourismTool,
                candidates
        );

        List<PlaceCandidateContext.Candidate> result = tool
                .searchAttractionsByRegion(
                        "서울",
                        "종로구"
                );

        assertEquals("tour:123", result.getFirst().candidateId());
        assertEquals("경복궁", result.getFirst().name());
        assertEquals("126.9769", result.getFirst().longitude());
        assertEquals("37.5796", result.getFirst().latitude());
        assertSame(
                result.getFirst(),
                candidates.find("tour:123")
        );
    }

    @Test
    @DisplayName("candidateId로 요청한 음식점 상세 조회의 tour 접두사 제거")
    void stripsCandidateIdPrefixBeforeRestaurantDetailLookup() {

        TourismTool tourismTool = mock(TourismTool.class);

        PlanTourismTool tool = new PlanTourismTool(
                tourismTool,
                new PlaceCandidateContext()
        );

        tool.getRestaurantDetail("tour:126508");

        verify(tourismTool)
                .getRestaurantDetail("126508");
    }

    @Test
    @DisplayName("접두사 없는 contentId의 음식점 상세 조회 그대로 전달")
    void passesBareContentIdUnchanged() {

        TourismTool tourismTool = mock(TourismTool.class);

        PlanTourismTool tool = new PlanTourismTool(
                tourismTool,
                new PlaceCandidateContext()
        );

        tool.getRestaurantDetail("126508");

        verify(tourismTool)
                .getRestaurantDetail("126508");
    }

    @Test
    @DisplayName("같은 요청의 correction 재시도는 최초 관광지 후보를 재사용")
    void reusesInitialAttractionCandidatesDuringCorrectionRetry() {

        TourismTool tourismTool = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        Kor2KeywordSearchResponse.Item item = mock(
                Kor2KeywordSearchResponse.Item.class
        );

        when(item.contentid())
                .thenReturn("123");

        when(item.contenttypeid())
                .thenReturn("12");

        when(item.title())
                .thenReturn("경복궁");

        when(
                tourismTool
                        .searchAttractionsByRegion(
                                "서울",
                                "종로구"
                        )
        ).thenReturn(response(item));

        PlanTourismTool tool = new PlanTourismTool(
                tourismTool,
                candidates
        );

        List<PlaceCandidateContext.Candidate> first = tool
                .searchAttractionsByRegion(
                        "서울",
                        "종로구"
                );

        List<PlaceCandidateContext.Candidate> correction = tool
                .searchAttractionsByRegion(
                        "서울",
                        "종로구"
                );

        assertEquals(first, correction);
        assertSame(
                correction.getFirst(),
                candidates.find("tour:123")
        );

        verify(
                tourismTool,
                times(1)
        ).searchAttractionsByRegion(
                "서울",
                "종로구"
        );
    }

    @Test
    @DisplayName("카페 후보 검색 시 CE7 카테고리만 요청")
    void findsCafeWithCafeCategory() {

        TourismTool tourismTool = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        PlaceWithRouteResult expected = new PlaceWithRouteResult(
                true,
                "카페 황남다락",
                "경주시 황남동",
                "129.2",
                "35.8",
                10,
                "kakao:cafe",
                "CE7",
                "음식점 > 카페"
        );

        when(
                tourismTool
                        .findPlaceWithRoute(
                                "경주 카페 황남다락",
                                "첨성대",
                                Transportation.TRANSIT,
                                List.of(),
                                "CE7",
                                null,
                                null
                        )
        ).thenReturn(expected);

        PlanTourismTool tool = new PlanTourismTool(
                tourismTool,
                candidates
        );

        PlaceWithRouteResult result = tool.findPlaceWithRoute(
                "경주 카페 황남다락",
                "첨성대",
                Transportation.TRANSIT,
                List.of(),
                CourseType.CAFE_REST
        );

        assertSame(expected, result);
        assertNotNull(candidates.find("kakao:cafe"));
    }

    @Test
    @DisplayName("직전 장소가 이번 호출 후보면 확정 좌표를 함께 전달")
    void passesConfirmedCoordinatesOfPreviousCandidate() {

        // 이름만 넘기면 카카오가 전국에서 동명 장소를 다시 찾아 엉뚱한 좌표를 쓴다.
        TourismTool tourismTool = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new PlaceWithRouteResult(
                true,
                "첨성대",
                "경주시 인왕동",
                "129.2",
                "35.8",
                null,
                "kakao:previous",
                "AT4",
                "여행 > 관광명소"
        ));

        PlanTourismTool tool = new PlanTourismTool(
                tourismTool,
                candidates
        );

        tool.findPlaceWithRoute(
                "경주 카페 황남다락",
                "첨성대",
                Transportation.TRANSIT,
                List.of(),
                CourseType.CAFE_REST
        );

        verify(tourismTool).findPlaceWithRoute(
                "경주 카페 황남다락",
                "첨성대",
                Transportation.TRANSIT,
                List.of(),
                "CE7",
                "129.2",
                "35.8"
        );
    }

    @Test
    @DisplayName("후보 ID로 확정한 두 장소의 원본 좌표를 유지하여 경로 조회")
    void getsRouteWithCoordinatesFromCandidateIdentity() {

        KakaoMapServiceHandler kakao = mock(KakaoMapServiceHandler.class);
        TourismTool tourismTool = new TourismTool(
                mock(Kor2ServiceHandler.class),
                kakao,
                mock(NutritionService.class),
                mock(NutritionEvaluationCollector.class));
        PlaceCandidateContext candidates = new PlaceCandidateContext();

        candidates.record(new PlaceWithRouteResult(
                true,
                "불국사",
                "경상북도 경주시 불국로 385",
                "129.331843",
                "35.789915",
                null,
                "kakao:origin",
                "",
                "문화,예술 > 종교 > 불교 > 절,사찰"));
        candidates.record(new PlaceWithRouteResult(
                true,
                "교동쌈밥",
                "경상북도 경주시 첨성로 77",
                "129.219431",
                "35.834921",
                null,
                "kakao:destination",
                "FD6",
                "음식점"));

        KakaoRouteResult expected = new KakaoRouteResult(
                "불국사",
                "교동쌈밥",
                15000,
                35);

        when(kakao.getRoute(
                "불국사",
                "교동쌈밥",
                Transportation.TRANSIT,
                "129.331843",
                "35.789915",
                "129.219431",
                "35.834921"))
                .thenReturn(Mono.just(expected));

        PlanTourismTool tool = new PlanTourismTool(tourismTool, candidates);

        KakaoRouteResult result = tool.getRoute(
                "kakao:origin",
                "kakao:destination",
                Transportation.TRANSIT);

        assertSame(expected, result);

        verify(kakao)
                .getRoute(
                        "불국사",
                        "교동쌈밥",
                        Transportation.TRANSIT,
                        "129.331843",
                        "35.789915",
                        "129.219431",
                        "35.834921");
    }
    private Kor2KeywordSearchResponse response(
            Kor2KeywordSearchResponse.Item item
    ) {

        return new Kor2KeywordSearchResponse(
                new Kor2KeywordSearchResponse.Response(
                        null,
                        new Kor2KeywordSearchResponse.Body(
                                new Kor2KeywordSearchResponse.Items(List.of(item)),
                                1,
                                1,
                                1
                        )
                )
        );
    }

}
