package com.planb.domain.travel.controller;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.facade.TravelFacade;
import com.planb.global.config.exception.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@Tag(name ="여행 API",description = "여행 생성 관련 도메인")
@RequestMapping("/api/v1/travel")
@RequiredArgsConstructor
public class TravelController {

    private final TravelFacade travelFacade;

    @GetMapping("/recommend-local-food")
    @Operation(summary = "여행지 음식 추천 API",
            description = "입력 받은 지역의 음식을 추천받습니다.")
    public ResponseEntity<ApiResult<MakeRecommendFoodResponse>> recommendLocalFood
            (@RequestParam String locationDo,
             @RequestParam String locationSigungu){

        MakeRecommendFoodsRequest makeRecommendFoodsRequest =
                new MakeRecommendFoodsRequest(
                        locationDo,
                        locationSigungu
                );

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .showRecommendFoods(makeRecommendFoodsRequest)));
    }

    @GetMapping("/search-planned-place")
    @Operation(summary = "장소 찾기 API",
            description = "기존에 계획한 장소(숙박,관광지)를 찾습니다.")
    public Mono<ResponseEntity<ApiResult<SearchPlannedPlaceResponse>>> searchPlannedPlace
            (@RequestParam String searchText){

        SearchPlannedPlaceRequest searchPlannedPlaceRequest =
                new SearchPlannedPlaceRequest(
                        searchText
                );

        return travelFacade
                .searchPlannedPlaceByText(searchPlannedPlaceRequest)
                .map(response ->
                        ResponseEntity
                                .status(HttpStatus.OK)
                                .body(ApiResult
                                        .success(response)));
    }

    @PostMapping("/add-with-recommend")
    @Operation(summary = "여행조건 등록 및 생성 API",
            description = "사용자의 입력에 기반하여 여행조건을 등록합니다." +
                    "그 후,해당 정보에 기반하여 일정을 생성합니다.")
    public ResponseEntity<ApiResult<CreatePlanAiResponse>> addTravelOptionsAndRecommend
            (@RequestBody CreateTravelRequest createTravelRequest,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(ApiResult
                        .success(travelFacade
                                .makeTravelOptionsAndRecommend(
                                        createTravelRequest,
                                        userDetails
                                                .getUsername())));
    }

    @GetMapping("/get-ai-travel-plan")
    @Operation(summary = "여행 계획 전체 조회 API",
            description = "AI가 생성한 여행 세부,전체 정보를 조회합니다.")
    public ResponseEntity<ApiResult<GetAiPlanResponse>> getAiPlanDetailAll
            (@RequestParam Long travelId,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .getAiPlan(
                                        new GetAiPlanRequest(
                                                travelId
                                        ),
                                        userDetails
                                                .getUsername())));
    }

}