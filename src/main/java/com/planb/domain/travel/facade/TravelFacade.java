package com.planb.domain.travel.facade;


import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.service.FoodInfoService;
import com.planb.domain.health.service.HealthService;
import com.planb.domain.health.service.MedicationInfoService;
import com.planb.domain.travel.dto.request.*;

import com.planb.domain.travel.dto.response.CreatePlanResponse;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.domain.travel.dto.response.GetAiPlanResponse;
import com.planb.domain.travel.dto.response.MakeRecommendFoodResponse;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.dto.response.SaveTravelResponse;
import com.planb.domain.travel.dto.response.ShareTravelResponse;
import com.planb.domain.travel.dto.response.TravelListItemResponse;
import com.planb.domain.travel.dto.response.TravelListResponse;
import com.planb.domain.travel.entity.*;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.domain.travel.service.*;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.TravelExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.query.health.service.HealthQueryService;
import com.planb.query.health.service.MedicationInfoQueryService;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.dto.response.TravelListItemQueryResponse;
import com.planb.query.travel.service.*;
import com.planb.query.user.service.UserQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 여행 조건, 건강 정보, AI 일정의 생성과 조회 및 편집 흐름을 조합하는 Facade.
 *
 * 사용자 소유권과 건강 정보를 여행 일정에 연결하고 AI 결과를 영속 도메인으로
 * 변환해 Controller가 세부 저장 순서와 외부 호출 정책을 알지 않도록 한다.
 */
@Component
@RequiredArgsConstructor
public class TravelFacade {

    /*
    User Domain Service
     */
    private final UserQueryService userQueryService;

    /*
    Health Domain Service
     */
    private final HealthService healthService;
    private final FoodInfoService foodInfoService;
    private final MedicationInfoService medicationInfoService;

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
    private final PlanDayService planDayService;
    private final PlanScheduleService planScheduleService;
    private final RestaurantDetailService restaurantDetailService;
    private final PlanEditCacheService planEditCacheService;

    /*
    Travel Query Service
     */
    private final TravelQueryService travelQueryService;
    private final PlanQueryService planQueryService;
    private final PlanScheduleQueryService planScheduleQueryService;
    private final PlanDayQueryService planDayQueryService;
    private final RestaurantDetailQueryService restaurantDetailQueryService;


    /**
     * 여행 지역에 맞는 향토 음식 추천 결과를 제공한다.
     *
     * @param request 음식 추천에 사용할 지역 정보
     * @return 지역 음식 추천 결과
     */
    public MakeRecommendFoodResponse showRecommendFoods
    (MakeRecommendFoodsRequest request){
        return travelService.makeRecommendFoodResponse(request);
    }

    /**
     * 사용자가 입력한 검색어로 일정에 추가할 장소 후보를 조회한다.
     *
     * @param searchPlannedPlaceRequest 장소 검색어
     * @return 비동기로 조회한 장소 후보
     */
    public Mono<SearchPlannedPlaceResponse> searchPlannedPlaceByText
    (SearchPlannedPlaceRequest searchPlannedPlaceRequest){

        return plannedPlaceService.searchPlannedPlace(searchPlannedPlaceRequest);
    }


