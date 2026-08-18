package com.planb.unit.domain.health.service;

import com.planb.domain.health.dto.request.CreateFoodInfoRequest;
import com.planb.domain.health.entity.FoodInfo;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.repository.FoodInfoRepository;
import com.planb.domain.health.service.FoodInfoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FoodInfoServiceTest {

    @Mock
    private FoodInfoRepository foodInfoRepository;

    @Mock
    private Health health;

    private FoodInfoService foodInfoService;

    @BeforeEach
    void setUp() {
        foodInfoService =
                new FoodInfoService(foodInfoRepository);
    }

    @Test
    @DisplayName("음식 정보 요청 기반 FoodInfo 리스트 생성")
    void makeFoodInfoListSuccess() {

        // given
        FoodType foodType = FoodType.values()[0];

        CreateFoodInfoRequest request =
                new CreateFoodInfoRequest(
                        health,
                        List.of(
                                new CreateFoodInfoRequest.FoodInfoDetail(
                                        "땅콩",
                                        foodType
                                ),
                                new CreateFoodInfoRequest.FoodInfoDetail(
                                        "우유",
                                        foodType
                                )
                        )
                );

        // when
        List<FoodInfo> result =
                foodInfoService.makeFoodInfoList(request);

        // then
        assertEquals(2, result.size());

        assertSame(
                health,
                result.get(0).getHealth()
        );

        assertEquals(
                "땅콩",
                result.get(0).getFoodName()
        );

        assertEquals(
                foodType,
                result.get(0).getFoodType()
        );
    }

    @Test
    @DisplayName("FoodInfo 리스트 일괄 저장")
    void saveFoodInfoAllSuccess() {

        // given
        List<FoodInfo> foodInfos =
                List.of(
                        FoodInfo.builder()
                                .health(health)
                                .foodName("땅콩")
                                .foodType(FoodType.values()[0])
                                .build()
                );

        // when
        foodInfoService.saveFoodInfoAll(foodInfos);

        // then
        verify(foodInfoRepository)
                .saveAll(foodInfos);
    }
}