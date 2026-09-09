package com.planb.unit.global.client.kakaoMapService;

import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KakaoPlaceSearchHelperTest {

    private final KakaoPlaceSearchHelper helper = new KakaoPlaceSearchHelper();

    @Test
    @DisplayName("카페 검색에서는 앞선 관광지 결과를 건너뛰고 CE7 장소 선택")
    void selectsFirstPlaceMatchingExpectedCategory() {

        KakaoPlaceSearchResponse response =
                new KakaoPlaceSearchResponse(
                        new KakaoPlaceSearchResponse.Meta(
                                2,
                                2,
                                true
                        ),
                        List.of(
                                document(
                                        "attraction",
                                        "황남동 카페거리",
                                        "AT4"
                                ),
                                document(
                                        "cafe",
                                        "카페 황남다락",
                                        "CE7"
                                )
                        )
                );

        KakaoPlaceSearchResponse filtered =
                helper.filterByCategory(
                        response,
                        "CE7"
                );

        assertEquals(
                "카페 황남다락",
                helper.firstPlaceName(filtered)
        );
    }

    private KakaoPlaceSearchResponse.Document document(
            String id,
            String name,
            String categoryCode
    ) {

        return new KakaoPlaceSearchResponse.Document(
                id,
                name,
                "여행 > 관광,명소",
                categoryCode,
                null,
                null,
                "경주시 황남동",
                null,
                "129.2",
                "35.8",
                null,
                null
        );
    }
}
