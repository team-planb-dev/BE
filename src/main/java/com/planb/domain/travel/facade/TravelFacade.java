package com.planb.domain.travel.facade;


import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.travel.dto.request.*;

import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.dto.response.SaveTravelResponse;
import com.planb.domain.travel.dto.response.ShareTravelResponse;
import com.planb.domain.travel.dto.response.TravelListResponse;
import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.domain.travel.service.*;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.travel.service.*;
import com.planb.query.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Mono;

import java.time.LocalTime;
import java.util.List;

/**
 * 여행 일정의 생성과 조회 및 편집 흐름을 조합
 */
@Component
@RequiredArgsConstructor
public class TravelFacade {

    /*
    User Domain Service
     */
    private final UserQueryService userQueryService;

    /*
    Health Query Service
     */
    private final HealthQueryService healthQueryService;
    private final MedicationInfoQueryService medicationInfoQueryService;


    /*
    Travel Domain Service
     */
    private final TravelService travelService;
    private final PlannedPlaceService plannedPlaceService;
    private final PlanService planService;
    private final PlanEditCacheService planEditCacheService;

    /*
    Travel Query Service
     */
    private final TravelQueryService travelQueryService;
    private final PlanQueryService planQueryService;


    /**
     * 여행 지역의 향토 음식 추천을 조회
     */
    public MakeRecommendFoodResponse showRecommendFoods
    (MakeRecommendFoodsRequest request) {
        return travelService.makeRecommendFoodResponse(request); // 향토 음식 추천 조회
    }

    /**
     * 일정에 추가할 장소 후보를 검색
     */
    public Mono<SearchPlannedPlaceResponse> searchPlannedPlaceByText
    (SearchPlannedPlaceRequest searchPlannedPlaceRequest) {

        return plannedPlaceService.searchPlannedPlace(searchPlannedPlaceRequest); // 장소 후보 검색
    }


    /**
     * 여행 조건에 맞는 AI 일정을 생성
     */
    public CreatePlanResponse makeTravelOptionsAndRecommend(
            CreateTravelRequest createTravelRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        List<TravelHealthContext> healthContexts = travelService.loadHealthContexts(
                createTravelRequest,
                userId
        ); // 동행인 건강 정보 조회

        CreatePlanAiResponse aiResponse = planService.makePlanByAi(
                createTravelRequest,
                healthContexts
        ); // AI 일정 생성

        return travelService.saveGeneratedPlan(
                createTravelRequest,
                userId,
                aiResponse
        ); // 여행 일정 저장
    }

    /**
     * 여행 일정과 건강 정보를 조회
     */
    @Transactional(readOnly = true)
    public GetAiPlanResponse getAiPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        Long travelId = getAiPlanRequest.travelId();

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        List<Long> healthIds = travelService.findHealthIdsByTravelId(travelId); // 동행인 ID 조회

        List<HealthSummaryQueryResponse> healthSummaries =
                healthQueryService.getHealthSummaryListByHealthIds(healthIds); // 동행인 요약 조회

        List<LocalTime> medicationTimes =
                medicationInfoQueryService.getMedicationTimesByHealthIds(healthIds); // 복약 시간 조회

        return planQueryService.getPlanDetailResponse(
                travelId,
                healthSummaries,
                medicationTimes
        ); // 일정 상세 조회
    }


    /**
     * AI 여행 일정을 저장 확정
     */
    @Transactional
    public SaveTravelResponse saveTravel(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        Long travelId = getAiPlanRequest.travelId();

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        return travelService.markSaved(travelId); // 여행 저장 확정
    }


    /**
     * 여행 일정의 공유 링크를 발급
     */
    @Transactional
    public ShareTravelResponse createShareLink(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        Long travelId = getAiPlanRequest.travelId();

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        return travelService.issueShareLink(travelId); // 공유 링크 발급
    }


    /**
     * 공유 링크의 여행 일정을 조회
     */
    @Transactional(readOnly = true)
    public GetAiPlanResponse getSharedPlan(String shareToken) {

        Long travelId = travelQueryService.getTravelIdByShareToken(shareToken); // 공유 여행 조회

        return planQueryService.getSharedPlanDetailResponse(travelId); // 공유 일정 조회
    }

    /**
     * 자연어 요청으로 일정 수정 미리보기를 생성
     */
    public EditPlanPreviewResponse makeEditPlanPreview(
            EditPlanRequest editPlanRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        Long travelId = editPlanRequest.travelId();

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        PlanEditContext editContext = travelService.prepareEditContext(
                travelId,
                editPlanRequest.editRequest()
        ); // 편집 정보 조회

        EditPlanAiResponse editResponse = planService.makeEditPlanByAi(editContext); // AI 수정안 생성

        planEditCacheService.saveEditResult(travelId, editResponse); // 수정안 캐시 저장

        return planService.createEditPreviewResponse(
                editContext,
                editResponse
        ); // 수정 미리보기 반환
    }

    /**
     * 일정 수정안을 최종 일정으로 확정
     */
    @Transactional
    public CreatePlanResponse confirmEditPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        Long travelId = getAiPlanRequest.travelId();

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        return travelService.confirmEditPlan(travelId); // 수정안 확정
    }

    /**
     * 일정 수정안을 취소
     */
    @Transactional
    public void cancelEditPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        Long travelId = getAiPlanRequest.travelId();

        travelQueryService.validateOwner(travelId, userId); // 여행 소유권 검증

        planEditCacheService.deleteEditResult(travelId); // 수정안 취소
    }









    /**
     * 사용자의 여행 목록을 조회
     */
    @Transactional(readOnly = true)
    public TravelListResponse getTravelList(
            TravelListFilter filter,
            String username
    ) {

        Long userId = userQueryService.findUserIdInCache(username); // 사용자 ID 조회

        return travelQueryService.getTravelListResponse(userId, filter); // 여행 목록 조회
    }
}
