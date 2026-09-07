package com.planb.unit.global.ai.tool;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.mcp.NutritionEvaluationCollector;
import com.planb.ai.mcp.PlanTourismTool;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanTourismToolTest {

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
}