    /**
     * 사용자 여행 조건과 건강 정보를 바탕으로 AI 일정을 생성하고 저장한다.
     *
     * 1. 여행 조건과 계획 장소를 저장한다.
     * 2. 동행인의 건강 정보를 AI 입력 컨텍스트로 구성한다.
     * 3. 검증된 AI 일정을 날짜, 장소, 음식점 정보로 구체화한다.
     *
     * @param createTravelRequest 여행 조건과 계획 장소
     * @param username 여행을 생성하는 사용자의 username
     * @return 생성된 여행 일정과 추천 태그
     */
    @Transactional
    public CreatePlanResponse makeTravelOptionsAndRecommend(
            CreateTravelRequest createTravelRequest,
            String username
    ) {

        // Redis 캐시에서 userId 조회
        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        // 이번 여행에 참여할 구성원을 검증 (소유자 확인 및 중복 제거)
        List<Health> selectedHealths =
                findSelectedHealths(createTravelRequest.healthIds(), userId);

        // Travel 객체 생성하기
        Travel travel = travelService
                .createTravel(createTravelRequest, userId);

        // Travel 객체 생성 후 , 저장
        travelService.saveTravel(travel);

        // 선택한 구성원을 Travel과 연결해 저장
        travelService.saveTravelHealths(travel, selectedHealths);

        // PlannedPlan 객체 생성 후 , 저장하기
        plannedPlaceService
                .savePlannedPlaceList(plannedPlaceService
                        .makePlannedPlace(CreatePlannedPlaceRequest
                                .from(
                                        travel,
                                        createTravelRequest)));

        // Travel과 연결된 Plan객체 생성
        Plan plan = planService.createPlan(
                new CreatePlanRequest(
                        travel,
                        createTravelRequest
                                .travelName()
                )
        );

        // Plan 객체 저장
        planService.savePlan(plan);

        // 선택한 구성원 기반 Health 컨텍스트 생성하기
        List<TravelHealthContext> healthContexts =
                buildHealthContexts(selectedHealths);

        // Travel 정보와 Health 컨텍스트 기반으로 AI 일정 생성하기
        CreatePlanAiResponse createPlanAiResponse =
                planService.makePlanByAi(
                        new TravelPlanContext(
                                createTravelRequest,
                                healthContexts
                        )
                );

        // AI 응답의 PlanSchedule 태그를 모두 모아 Plan에 반영
        Set<RecommendationTag> aggregatedTags =
                planService.aggregateTags(
                        createPlanAiResponse.planDays()
                );
        plan.updateTags(aggregatedTags);
        planService.savePlan(plan);

        // AI 응답 기반으로 PlanDay, PlanSchedule, RestaurantDetail 생성 후 저장
        materializePlanDays(plan, createPlanAiResponse.planDays());

        // 생성된 AI 여행일정 응답 반환
        return CreatePlanResponse.of(
                travel,
                aggregatedTags,
                createPlanAiResponse
        );
    }

    /**
     * 여행 소유권을 검증한 뒤 저장된 일정과 건강 관련 정보를 함께 조회한다.
     *
     * @param getAiPlanRequest 조회할 여행 ID
     * @param username 조회를 요청한 사용자의 username
     * @return 여행 조건, 건강 정보, 날짜별 일정의 전체 조회 결과
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     */
    @Transactional(readOnly = true)
    public GetAiPlanResponse getAiPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId =
                getAiPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        List<Long> healthIds = travelService
                .findHealthListByTravelId(travelId)
                .stream()
                .map(Health::getId)
                .toList();

