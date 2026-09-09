package com.planb.domain.travel.controller;

import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.travel.dto.request.MakeRecommendFoodsRequest;
import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.dto.response.SaveTravelResponse;
import com.planb.domain.travel.dto.response.ShareTravelResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.dto.response.TravelListResponse;
import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.domain.travel.facade.TravelFacade;
import com.planb.global.config.exception.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @SecurityRequirement(name = "JWT")
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
    @SecurityRequirement(name = "JWT")
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
                    "그 후,해당 정보에 기반하여 일정을 생성합니다. " +
                    "healthIds에는 이번 여행에 참여할 구성원을 한 명 이상 전달해야 합니다. " +
                    "생성 직후 일정은 저장 확정 전(saved=false) 상태입니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<CreatePlanResponse>> addTravelOptionsAndRecommend
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

    @GetMapping("/list")
    @Operation(summary = "여행 목록 조회 API",
            description = "로그인한 사용자의 여행 목록을 조회합니다. " +
                    "status가 UPCOMING이면 오늘 이후에 끝나는 여행을, PAST면 이미 끝난 여행을 반환합니다. " +
                    "각 여행의 status 필드는 조회 시점 기준으로 예정(UPCOMING), 여행 중(ONGOING), 완료(COMPLETED)를 나타냅니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<TravelListResponse>> getTravelList
            (@RequestParam(defaultValue = "UPCOMING") TravelListFilter status,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .getTravelList(
                                        status,
                                        userDetails
                                                .getUsername())));
    }

    @GetMapping("/get-ai-travel-plan")
    @Operation(summary = "여행 계획 전체 조회 API",
            description = "AI가 생성한 여행 세부,전체 정보를 조회합니다.")
    @SecurityRequirement(name = "JWT")
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


    @PostMapping("/save")
    @Operation(summary = "여행 일정 저장 확정 API",
            description = "AI가 생성한 일정을 저장 확정 상태로 바꿉니다. " +
                    "저장 확정 후에만 공유 링크를 발급할 수 있습니다. 이미 저장된 여행에 다시 요청해도 결과는 같습니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<SaveTravelResponse>> saveTravel
            (@RequestBody GetAiPlanRequest getAiPlanRequest,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .saveTravel(
                                        getAiPlanRequest,
                                        userDetails
                                                .getUsername())));
    }

    @PostMapping("/share/issue")
    @Operation(summary = "여행 일정 공유 링크 발급 API",
            description = "여행 일정을 읽기 전용으로 공유할 토큰을 발급합니다. " +
                    "저장 확정(POST /api/v1/travel/save)한 여행만 공유할 수 있습니다. " +
                    "이미 발급된 여행은 같은 토큰을 반환합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<ShareTravelResponse>> issueShareLink
            (@RequestBody GetAiPlanRequest getAiPlanRequest,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .createShareLink(
                                        getAiPlanRequest,
                                        userDetails
                                                .getUsername())));
    }

    @GetMapping("/shared/{shareToken}")
    @Operation(summary = "공유된 여행 일정 조회 API",
            description = "공유 링크 토큰으로 여행 일정을 조회합니다. 로그인이 필요 없으며 읽기 전용입니다. " +
                    "동행인의 질환과 복약 시간은 포함되지 않습니다.")
    public ResponseEntity<ApiResult<GetAiPlanResponse>> getSharedPlan
            (@PathVariable String shareToken){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .getSharedPlan(shareToken)));
    }

    @PostMapping("/edit-plan/preview")
    @Operation(summary = "AI 일정 수정 미리보기 생성",
            description = "자연어 수정 요청을 받아 AI가 생성한 수정안을 기존 일정과 함께 미리보기로 반환합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<EditPlanPreviewResponse>> previewEditPlan
            (@RequestBody EditPlanRequest editPlanRequest,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .makeEditPlanPreview(
                                        editPlanRequest,
                                        userDetails
                                                .getUsername())));
    }

    @PostMapping("/edit-plan/confirm")
    @Operation(summary = "AI 일정 수정 확정",
            description = "미리보기로 생성된 수정안을 실제 일정에 반영합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<CreatePlanResponse>> confirmEditPlan
            (@RequestBody GetAiPlanRequest getAiPlanRequest,
             @AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .confirmEditPlan(
                                        getAiPlanRequest,
                                        userDetails
                                                .getUsername())));
    }

    @PostMapping("/edit-plan/cancel")
    @Operation(summary = "AI 일정 수정 취소",
            description = "미리보기로 생성된 수정안을 취소하고 기존 일정을 유지합니다.")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<Void>> cancelEditPlan
            (@RequestBody GetAiPlanRequest getAiPlanRequest,
             @AuthenticationPrincipal UserDetails userDetails){

        travelFacade.cancelEditPlan(
                getAiPlanRequest,
                userDetails.getUsername()
        );

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .successNoContent());
    }

}
