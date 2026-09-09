package com.planb.unit.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.request.CreateRestaurantDetailRequest;
import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.RestaurantDetail;
import com.planb.domain.travel.repository.RestaurantDetailRepository;
import com.planb.domain.travel.service.RestaurantDetailService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestaurantDetailServiceTest {

    @Mock
    private RestaurantDetailRepository restaurantDetailRepository;

    @InjectMocks
    private RestaurantDetailService restaurantDetailService;

    @Test
    @DisplayName("RestaurantDetail 객체 생성")
    void createRestaurantDetail() {

        PlanSchedule planSchedule =
                PlanSchedule.builder()
                        .locationName("막국수집")
                        .build();

        CreateRestaurantDetailRequest request =
                new CreateRestaurantDetailRequest(
                        "막국수",
                        50.0,
                        500.0,
                        10.0,
                        "10:00 ~ 20:00",
                        "강원도 춘천시",
                        "127.0",
                        "37.0",
                        "image-url"
                );

        RestaurantDetail restaurantDetail =
                restaurantDetailService.createRestaurantDetail(
                        planSchedule,
                        request
                );

        assertSame(
                planSchedule,
                restaurantDetail.getPlanSchedule()
        );

        assertEquals(
                "막국수",
                restaurantDetail.getMenuName()
        );

        assertEquals(
                50.0,
                restaurantDetail.getCarbohydrate()
        );

        assertEquals(
                500.0,
                restaurantDetail.getSodium()
        );

        assertEquals(
                10.0,
                restaurantDetail.getFat()
        );

        assertEquals(
                "10:00 ~ 20:00",
                restaurantDetail.getOpenTime()
        );

        assertEquals(
                "강원도 춘천시",
                restaurantDetail.getAddress()
        );

        assertEquals(
                "127.0",
                restaurantDetail.getLongitude()
        );

        assertEquals(
                "37.0",
                restaurantDetail.getLatitude()
        );

        assertEquals(
                "image-url",
                restaurantDetail.getImageUrl()
        );
    }

    @Test
    @DisplayName("RestaurantDetail 객체 리스트 생성")
    void makeRestaurantDetailList() {

        PlanSchedule attractionSchedule =
                PlanSchedule.builder()
                        .locationName("관광지")
                        .build();

        PlanSchedule restaurantSchedule =
                PlanSchedule.builder()
                        .locationName("막국수집")
                        .build();

        CreatePlanAiResponse.PlanScheduleDetail attractionDetail =
                org.mockito.Mockito.mock(
                        CreatePlanAiResponse.PlanScheduleDetail.class
                );

        CreatePlanAiResponse.RestaurantDetail restaurant =
                new CreatePlanAiResponse.RestaurantDetail(
                        "막국수",
                        50.0,
                        500.0,
                        10.0,
                        "10:00 ~ 20:00",
                        "강원도 춘천시",
                        "127.0",
                        "37.0",
                        "image-url"
                );

        CreatePlanAiResponse.PlanScheduleDetail restaurantScheduleDetail =
                org.mockito.Mockito.mock(
                        CreatePlanAiResponse.PlanScheduleDetail.class
                );

        org.mockito.Mockito.when(
                attractionDetail.restaurantDetail()
        ).thenReturn(null);

        org.mockito.Mockito.when(
                restaurantScheduleDetail.restaurantDetail()
        ).thenReturn(restaurant);

        List<RestaurantDetail> result =
                restaurantDetailService.makeRestaurantDetailList(
                        List.of(
                                attractionSchedule,
                                restaurantSchedule
                        ),
                        List.of(
                                attractionDetail,
                                restaurantScheduleDetail
                        )
                );

        assertEquals(
                1,
                result.size()
        );

        assertSame(
                restaurantSchedule,
                result.get(0)
                        .getPlanSchedule()
        );

        assertEquals(
                "막국수",
                result.get(0)
                        .getMenuName()
        );
    }

    @Test
    @DisplayName("RestaurantDetail 객체 리스트 일괄 저장")
    void saveRestaurantDetailAll() {

        List<RestaurantDetail> restaurantDetails =
                List.of(
                        RestaurantDetail.builder()
                                .menuName("막국수")
                                .build(),
                        RestaurantDetail.builder()
                                .menuName("닭갈비")
                                .build()
                );

        restaurantDetailService.saveRestaurantDetailAll(
                restaurantDetails
        );

        verify(restaurantDetailRepository)
                .saveAll(restaurantDetails);
    }

    @Test
    @DisplayName("특정 PlanSchedule 목록에 속한 RestaurantDetail 리스트 일괄 삭제")
    void deleteAllByPlanScheduleIn() {

        PlanSchedule planSchedule =
                PlanSchedule.builder()
                        .locationName("막국수집")
                        .build();

        restaurantDetailService.deleteAllByPlanScheduleIn(
                List.of(planSchedule)
        );

        verify(restaurantDetailRepository)
                .deleteAllByPlanScheduleIn(List.of(planSchedule));
    }
}