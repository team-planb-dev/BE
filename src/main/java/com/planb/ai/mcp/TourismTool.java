package com.planb.ai.mcp;

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

    @Tool(description = """
            한국관광공사의 실제 관광지 데이터를 키워드로 검색합니다.
            여행 일정에 포함할 장소의 존재 여부, 위치, 관광지 유형 등
            실제 정보가 필요한 경우 사용합니다.
            """)
    public Mono<Kor2KeywordSearchResponse> searchTourism(String keyword){

        return kor2ServiceHandler.searchKeyword(keyword);
    }
}
