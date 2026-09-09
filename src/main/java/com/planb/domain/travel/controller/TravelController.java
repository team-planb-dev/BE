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
import io.swagger.v3.oas.annotations.Parameter;
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
@Tag(name = "여행 API", description = "여행 조건, AI 일정, 저장, 공유와 일정 수정을 관리합니다.")
@RequestMapping("/api/v1/travel")
@RequiredArgsConstructor
public class TravelController {

    private final TravelFacade travelFacade;

    @GetMapping("/recommend-local-food")
    @Operation(
            summary = "여행지 음식 추천",
            description = """
                    여행 지역의 시·도와 시·군·구를 기준으로 지역 음식을 추천합니다.
                    음식 정보 조회 과정에서 외부 API를 호출하므로 응답 시간이 길어지거나
                    외부 서비스 오류가 `BASE.EXCEPTION.EXCEPTION_ISSUED`로 반환될 수 있습니다.
                    """
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<MakeRecommendFoodResponse>> recommendLocalFood
            (@Parameter(description = "지역 규격에 맞는 시·도 이름", example = "경상북도")
             @RequestParam String locationDo,
             @Parameter(description = "여행할 시·군·구 이름", example = "경주시")
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
    @Operation(
            summary = "계획 장소 검색",
            description = """
                    사용자가 미리 계획한 숙박 또는 관광 장소를 검색합니다.
                    장소 검색은 외부 API를 비동기로 호출하므로 응답 시간이 길어질 수 있습니다.
                    검색어와 일치하는 장소 이름과 주소 목록을 반환합니다.
                    """
    )
    @SecurityRequirement(name = "JWT")
    public Mono<ResponseEntity<ApiResult<SearchPlannedPlaceResponse>>> searchPlannedPlace
            (@Parameter(description = "검색할 장소 이름 또는 키워드", example = "불국사")
             @RequestParam String searchText){

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
    @Operation(
            summary = "여행 조건 등록 및 AI 일정 생성",
            description = """
                    여행 조건과 사용자가 선택한 동행인의 건강 정보를 기준으로 AI 일정을 생성합니다.
                    `healthIds`에는 로그인한 사용자가 소유한 동행인을 한 명 이상 전달해 주세요.
                    생성 과정에서 관광지, 음식점과 이동 경로 외부 API를 조회할 수 있어 응답 시간이 길어질 수 있습니다.
                    AI 결과는 장소, 일정 시간과 복약 규칙에 대한 서버 검증과 보정을 거친 후 반환됩니다.
                    생성 직후 일정은 저장 확정 전인 `saved=false` 상태입니다.

                    구성원 누락은 `TRAVEL.EXCEPTION.COMPANION_REQUIRED`, 소유권 불일치는
                    `TRAVEL.EXCEPTION.COMPANION_NOT_OWNED`, 장소 검증 실패는
                    `PLAN.EXCEPTION.INVALID_AI_PLACE`로 구분됩니다.
                    AI 또는 외부 API 처리 실패는 `BASE.EXCEPTION.EXCEPTION_ISSUED`로 반환될 수 있습니다.
                    """
    )
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
    @Operation(
            summary = "여행 목록 조회",
            description = """
                    로그인한 사용자의 여행 목록을 조회합니다.
                    `status=UPCOMING`이면 종료일이 오늘 이후인 여행을, `PAST`이면 이미 끝난 여행을 반환합니다.
                    각 여행의 상태는 조회 시점에 따라 `UPCOMING`, `ONGOING`, `COMPLETED`로 계산됩니다.
                    """
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<TravelListResponse>> getTravelList
            (@Parameter(description = "조회할 여행 목록 구분", example = "UPCOMING")
             @RequestParam(defaultValue = "UPCOMING") TravelListFilter status,
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
    @Operation(
            summary = "여행 일정 전체 조회",
            description = """
                    로그인한 사용자가 소유한 여행 ID를 전달해 주세요.
                    여행 조건, 선택한 동행인의 건강 요약, 복약 시간, 추천 태그와 날짜별 전체 일정을 반환합니다.
                    여행 소유자가 아니면 HTTP 403으로 응답합니다.
                    """
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<GetAiPlanResponse>> getAiPlanDetailAll
            (@Parameter(description = "조회할 여행 ID", example = "1")
             @RequestParam Long travelId,
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
    @Operation(
            summary = "여행 일정 저장 확정",
            description = """
                    AI가 생성한 일정의 여행 ID를 전달해 저장 확정 상태로 변경합니다.
                    저장 확정 후에만 공유 링크를 발급할 수 있습니다.
                    이미 저장된 여행에 다시 요청해도 같은 상태를 반환합니다.
                    여행 소유자가 아니면 HTTP 403으로 응답합니다.
                    """
    )
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
    @Operation(
            summary = "여행 일정 공유 링크 발급",
            description = """
                    여행 일정을 읽기 전용으로 공유할 토큰을 발급합니다.
                    `POST /api/v1/travel/save`로 저장을 확정한 여행만 공유할 수 있습니다.
                    저장 전에는 `TRAVEL.EXCEPTION.TRAVEL_NOT_SAVED`를 반환합니다.
                    이미 발급된 여행은 같은 토큰을 반환하며 여행 소유자가 아니면 HTTP 403으로 응답합니다.
                    """
    )
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
    @Operation(
            summary = "공유된 여행 일정 조회",
            description = """
                    공유 링크 토큰으로 저장된 여행 일정을 조회합니다.
                    로그인은 필요하지 않으며 조회 결과는 읽기 전용입니다.
                    동행인의 질환과 복약 시간은 개인정보 보호를 위해 포함하지 않습니다.
                    """
    )
    public ResponseEntity<ApiResult<GetAiPlanResponse>> getSharedPlan
            (@Parameter(description = "공유 링크에 포함된 토큰")
             @PathVariable String shareToken){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(travelFacade
                                .getSharedPlan(shareToken)));
    }

    @PostMapping("/edit-plan/preview")
    @Operation(
            summary = "AI 일정 수정 미리보기 생성",
            description = """
                    여행 ID와 자연어 수정 요청을 기준으로 AI 수정안을 생성합니다.
                    기존 여행 조건, 선택한 동행인의 건강 정보와 현재 일정을 활용하며,
                    장소와 이동 경로 외부 API를 조회할 수 있어 응답 시간이 길어질 수 있습니다.
                    수정안은 서버 검증을 통과한 뒤 기존 일정과 함께 반환되며 아직 실제 일정에는 반영되지 않습니다.
                    AI가 요청을 처리할 수 없다고 판단하면 `processable=false`인 정상 미리보기를 반환합니다.
                    수정 범위 또는 재구성 검증 실패는 `PLAN.EXCEPTION.EDIT_NOT_APPLIED`,
                    장소 검증 실패는 `PLAN.EXCEPTION.INVALID_AI_PLACE`로 구분됩니다.
                    AI 또는 외부 API 처리 실패는 `BASE.EXCEPTION.EXCEPTION_ISSUED`로 반환될 수 있습니다.
                    """
    )
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
    @Operation(
            summary = "AI 일정 수정 확정",
            description = """
                    미리보기로 생성해 임시 보관한 수정안을 실제 일정에 반영합니다.
                    미리보기를 먼저 생성해야 하며, 결과가 없거나 만료되었으면
                    `PLAN.EXCEPTION.EDIT_RESULT_NOT_FOUND`를 반환합니다.
                    """
    )
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
    @Operation(
            summary = "AI 일정 수정 취소",
            description = "미리보기로 임시 보관한 수정안을 삭제하고 현재 확정 일정을 유지합니다. 여행 소유자가 아니면 HTTP 403으로 응답합니다."
    )
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
