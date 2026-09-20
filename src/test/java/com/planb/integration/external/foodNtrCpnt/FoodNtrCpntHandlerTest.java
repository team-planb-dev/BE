package com.planb.integration.external.foodNtrCpnt;

import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.travel.dto.nutrition.NutritionEvaluationResult;
import com.planb.domain.travel.entity.constant.NutritionEvaluationStatus;
import com.planb.domain.travel.service.NutritionService;
import com.planb.global.client.foodNtrCpnt.dto.request.FoodNtrCpntSearchRequest;
import com.planb.global.client.foodNtrCpnt.dto.response.FoodNtrCpntResponse;
import com.planb.global.client.foodNtrCpnt.handler.FoodNtrCpntHandler;
import com.planb.integration.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
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
@Tag("external")
class FoodNtrCpntHandlerTest extends IntegrationTest {

    @Autowired
    private FoodNtrCpntHandler foodNtrCpntHandler;

    @Autowired
    private NutritionService nutritionService;

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

        });
    }

    @Test
    @DisplayName("1회분량 근거 없는 실제 영양정보 평가 불가")
    void nutritionWithoutReliableServing() {

        NutritionEvaluationResult result = nutritionService
                .evaluateFoodNutrition(
                        "막국수",
                        List.of(DiseaseType.DIABETES)
                )
                .block();

        assertThat(result)
                .isNotNull();

        assertThat(result.status())
                .isEqualTo(NutritionEvaluationStatus.NOT_EVALUABLE);

        assertThat(result.evaluations())
                .isEmpty();
    }

    @Test
    @DisplayName("식품 영양성분 API가 부분일치를 하는지 확인")
    void partialMatchProbe() {

        List.of(
                "칼국수",
                "장칼국수",
                "검은콩 장칼국수"
        ).forEach(keyword -> {

            List<FoodNtrCpntResponse.Item> items = foodNtrCpntHandler
                    .getFoodNutrition(FoodNtrCpntSearchRequest.of(keyword))
                    .block();

            System.out.println(keyword + " 결과 수: " + items.size());

            items.forEach(item -> System.out.println("  " + item.foodName()));
        });
    }

    @Test
    @DisplayName("접미사가 같은 다른 실제 음식 제외")
    void excludeDifferentFoodWithSameSuffix() {

        List<FoodNtrCpntResponse.Item> items = foodNtrCpntHandler
                .getFoodNutrition(
                        FoodNtrCpntSearchRequest.of("순두부")
                )
                .block();

        assertThat(items)
                .isNotNull()
                .noneMatch(item ->
                        "달걀탕_순두부".equals(
                                item.foodName()
                        )
                );
    }

}
