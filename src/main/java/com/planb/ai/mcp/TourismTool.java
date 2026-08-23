package com.planb.ai.mcp;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Component
@RequiredArgsConstructor
public class TourismTool {

    private final Kor2ServiceHandler kor2ServiceHandler;
    private final KakaoMapServiceHandler kakaoMapServiceHandler;

    @Tool(description = """
            한국관광공사의 실제 관광지 데이터를 키워드로 검색합니다.
            여행 일정에 포함할 장소의 존재 여부, 위치, 관광지 유형 등
            실제 정보가 필요한 경우 사용합니다.
            """)
    public Mono<Kor2KeywordSearchResponse> searchTourism(
            String keyword
    ) {

        return kor2ServiceHandler.searchKeyword(
                keyword
        );
    }

    @Tool(description = """
            두 장소 사이의 실제 이동거리와 예상 이동시간을 조회합니다.
            출발지와 도착지의 장소명을 기준으로 좌표를 검색한 뒤,
            사용자의 이동수단에 맞는 실제 경로를 조회합니다.

            TRANSIT은 버스 및 지하철을 포함한 대중교통을 의미합니다.
            CAR는 자가용 이동을 의미합니다.
            """)
    public Mono<KakaoRouteResult> getRoute(
            String origin,
            String destination,
            Transportation transportation
    ) {

        return kakaoMapServiceHandler.getRoute(
                origin,
                destination,
                transportation
        );
    }
}