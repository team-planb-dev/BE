package com.planb.integration.external.kor2Service;

import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kor2Service 관광정보 조회와 응답 매핑 검증
 */
@Tag("external")
class Kor2ServiceHandlerTest extends IntegrationTest {

    @Autowired
    private Kor2ServiceHandler kor2ServiceHandler;

    @Test
    @DisplayName("관광정보 키워드 검색 API 호출 및 응답 파싱")
    void searchKeyword() {

        Kor2KeywordSearchResponse response = kor2ServiceHandler
                .searchKeywordOnly("해운대")
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.response())
                .isNotNull();

        assertThat(response
                        .response()
                        .header())
                .isNotNull();

        assertThat(response
                        .response()
                        .header()
                        .resultCode())
                .isEqualTo("0000");

        assertThat(response
                        .response()
                        .body())
                .isNotNull();

        assertThat(response
                        .response()
                .body()
                .items())
                .isNotNull();

        assertThat(response
                        .response()
                .body()
                .items()
                .item())
                .isNotEmpty();
    }

    @Test
    @DisplayName("지역 기반 음식점 키워드 검색 API 호출 및 응답 파싱")
    void searchKeywordByLocation() {

        Kor2KeywordSearchResponse response = kor2ServiceHandler
                .searchRestaurants(
                        "돼지국밥",
                        "부산",
                        "해운대구"
                )
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.response())
                .isNotNull();

        assertThat(response
                        .response()
                        .header())
                .isNotNull();

        assertThat(response
                        .response()
                        .header()
                        .resultCode())
                .isEqualTo("0000");

        assertThat(response
                        .response()
                        .body())
                .isNotNull();

        assertThat(response
                        .response()
                .body()
                .items())
                .isNotNull();

        assertThat(response
                        .response()
                .body()
                .items()
                .item())
                .isNotEmpty();

        assertThat(response
                        .response()
                .body()
                .items()
                .item())
                .allSatisfy(item ->
                        assertThat(item.contenttypeid())
                                .isEqualTo("39")
                );
    }

    @Test
    @DisplayName("음식점 소개정보 API 호출 및 응답 파싱")
    void getRestaurantDetail() {

        Kor2KeywordSearchResponse searchResponse = kor2ServiceHandler
                .searchKeywordOnly("막국수")
                .block();

        assertThat(searchResponse)
                .isNotNull();

        String contentId = searchResponse
                .response()
                .body()
                .items()
                .item()
                .stream()
                .filter(item ->
                        "39".equals(
                                item.contenttypeid()
                        )
                )
                .map(
                        Kor2KeywordSearchResponse.Item::contentid
                )
                .findFirst()
                .orElseThrow(() ->
                        new AssertionError(
                                "음식점 타입 검색 결과 없음"
                        )
                );

        Kor2RestaurantIntroResponse response = kor2ServiceHandler
                .getRestaurantDetail(contentId)
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.response())
                .isNotNull();

        assertThat(response
                        .response()
                        .header())
                .isNotNull();

        assertThat(response
                        .response()
                        .header()
                        .resultCode())
                .isEqualTo("0000");

        assertThat(response
                        .response()
                        .body())
                .isNotNull();

        assertThat(response
                        .response()
                .body()
                .items())
                .isNotNull();

        assertThat(response
                        .response()
                .body()
                .items()
                .item())
                .isNotEmpty();

        Kor2RestaurantIntroResponse.Item restaurant = response
                .response()
                .body()
                .items()
                .item()
                .getFirst();

        assertThat(restaurant.contenttypeid())
                .isEqualTo("39");
    }
}