        return findPlanDetail(travelId,
                healthQueryService
                        .getHealthSummaryListByHealthIds(healthIds),
                medicationInfoQueryService
                        .getMedicationTimesByHealthIds(healthIds));
    }


    /**
     * AI로 생성한 일정을 저장 확정 상태로 바꾼다.
     *
     * 이미 저장된 여행에 다시 요청해도 상태와 데이터가 바뀌지 않는다.
     *
     * @param getAiPlanRequest 저장할 여행 ID
     * @param username 저장을 요청한 사용자의 username
     * @return 저장 확정 결과
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     */
    @Transactional
    public SaveTravelResponse saveTravel(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = getAiPlanRequest.travelId();

        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        Travel travel = travelService
                .findTravelById(travelId);

        travel.markSaved();

        return new SaveTravelResponse(travelId,
                travel.isSaved());
    }


    /**
     * 여행 소유자가 읽기 전용 공유 링크 토큰을 발급받는다.
     *
     * 이미 발급된 토큰이 있으면 같은 토큰을 그대로 돌려주어 기존 링크가 계속 열리도록 한다.
     *
     * @param getAiPlanRequest 공유할 여행 ID
     * @param username 공유를 요청한 사용자의 username
     * @return 공유 링크 토큰
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     */
    @Transactional
    public ShareTravelResponse createShareLink(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = getAiPlanRequest.travelId();

        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        Travel travel = travelService
                .findTravelById(travelId);

        // 저장 확정 전 일정은 공유할 수 없다.
        if (!travel.isSaved()) {
            throw new BaseException(TravelExceptionEnum.TRAVEL_NOT_SAVED);
        }

        travel.issueShareToken(UUID
                .randomUUID()
                .toString());

        return new ShareTravelResponse(travelId,
                travel.getShareToken());
    }


    /**
     * 공유 링크로 여행 일정을 조회한다.
     *
     * 링크만 알면 누구나 열 수 있으므로 동행인의 질환과 복약 시간은 포함하지 않는다.
     *
     * @param shareToken 공유 링크 토큰
     * @return 건강 정보를 제외한 일정 조회 결과
     * @throws BaseException 토큰에 해당하는 여행이 없는 경우
     */
    @Transactional(readOnly = true)
    public GetAiPlanResponse getSharedPlan(String shareToken) {

        return findPlanDetail(travelQueryService
                        .getTravelIdByShareToken(shareToken),
                List.of(),
                List.of());
    }


    /**
     * 여행 일정과 세부 정보를 조회해 응답으로 조립한다.
     *
     * @param travelId 조회할 여행 ID
     * @param healthSummaries 함께 노출할 동행인 건강 요약, 공유 조회에서는 빈 목록
     * @param medicationTimes 함께 노출할 복약 시간, 공유 조회에서는 빈 목록
     * @return 여행 조건과 날짜별 일정의 전체 조회 결과
     */
    private GetAiPlanResponse findPlanDetail(
            Long travelId,
            List<HealthSummaryQueryResponse> healthSummaries,
            List<LocalTime> medicationTimes
    ) {

        TravelConditionQueryResponse travelCondition =
                travelQueryService
                        .getTravelConditionQueryResponse(
                                travelId
                        );

        PlanQueryResponse plan =
                planQueryService
                        .getPlanByTravelId(
                                travelId
                        );

        List<PlanDayQueryResponse> planDays =
                planDayQueryService
                        .getPlanDaysByPlanId(
                                plan.planId()
                        );

        List<PlanSchedule> planSchedules =
                planScheduleQueryService
                        .getPlanSchedulesByPlanDayIds(
                                planDays.stream()
                                        .map(
                                                PlanDayQueryResponse::planDayId
                                        )
                                        .toList()
                        );

        List<RestaurantDetailQueryResponse> restaurantDetails =
                restaurantDetailQueryService
                        .getRestaurantDetailsByPlanScheduleIds(
                                planSchedules.stream()
                                        .map(
                                                PlanSchedule::getId
                                        )
                                        .toList()
                        );

        return GetAiPlanResponse.from(
                plan,
                travelCondition,
                healthSummaries,
                medicationTimes,
                planDays,
                planSchedules,
                restaurantDetails
        );
    }

    /**
     * 자연어 수정 요청으로 일정 미리보기를 생성하고 확정 전까지 캐시에 보관한다.
     *
     * 실제 일정은 변경하지 않아 사용자가 수정안을 확인한 뒤 확정하거나 취소할 수 있다.
     *
     * @param editPlanRequest 여행 ID와 자연어 수정 요청
     * @param username 수정을 요청한 사용자의 username
     * @return 수정 전 일정과 AI 수정안
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     */
    @Transactional(readOnly = true)
    public EditPlanPreviewResponse makeEditPlanPreview(
            EditPlanRequest editPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = editPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        // 현재 확정되어 있는 일정 조회 (getAiPlan 재사용)
        GetAiPlanResponse currentPlan =
                getAiPlan(
                        new GetAiPlanRequest(travelId),
                        username
                );

        // Travel 엔티티 기반으로 원본 CreateTravelRequest 재구성
        Travel travel = travelService.findTravelById(travelId);

        List<CreateTravelRequest.PlannedPlaceDetail> plannedPlaceDetails =
                plannedPlaceService.findAllByTravel(travel)
                        .stream()
                        .map(plannedPlace ->
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        plannedPlace.getLocationName(),
                                        plannedPlace.getLocation()
                                )
                        )
                        .toList();

        // 여행 생성 당시 선택한 구성원 복원
        List<Health> selectedHealths =
                travelService.findHealthListByTravelId(travelId);

        CreateTravelRequest createTravelRequest =
                CreateTravelRequest.from(
                        travel,
                        plannedPlaceDetails,
                        selectedHealths
                                .stream()
                                .map(Health::getId)
                                .toList()
                );

        // 선택한 구성원 기반 Health 컨텍스트 생성하기
        List<TravelHealthContext> healthContexts =
                buildHealthContexts(selectedHealths);

        // AI로 수정안 생성
        EditPlanAiResponse editPlanAiResponse =
                planService.makeEditPlanByAi(
                        new PlanEditContext(
                                createTravelRequest,
                                healthContexts,
                                currentPlan,
                                editPlanRequest.editRequest()
                        )
                );

        // Redis에 수정안 저장 (5단계 저장 확정에서 사용)
        planEditCacheService.saveEditResult(
                travelId,
                editPlanAiResponse
        );

        // Before/After 반환
        return new EditPlanPreviewResponse(
                currentPlan,
                editPlanAiResponse
        );
    }

    /**
     * 캐시에 보관된 수정안을 기존 일정 대신 최종 일정으로 확정한다.
     *
     * 참조 무결성을 위해 기존 세부 일정을 자식부터 제거하고 수정안으로 재생성한 뒤
     * 사용이 끝난 캐시를 삭제한다.
     *
     * @param getAiPlanRequest 확정할 여행 ID
     * @param username 확정을 요청한 사용자의 username
     * @return 확정된 여행 일정과 추천 태그
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     * @throws BaseException 캐시에 확정할 수정안이 없는 경우
     */
    @Transactional
    public CreatePlanResponse confirmEditPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = getAiPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        // Redis에서 수정안을 조회와 동시에 소비 (동시 확정 요청 중 하나만 통과)
        Optional<EditPlanAiResponse> consumed =
                planEditCacheService.consumeEditResult(travelId);

        // 수정안이 없다면 이미 확정된 요청의 재시도일 수 있다.
        // 확정 표식이 남아 있으면 일정을 다시 쓰지 않고 같은 응답만 돌려준다.
        if (consumed.isEmpty()) {
            EditPlanAiResponse confirmed =
                    planEditCacheService.findConfirmedResult(travelId)
                            .orElseThrow(() ->
                                    new BaseException(
                                            PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND
                                    )
                            );

            return confirmedResponse(travelId, confirmed);
        }

        EditPlanAiResponse editPlanAiResponse = consumed.get();

        PlanQueryResponse planQueryResponse =
                planQueryService.getPlanByTravelId(travelId);

        Plan plan = planService.findPlanById(planQueryResponse.planId());

        // 기존 PlanDay/PlanSchedule/RestaurantDetail 삭제 (자식 -> 부모 순서)
        List<PlanDay> existingPlanDays =
                planDayService.findAllByPlan(plan);

        List<PlanSchedule> existingPlanSchedules =
                planScheduleService.findAllByPlanDayIn(existingPlanDays);

        restaurantDetailService.deleteAllByPlanScheduleIn(existingPlanSchedules);
        planScheduleService.deleteAllByPlanDayIn(existingPlanDays);
        planDayService.deleteAllByPlan(plan);

        // 수정안의 PlanSchedule 태그를 모두 모아 Plan에 반영
        Set<RecommendationTag> aggregatedTags =
                planService.aggregateTags(
                        editPlanAiResponse.planDays()
                );
        plan.updateTags(aggregatedTags);
        planService.savePlan(plan);

        // 수정안 기반으로 PlanDay, PlanSchedule, RestaurantDetail 재생성 후 저장
        materializePlanDays(plan, editPlanAiResponse.planDays());

        // 확정 표식 기록. 커밋된 뒤에만 남겨야 롤백된 확정이 성공으로 보이지 않는다.
        markConfirmedAfterCommit(travelId, editPlanAiResponse);

        return confirmedResponse(travelId, editPlanAiResponse);
    }

    // 확정된 수정안을 CreatePlanResponse 형태로 되돌린다.
    // 태그는 수정안에서 결정적으로 다시 계산되므로 저장 경로와 재시도 경로가 같은 값을 낸다.
    private CreatePlanResponse confirmedResponse(
            Long travelId,
            EditPlanAiResponse editPlanAiResponse
    ) {

        return CreatePlanResponse.of(
                travelService
                        .findTravelById(travelId),
                planService.aggregateTags(editPlanAiResponse.planDays()),
                new CreatePlanAiResponse(editPlanAiResponse.planDays())
        );
    }

    // 트랜잭션이 커밋된 뒤에 확정 표식을 남긴다.
    // 트랜잭션 밖에서 호출되면(단위 테스트 등) 바로 기록한다.
    private void markConfirmedAfterCommit(
            Long travelId,
            EditPlanAiResponse editPlanAiResponse
    ) {

        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            planEditCacheService.markConfirmed(travelId, editPlanAiResponse);

            return;
        }

        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {

                    @Override
                    public void afterCommit() {

                        planEditCacheService.markConfirmed(travelId, editPlanAiResponse);
                    }
                }
        );
    }

    /**
     * 캐시에 보관된 수정안을 삭제하고 현재 확정 일정을 유지한다.
     *
     * @param getAiPlanRequest 취소할 여행 ID
     * @param username 취소를 요청한 사용자의 username
     * @throws ForbiddenException 사용자가 해당 여행의 소유자가 아닌 경우
     */
    @Transactional
    public void cancelEditPlan(
            GetAiPlanRequest getAiPlanRequest,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        Long travelId = getAiPlanRequest.travelId();

        // travelId가 이 userId 소유인지 검증
        if (!travelQueryService.existsByIdAndUserId(travelId, userId)) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행에 대한 접근 권한이 없습니다."}
            );
        }

        planEditCacheService.deleteEditResult(travelId);
    }

    /**
     * 요청한 구성원이 모두 로그인 사용자 소유인지 확인하고 중복을 제거해 반환한다.
     *
     * @param healthIds 이번 여행에 참여할 구성원 id
     * @param userId 여행을 생성하는 사용자 ID
     * @return 검증된 구성원 목록
     * @throws BaseException 구성원을 선택하지 않았거나 사용자 소유가 아닌 구성원이 포함된 경우
     */
    private List<Health> findSelectedHealths(
            List<Long> healthIds,
            Long userId
    ) {

        if (healthIds == null || healthIds.isEmpty()) {
            throw new BaseException(TravelExceptionEnum.COMPANION_REQUIRED);
        }

        return healthIds
                .stream()
                .distinct()
                .map(healthId -> {

                    if (!healthQueryService.checkHealthWithUser(healthId, userId)) {
                        throw new BaseException(TravelExceptionEnum.COMPANION_NOT_OWNED);
                    }

                    return healthService.getHealthById(healthId);
                })
                .toList();
    }


    /**
     * 생성과 편집이 동일한 건강 정보 계약을 사용하도록 AI 컨텍스트를 구성한다.
     *
     * @param healths 이번 여행에 선택된 구성원
     * @return 동행인별 건강, 음식 제한, 복약 정보 컨텍스트
     */
    private List<TravelHealthContext> buildHealthContexts(List<Health> healths) {

        return healths
                .stream()
                .map(health ->
                        TravelHealthContext.from(
                                health,
                                foodInfoService.getFoodInfoList(
                                        health.getId()
                                ),
                                medicationInfoService.findAllByHealthId(
                                        health.getId()
                                )
                        )
                )
                .toList();
    }

    /**
     * 생성과 편집 결과가 동일한 영속화 순서를 따르도록 날짜별 AI 일정을 구체화한다.
     *
     * @param plan 일정을 소유할 여행 계획
     * @param planDays 구체화할 날짜별 AI 일정
     */
    private void materializePlanDays(
            Plan plan,
            List<CreatePlanAiResponse.PlanDayDetail> planDays
    ) {

        planDays.forEach(planDayDetail -> {

            // AI 응답 기반으로 PlanDay 객체 생성
            PlanDay planDay =
                    planDayService.createPlanDay(
                            new CreatePlanDayRequest(
                                    plan,
                                    planDayDetail.dayNumber(),
                                    planDayDetail.date()
                            )
                    );

            // PlanDay 객체 저장
            planDayService.savePlanDay(planDay);

            // PlanDay와 연결된 PlanSchedule 객체 리스트 생성
            List<PlanSchedule> planSchedules =
                    planScheduleService.makePlanScheduleList(
                            planDay,
                            planDayDetail.schedules()
                    );

            // PlanSchedule 객체 리스트 일괄 저장
            planScheduleService.savePlanScheduleAll(
                    planSchedules
            );

            // RestaurantDetail 객체 리스트 생성
            List<RestaurantDetail> restaurantDetails =
                    restaurantDetailService.makeRestaurantDetailList(
                            planSchedules,
                            planDayDetail.schedules()
                    );

            // RestaurantDetail 객체 리스트 일괄 저장
            restaurantDetailService.saveRestaurantDetailAll(
                    restaurantDetails
            );
        });
    }


    /**
     * 사용자의 여행 목록을 탭 기준으로 조회한다.
     *
     * 진행 상태는 저장하지 않고 조회 시점의 오늘 날짜로 계산한다.
     *
     * @param filter   목록 탭 구분 (UPCOMING, PAST)
     * @param username 조회하는 사용자의 username
     * @return 여행 목록
     */
    @Transactional(readOnly = true)
    public TravelListResponse getTravelList(
            TravelListFilter filter,
            String username
    ) {

        Long userId = userQueryService
                .findByUsernameInCache(username)
                .userId();

        LocalDate today = LocalDate.now();

        List<TravelListItemQueryResponse> travels = travelQueryService
                .getTravelList(userId, filter, today);

        Map<Long, String> thumbnailUrls = travelQueryService
                .getThumbnailUrls(travels
                        .stream()
                        .map(TravelListItemQueryResponse::travelId)
                        .toList());

        return new TravelListResponse(travels
                .stream()
                .map(travel -> TravelListItemResponse
                        .of(travel,
                                today,
                                thumbnailUrls.get(travel.travelId())))
                .toList());
    }
}
