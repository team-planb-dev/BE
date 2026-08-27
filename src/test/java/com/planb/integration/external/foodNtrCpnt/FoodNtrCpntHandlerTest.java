package com.planb.integration.external.foodNtrCpnt;

import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 식품의약품안전처 식품 영양성분 API Handler 통합 테스트
 *
 * 실제 외부 API 호출 및 요청 정상 수행 확인.
 * FoodNtrCpntResponse DTO 응답 파싱 검증.
 * Handler 내부 응답 정제 로직 검증.
 */
class FoodNtrCpntHandlerTest extends IntegrationTest {

    @Autowired
    private FoodNtrCpntHandler foodNtrCpntHandler;

    @Test
    @DisplayName("식품 영양성분 API 호출 및 응답 파싱")
    void searchFoodNutrition() {

        FoodNtrCpntResponse response = foodNtrCpntHandler
                .searchFoodNutrition(
                        FoodNtrCpntSearchRequest
                                .of("막국수")
                )
                .block();

        assertThat(response)
                .isNotNull();

        assertThat(response.header())
                .isNotNull();

        assertThat(response.header().resultCode())
                .isEqualTo("00");

        assertThat(response.body())
                .isNotNull();

        assertThat(response.body().items())
                .isNotEmpty();

        FoodNtrCpntResponse.Item firstItem = response
                .body()
                .items()
                .getFirst();

        assertThat(firstItem.foodName())
                .isNotBlank();

        assertThat(firstItem.foodCode())
                .isNotBlank();
    }

    @Test
    @DisplayName("식품 영양성분 API 조회 결과 정제")
    void getFoodNutrition() {

        List<FoodNtrCpntResponse.Item> response = foodNtrCpntHandler
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest
                                .of("막국수")
                )
                .block();

        assertThat(response)
                .isNotNull()
                .isNotEmpty();

        response.forEach(item -> {

            assertThat(item.dbGroupName())
                    .isEqualTo("음식");

            assertThat(item.foodOriginName())
                    .doesNotContain("급식");
        });
    }
}