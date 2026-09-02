package com.planb.ai.prompt;

import com.planb.domain.travel.entity.constant.Transportation;

import java.util.List;

public record AttractionRecommendPrompt(
        String locationDo,
        String locationSigungu,
        String decidedLocation,
        String previousLocation,
        Transportation transportation,
        List<String> excludeNames
) implements AiPrompt {

    @Override
    public String system() {
        return """
                너는 여행 일정 중 관광지(ATTRACTION) 장소 하나를 새로 확정하는 AI다.

                findPlaceWithRoute(keyword, previousLocation, transportation, excludeNames) Tool을
                사용해 실제로 존재하고 아직 사용되지 않은 관광지를 확인한다.
                이 Tool은 searchTourismByLocation(contentTypeId=12)으로 확인되지 않거나
                이미 사용된 관광지를 대체할 때 사용하는 최후 확인 수단이다.

                - keyword에는 지역명(또는 구역명)을 포함한 실제 관광지명 후보를 사용한다.
                  "관광지", "명소"처럼 너무 일반적인 keyword는 사용하지 않는다.
                - 음식점 상호명, 메뉴명 등 RESTAURANT 관련 키워드는 사용하지 않는다.
                - previousLocation, transportation, excludeNames는 입력으로 주어진 값을 그대로 전달한다.
                - found가 false이면 keyword를 다른 후보로 바꾸어 최대 1회 재호출한다.
                  재호출해도 found가 false이면 더 이상 시도하지 않고 그 결과를 그대로 반환한다.
                - Tool 결과에 없는 이름, 주소, 좌표를 임의로 생성하지 않는다.
                - 최종 응답은 findPlaceWithRoute의 마지막 호출 결과를 그대로,
                  지정된 JSON 형식(found, placeName, address, longitude, latitude, travelMinutes)에
                  맞춰 반환한다. Tool을 호출하지 않고 임의로 응답을 생성하지 않는다.
                """;
    }

    @Override
    public String user() {
        return """
                locationDo: %s
                locationSigungu: %s
                decidedLocation: %s
                previousLocation: %s
                transportation: %s
                excludeNames: %s
                """.formatted(
                locationDo,
                locationSigungu,
                decidedLocation,
                previousLocation,
                transportation,
                excludeNames
        );
    }
}
