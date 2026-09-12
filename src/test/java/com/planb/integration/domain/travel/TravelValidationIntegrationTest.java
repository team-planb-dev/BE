package com.planb.integration.domain.travel;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanDayDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.dto.response.RebuildPlanDayResponse;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.dto.request.EditPlanRequest;
import com.planb.domain.travel.dto.request.GetAiPlanRequest;
import com.planb.domain.health.repository.HealthRepository;
import com.planb.domain.travel.dto.response.ShareTravelResponse;
import com.planb.domain.travel.entity.constant.*;
import com.planb.domain.travel.repository.*;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.domain.travel.service.PlanEditCacheService;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.mockito.ArgumentCaptor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 외부 AI·지도 응답만 고정하고 HTTP, 검증, DB, Redis 처리는 실제로 실행한다. */
class TravelValidationIntegrationTest extends TravelApiTestSupport {

    @MockitoBean
    private TravelRecommendHandler handler;

    @MockitoBean
    private KakaoMapServiceHandler kakao;

    @Autowired
    private TravelRepository travels;

    @Autowired
    private PlanRepository plans;

    @Autowired
    private PlanDayRepository days;

    @Autowired
    private PlanScheduleRepository schedules;

    @Autowired
    private RestaurantDetailRepository restaurants;

    @Autowired
    private PlanEditCacheService cache;

    @Autowired
    private TravelHealthRepository travelHealths;

    @Autowired
    private HealthRepository healths;

    private static final String THREE_DAY_DECIDED_LOCATION = "여행 출발지";

    private final LocalDate date = LocalDate.of(2026, 10, 10);
    private LoginResult session;
    private CreateTravelRequest request;
    private Long selectedHealthId;
    private Long unselectedHealthId;

    @BeforeEach
    void prepareTravel() throws Exception {

        org.mockito.Mockito
                .lenient()
                .when(kakao.getRoute(any(), any(), any(), any(), any(), any(), any()))
                .thenAnswer(invocation -> kakao.getRoute(
                        invocation.<String>getArgument(0),
                        invocation.<String>getArgument(1),
                        invocation.<com.planb.domain.travel.entity.constant.Transportation>getArgument(2)));


        String username = createUniqueUsername();
        createUser(username);
        session = login(username);
        selectedHealthId = addCompanion(session.accessToken());
        unselectedHealthId = addCompanion(session.accessToken(), "동행인2");

        request = new CreateTravelRequest(
                "검증 여행-" + UUID.randomUUID(),
                "부산",
                "해운대구",
                date,
                DateType.ONE_NIGHT_TWO_DAYS,
                Transportation.CAR,
                "부산역",
                List.of(),
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List.of(),
                List.of(),
                List.of(selectedHealthId));

        when(kakao.getRoute(anyString(), anyString(), any()))
                .thenReturn(Mono.just(new KakaoRouteResult(null, null, null, 10)));
        when(kakao.searchPlace(anyString()))
                .thenAnswer(invocation -> {
                    String name = invocation.getArgument(0);
                    String id = name.substring(name.lastIndexOf('-') + 1);
                    boolean meal = id.endsWith("2");
                    String category = meal ? "FD6" : id.endsWith("3") ? "CE7" : "AT4";

                    return Mono.just(new KakaoPlaceSearchResponse(
                            null,
                            List.of(new KakaoPlaceSearchResponse.Document(
                                    id,
                                    name,
                                    "관광",
                                    category,
                                    null,
                                    null,
                                    "부산 " + id,
                                    "부산 " + id,
                                    "129." + id,
                                    "35.1",
                                    null,
                                    null))));
                });
        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of()));
        stubCreate(false);
        stubEdit(3);
    }

    @Test
    @DisplayName("생성 결과의 필수 필드·복약·태그 검증과 비음식점 좌표 누락 저장·재조회")
    void createPersistsValidatedFieldsWithOptionalCoordinates() throws Exception {

        stubCreate(true);
        JsonNode created = success(postApi("/add-with-recommend", request));
        Long id = travelId();
        JsonNode stored = stored(id);

        TravelPlanAssertions.assertPlan(created, date, true);
        TravelPlanAssertions.assertPlan(stored, date, false);
        TravelPlanAssertions.assertMealMedication(
                stored,
                LocalTime.of(12, 0)
        );
        TravelPlanAssertions.assertSameDays(created.path("planDays"), stored.path("planDays"));
        assertThat(TravelPlanAssertions.codes(stored.path("tags")))
                .isEqualTo(TravelPlanAssertions.codes(created.path("tags")));
        assertThat(stored.path("planDays")
                .get(0)
                .path("schedules")
                .get(0)
                .path("longitude")
                .isNull())
                .isTrue();
        verify(handler, never())
                .reselectPlace(any(), any());
    }

    @Test
    @DisplayName("미등록 후보의 재선택 두 번 실패 시 실패 응답과 DB 롤백")
    void invalidCandidateRollsBackCreation() throws Exception {

        List<Long> before = counts();
        when(handler.createPlanByAi(any(), any()))
                .thenReturn(new CreatePlanAiResponse(List.of(day(1, 1, null, false))));

        assertError(postApi("/add-with-recommend", request), "PLAN.EXCEPTION.INVALID_AI_PLACE");
        assertThat(counts())
                .isEqualTo(before);
        verify(handler, times(2))
                .reselectPlace(any(), any());
    }

    @Test
    @DisplayName("비음식점과 달리 음식점 좌표 누락 거부와 생성 데이터 미저장")
    void restaurantCoordinatesRemainRequired() throws Exception {

        List<Long> before = counts();
        when(handler.createPlanByAi(any(), any()))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);
                    CreatePlanAiResponse result = fixture(1, candidates, false);
                    candidates.record(new PlaceWithRouteResult(
                            true,
                            "장소-12",
                            "부산 12",
                            null,
                            null,
                            10,
                            "kakao:12",
                            "FD6",
                            "음식점"));
                    return result;
                });

        assertError(postApi("/add-with-recommend", request), "PLAN.EXCEPTION.INVALID_AI_PLACE");
        assertThat(counts())
                .isEqualTo(before);
        verify(handler, times(2))
                .reselectPlace(any(), any());
    }

    @Test
    @DisplayName("수정 미리보기의 DB 보존과 확정 시 모든 필드 교체 및 캐시·이전 자식 정리")
    void previewConfirmAndQueryPreserveAllFields() throws Exception {

        success(postApi("/add-with-recommend", request));
        Long id = travelId();
        JsonNode original = stored(id);
        List<Long> before = counts();

        JsonNode preview = success(postApi("/edit-plan/preview", new EditPlanRequest(id, "장소를 변경해주세요.")));
        JsonNode after = preview.path("after");
        assertThat(after.path("processable")
                .asBoolean())
                .isTrue();
        TravelPlanAssertions.assertPlan(after, date, true);
        TravelPlanAssertions.assertMealMedication(
                after,
                LocalTime.of(12, 0)
        );
        TravelPlanAssertions.assertSameDays(original.path("planDays"), preview.path("before")
                .path("planDays"));
        TravelPlanAssertions.assertSameDays(original.path("planDays"), stored(id)
                .path("planDays"));
        assertThat(counts())
                .isEqualTo(before);
        assertThat(cache.findEditResult(id))
                .isPresent();

        JsonNode confirmed = success(postApi("/edit-plan/confirm", new GetAiPlanRequest(id)));
        JsonNode saved = stored(id);
        TravelPlanAssertions.assertPlan(saved, date, false);
        TravelPlanAssertions.assertMealMedication(
                saved,
                LocalTime.of(12, 0)
        );
        TravelPlanAssertions.assertSameDays(after.path("planDays"), confirmed.path("planDays"));
        TravelPlanAssertions.assertSameDays(after.path("planDays"), saved.path("planDays"));
        assertThat(counts())
                .isEqualTo(before);
        assertThat(cache.findEditResult(id))
                .isEmpty();

        // 같은 확정 요청이 다시 와도 일정을 다시 쓰지 않고 같은 응답을 돌려준다
        JsonNode reconfirmed = success(postApi("/edit-plan/confirm", new GetAiPlanRequest(id)));
        TravelPlanAssertions.assertSameDays(confirmed.path("planDays"), reconfirmed.path("planDays"));
        assertThat(counts())
                .isEqualTo(before);
        TravelPlanAssertions.assertSameDays(saved.path("planDays"), stored(id)
                .path("planDays"));
    }

    @Test
    @DisplayName("대기 중인 수정안 취소 시 원본 유지와 캐시 삭제 후 확정 실패")
    void cancelPendingPreviewKeepsOriginal() throws Exception {

        success(postApi("/add-with-recommend", request));
        Long id = travelId();
        JsonNode original = stored(id);
        List<Long> before = counts();

        success(postApi("/edit-plan/preview", new EditPlanRequest(id, "장소를 변경해주세요.")));
        assertThat(cache.findEditResult(id))
                .isPresent();
        success(postApi("/edit-plan/cancel", new GetAiPlanRequest(id)));

        assertThat(cache.findEditResult(id))
                .isEmpty();
        assertThat(counts())
                .isEqualTo(before);
        TravelPlanAssertions.assertSameDays(original.path("planDays"), stored(id)
                .path("planDays"));
        assertError(postApi("/edit-plan/confirm", new GetAiPlanRequest(id)), "PLAN.EXCEPTION.EDIT_RESULT_NOT_FOUND");
    }

    @Test
    @DisplayName("전체 재구성 두 번 실패 시 명시적 오류 반환과 원본·캐시 보존")
    void failedRebuildDoesNotPublishPreview() throws Exception {

        success(postApi("/add-with-recommend", request));
        Long id = travelId();
        JsonNode original = stored(id);
        List<Long> before = counts();
        stubEdit(1);
        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of(1)));
        when(handler.rebuildDay(any(), any(), eq(1), anyString(), any()))
                .thenReturn(new RebuildPlanDayResponse(false, "새로운 장소 검색 실패", List.of()));

        assertError(
                postApi("/edit-plan/preview", new EditPlanRequest(id, "1일차 전체 재구성, 2일차 유지")),
                "PLAN.EXCEPTION.EDIT_NOT_APPLIED");

        verify(handler, times(2))
                .rebuildDay(any(), any(), eq(1), anyString(), any());
        assertThat(counts())
                .isEqualTo(before);
        assertThat(cache.findEditResult(id))
                .isEmpty();
        TravelPlanAssertions.assertSameDays(original.path("planDays"), stored(id)
                .path("planDays"));
    }

    @Test
    @DisplayName("재구성 재시도 성공 시 대상 날짜만 변경과 좌표 없는 유지 날짜의 확정까지 보존")
    void rebuiltDayPreservesOtherDayThroughConfirmation() throws Exception {

        stubCreate(true);
        success(postApi("/add-with-recommend", request));
        Long id = travelId();
        JsonNode original = stored(id);
        stubEdit(1);
        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of(1)));
        when(handler.rebuildDay(any(), any(), eq(1), anyString(), any()))
                .thenAnswer(invocation -> new RebuildPlanDayResponse(
                        true,
                        "",
                        List.of(day(1, 3, invocation.getArgument(4), false))));

        JsonNode after = success(postApi(
                "/edit-plan/preview",
                new EditPlanRequest(id, "1일차 전체 재구성, 2일차 유지")))
                .path("after");

        TravelPlanAssertions.assertPlan(after, date, true);
        TravelPlanAssertions.assertMealMedication(
                after,
                LocalTime.of(12, 0)
        );
        TravelPlanAssertions.assertSameDays(
                objectMapper.valueToTree(List.of(original.path("planDays")
                        .get(1))),
                objectMapper.valueToTree(List.of(after.path("planDays")
                        .get(1))));
        assertThat(after.path("planDays")
                .get(0)
                .path("schedules")
                .get(0)
                .path("locationName")
                .asText())
                .isEqualTo("장소-31");
        verify(handler)
                .rebuildDay(any(), any(), eq(1), anyString(), any());

        success(postApi("/edit-plan/confirm", new GetAiPlanRequest(id)));
        TravelPlanAssertions.assertSameDays(after.path("planDays"), stored(id)
                .path("planDays"));
        assertThat(cache.findEditResult(id))
                .isEmpty();
    }

    @Test
    @DisplayName("잘못 분류한 장소만 재선택과 정상 슬롯 유지 결과 저장")
    void reselectsOnlyInvalidPlace() throws Exception {

        when(handler.createPlanByAi(any(), any()))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);
                    CreatePlanAiResponse result = fixture(1, candidates, false);
                    candidates.record(new PlaceWithRouteResult(
                            true,
                            "음식점-11",
                            "부산 11",
                            "129.11",
                            "35.1",
                            10,
                            "kakao:11",
                            "FD6",
                            "음식점"));
                    return result;
                });
        when(handler.reselectPlace(any(), any()))
                .thenAnswer(invocation -> slot(41, CourseType.ATTRACTION, 9, invocation.getArgument(1), false));

        JsonNode created = success(postApi("/add-with-recommend", request));
        JsonNode saved = stored(travelId());
        TravelPlanAssertions.assertPlan(created, date, true);
        TravelPlanAssertions.assertSameDays(created.path("planDays"), saved.path("planDays"));
        assertThat(created
                .path("planDays")
                .get(0)
                .path("schedules")
                .get(0)
                .path("candidateId")
                .asText())
                .isEqualTo("kakao:41");
        assertThat(created
                .path("planDays")
                .get(1)
                .path("schedules")
                .get(0)
                .path("candidateId")
                .asText())
                .isEqualTo("kakao:21");
        verify(handler)
                .reselectPlace(any(), any());
    }

    @Test
    @DisplayName("수정 장소 검증 불가 시 검증된 원본 복원과 원본 DB 유지")
    void invalidEditDoesNotReplaceStoredPlan() throws Exception {

        success(postApi("/add-with-recommend", request));
        Long id = travelId();
        JsonNode original = stored(id);
        List<Long> before = counts();
        when(handler.editPlanByAi(any(), any()))
                .thenReturn(new EditPlanAiResponse(
                        request.travelName(),
                        fixture(3, null, false)
                                .planDays(),
                        List.of("장소 변경"),
                        true));
        when(kakao.searchPlace(anyString()))
                .thenReturn(Mono.empty());

        // 확정 저장된 일정은 외부 검색 없이 그대로 보존되므로 미리보기 자체는 실패하지 않는다.
        // AI가 제시한 장소를 검증하지 못하면 슬롯마다 재선택을 시도하고, 그래도 안 되면 검증된 원본으로 되돌린다.
        JsonNode after = success(postApi("/edit-plan/preview", new EditPlanRequest(id, "장소를 변경해주세요.")))
                .path("after");

        verify(handler, atLeastOnce())
                .reselectPlace(any(), any());

        TravelPlanAssertions.assertSameDays(original.path("planDays"), after.path("planDays"));

        // 미리보기는 확정이 아니므로 저장된 일정은 그대로여야 한다
        assertThat(counts())
                .isEqualTo(before);
        TravelPlanAssertions.assertSameDays(original.path("planDays"), stored(id).path("planDays"));
    }

    @Test
    @DisplayName("선택한 구성원만 AI 컨텍스트 포함과 미선택 구성원 데이터 유지")
    void createUsesOnlySelectedCompanions() throws Exception {

        stubCreate(true);
        success(postApi("/add-with-recommend", request));

        ArgumentCaptor<TravelPlanContext> captor =
                ArgumentCaptor.forClass(TravelPlanContext.class);
        verify(handler)
                .createPlanByAi(captor.capture(), any());

        assertThat(captor.getValue()
                .healthContexts())
                .extracting(context -> context.travelerName())
                .containsExactly("동행인1");

        assertThat(travelHealths.findAllByTravelId(travelId()))
                .hasSize(1);
        assertThat(healths.findById(unselectedHealthId))
                .isPresent();
    }

    @Test
    @DisplayName("여러 구성원 선택 시 모두 관계 저장과 중복 요청 한 번만 저장")
    void createStoresEachSelectedCompanionOnce() throws Exception {

        stubCreate(true);
        request = withHealthIds(List.of(
                selectedHealthId,
                unselectedHealthId,
                selectedHealthId));

        success(postApi("/add-with-recommend", request));

        ArgumentCaptor<TravelPlanContext> captor =
                ArgumentCaptor.forClass(TravelPlanContext.class);
        verify(handler)
                .createPlanByAi(captor.capture(), any());

        assertThat(captor.getValue()
                .healthContexts())
                .extracting(context -> context.travelerName())
                .containsExactlyInAnyOrder("동행인1", "동행인2");
        assertThat(travelHealths.findAllByTravelId(travelId()))
                .hasSize(2);
    }

    @Test
    @DisplayName("구성원 미선택 생성 요청 거부와 여행 미저장")
    void createRejectsEmptyCompanions() throws Exception {

        List<Long> before = counts();
        request = withHealthIds(List.of());

        assertError(postApi("/add-with-recommend", request), "TRAVEL.EXCEPTION.COMPANION_REQUIRED");
        assertThat(counts())
                .isEqualTo(before);
        verify(handler, never())
                .createPlanByAi(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 구성원 선택 생성 요청 거부")
    void createRejectsUnknownCompanion() throws Exception {

        List<Long> before = counts();
        request = withHealthIds(List.of(selectedHealthId + 100_000L));

        assertError(postApi("/add-with-recommend", request), "TRAVEL.EXCEPTION.COMPANION_NOT_OWNED");
        assertThat(counts())
                .isEqualTo(before);
    }

    @Test
    @DisplayName("다른 사용자의 구성원 선택 생성 요청 거부")
    void createRejectsOtherUsersCompanion() throws Exception {

        String strangerUsername = createUniqueUsername();
        createUser(strangerUsername);
        LoginResult strangerSession = login(strangerUsername);
        Long strangerHealthId = addCompanion(strangerSession.accessToken(), "남의 동행인");

        List<Long> before = counts();
        request = withHealthIds(List.of(strangerHealthId));

        assertError(postApi("/add-with-recommend", request), "TRAVEL.EXCEPTION.COMPANION_NOT_OWNED");
        assertThat(counts())
                .isEqualTo(before);
    }

    @Test
    @DisplayName("수정 미리보기의 여행 생성 당시 선택 구성원 그대로 사용")
    void previewReusesSelectedCompanions() throws Exception {

        stubCreate(true);
        success(postApi("/add-with-recommend", request));
        Long id = travelId();

        success(postApi("/edit-plan/preview", new EditPlanRequest(id, "장소를 변경해주세요.")));

        ArgumentCaptor<PlanEditContext> captor =
                ArgumentCaptor.forClass(PlanEditContext.class);
        verify(handler)
                .editPlanByAi(captor.capture(), any());

        assertThat(captor.getValue()
                .healthContexts())
                .extracting(context -> context.travelerName())
                .containsExactly("동행인1");
        assertThat(captor.getValue()
                .createTravelRequest()
                .healthIds())
                .containsExactly(selectedHealthId);
    }

    @Test
    @DisplayName("생성 직후 저장 전 상태와 저장 확정에 필요한 travelId 반환")
    void createReturnsUnsavedTravelId() throws Exception {

        stubCreate(true);
        JsonNode created = success(postApi("/add-with-recommend", request));

        assertThat(created.path("saved")
                .asBoolean())
                .isFalse();
        assertThat(created.path("travelId")
                .asLong())
                .isEqualTo(travelId());
    }

    @Test
    @DisplayName("소유자의 저장 확정 성공과 중복 요청 결과 동일")
    void saveIsIdempotentForOwner() throws Exception {

        stubCreate(true);
        success(postApi("/add-with-recommend", request));
        Long id = travelId();
        List<Long> before = counts();

        JsonNode saved = success(postApi("/save", new GetAiPlanRequest(id)));
        assertThat(saved.path("saved")
                .asBoolean())
                .isTrue();
        assertThat(saved.path("travelId")
                .asLong())
                .isEqualTo(id);

        JsonNode again = success(postApi("/save", new GetAiPlanRequest(id)));
        assertThat(again.path("saved")
                .asBoolean())
                .isTrue();
        assertThat(counts())
                .isEqualTo(before);
        assertThat(travels.findById(id)
                .orElseThrow()
                .isSaved())
                .isTrue();
    }

    @Test
    @DisplayName("다른 사용자와 존재하지 않는 여행의 저장 확정 요청 거부")
    void saveRejectsForeignAndUnknownTravel() throws Exception {

        stubCreate(true);
        success(postApi("/add-with-recommend", request));
        Long id = travelId();

        String strangerUsername = createUniqueUsername();
        createUser(strangerUsername);
        LoginResult strangerSession = login(strangerUsername);

        mockMvc.perform(post("/api/v1/travel/save")
                        .header("Authorization", strangerSession.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GetAiPlanRequest(id))))
                .andExpect(status()
                        .isForbidden());

        mockMvc.perform(post("/api/v1/travel/save")
                        .header("Authorization", session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GetAiPlanRequest(id + 100_000L))))
                .andExpect(status()
                        .isForbidden());

        assertThat(travels.findById(id)
                .orElseThrow()
                .isSaved())
                .isFalse();
    }

    @Test
    @DisplayName("저장 전 공유 발급 거부와 저장 후 동일 토큰 재사용")
    void shareRequiresSavedTravel() throws Exception {

        stubCreate(true);
        success(postApi("/add-with-recommend", request));
        Long id = travelId();

        assertError(postApi("/share/issue", new GetAiPlanRequest(id)), "TRAVEL.EXCEPTION.TRAVEL_NOT_SAVED");
        assertThat(travels.findById(id)
                .orElseThrow()
                .getShareToken())
                .isNull();

        success(postApi("/save", new GetAiPlanRequest(id)));

        String first = success(postApi("/share/issue", new GetAiPlanRequest(id)))
                .path("shareToken")
                .asText();
        String second = success(postApi("/share/issue", new GetAiPlanRequest(id)))
                .path("shareToken")
                .asText();

        assertThat(first)
                .isNotBlank()
                .isEqualTo(second);
    }

    private CreateTravelRequest withHealthIds(List<Long> healthIds) {

        return new CreateTravelRequest(
                request.travelName(),
                request.locationDo(),
                request.locationSigungu(),
                request.startDate(),
                request.dateType(),
                request.transportation(),
                request.decidedLocation(),
                request.plannedPlaces(),
                request.travelStyle(),
                request.travelTheme(),
                request.localFoods(),
                request.recommendFoods(),
                healthIds);
    }

    @Test
    @DisplayName("2일차만 재구성 시 첫 장소 이동시간의 전날 마지막 장소 기준 계산")
    void rebuiltSecondDayAnchorsTravelMinutesToPreviousDayLastPlace() throws Exception {

        // given
        request = threeDayRequest();

        when(handler.createPlanByAi(any(), any()))
                .thenAnswer(invocation -> threeDayFixture(1, 2, 3, invocation.getArgument(1)));

        success(postApi("/add-with-recommend", request));

        Long id = travelId();

        // 출발지에서 재는 경로와 전날 마지막 장소에서 재는 경로를 구분
        when(kakao.getRoute(anyString(), anyString(), any()))
                .thenAnswer(invocation -> Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        THREE_DAY_DECIDED_LOCATION.equals(invocation.<String>getArgument(0))
                                ? 999
                                : 30)));

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of(2)));

        // 2일차만 다른 장소로 교체, 1·3일차는 그대로
        when(handler.editPlanByAi(any(), any()))
                .thenAnswer(invocation -> new EditPlanAiResponse(
                        request.travelName(),
                        threeDayFixture(1, 4, 3, invocation.getArgument(1))
                                .planDays(),
                        List.of("2일차 장소 변경"),
                        true));

        // when
        JsonNode after = success(postApi(
                "/edit-plan/preview",
                new EditPlanRequest(id, "2일차만 다시 짜주세요.")))
                .path("after");

        // then
        assertThat(after
                .path("planDays")
                .get(1)
                .path("schedules")
                .get(0)
                .path("travelMinutes")
                .asInt())
                .isEqualTo(30);
    }

    @Test
    @DisplayName("재구성 날짜 다음 보존 날짜 첫 장소의 이동시간 갱신")
    void preservedDayAfterRebuildRecalculatesFirstTravelMinutes() throws Exception {

        // given
        request = threeDayRequest();

        when(handler.createPlanByAi(any(), any()))
                .thenAnswer(invocation -> threeDayFixture(1, 2, 3, invocation.getArgument(1)));

        success(postApi("/add-with-recommend", request));

        Long id = travelId();

        // 재구성으로 2일차 마지막 장소가 바뀌면 3일차 첫 이동시간도 달라져야 한다
        when(kakao.getRoute(anyString(), anyString(), any()))
                .thenAnswer(invocation -> Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        "장소-45".equals(invocation.<String>getArgument(0))
                                ? 77
                                : 30)));

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of(2)));

        when(handler.editPlanByAi(any(), any()))
                .thenAnswer(invocation -> new EditPlanAiResponse(
                        request.travelName(),
                        threeDayFixture(1, 4, 3, invocation.getArgument(1))
                                .planDays(),
                        List.of("2일차 장소 변경"),
                        true));

        // when
        JsonNode after = success(postApi(
                "/edit-plan/preview",
                new EditPlanRequest(id, "2일차만 다시 짜주세요.")))
                .path("after");

        // then
        assertThat(after
                .path("planDays")
                .get(2)
                .path("schedules")
                .get(0)
                .path("travelMinutes")
                .asInt())
                .isEqualTo(77);
    }

    @Test
    @DisplayName("재구성 날짜 첫 장소가 그대로일 때도 이동시간의 전날 마지막 장소 기준 계산")
    void unchangedFirstPlaceOfRebuiltDayStillAnchorsToPreviousDay() throws Exception {

        // given
        request = threeDayRequest();

        when(handler.createPlanByAi(any(), any()))
                .thenAnswer(invocation -> threeDayFixture(1, 2, 3, invocation.getArgument(1)));

        success(postApi("/add-with-recommend", request));

        Long id = travelId();

        when(kakao.getRoute(anyString(), anyString(), any()))
                .thenAnswer(invocation -> Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        THREE_DAY_DECIDED_LOCATION.equals(invocation.<String>getArgument(0))
                                ? 999
                                : 30)));

        when(handler.classifyEditScope(any()))
                .thenReturn(new PlanEditScope(List.of(2)));

        // 2일차 첫 장소는 그대로 두고 나머지 슬롯만 교체
        when(handler.editPlanByAi(any(), any()))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates = invocation.getArgument(1);

                    return new EditPlanAiResponse(
                            request.travelName(),
                            List.of(
                                    day(1, 1, candidates, false),
                                    dayKeepingFirstPlace(2, 4, candidates),
                                    day(3, 3, candidates, false)),
                            List.of("2일차 장소 변경"),
                            true);
                });

        // when
        JsonNode after = success(postApi(
                "/edit-plan/preview",
                new EditPlanRequest(id, "2일차 나머지 장소만 바꿔주세요.")))
                .path("after");

        // then
        assertThat(after
                .path("planDays")
                .get(1)
                .path("schedules")
                .get(0)
                .path("travelMinutes")
                .asInt())
                .isEqualTo(30);
    }

    // 첫 슬롯은 기존 저장 장소와 동일하고 이동시간이 비어 있는 날짜
    private CreatePlanAiResponse.PlanDayDetail dayKeepingFirstPlace(
            int number,
            int source,
            PlaceCandidateContext candidates
    ) {

        return new CreatePlanAiResponse.PlanDayDetail(
                number,
                date.plusDays(number - 1),
                List.of(
                        unchangedFirstSlot(number * 10 + 1, candidates),
                        slot(source * 10 + 2, CourseType.RESTAURANT, 12, candidates, false),
                        slot(source * 10 + 3, CourseType.CAFE_REST, 14, candidates, false),
                        slot(source * 10 + 4, CourseType.ATTRACTION, 16, candidates, false),
                        slot(source * 10 + 5, CourseType.ATTRACTION, 18, candidates, false)));
    }

    // 검색 원본과 동일한 장소 정보를 그대로 담아 장소 변경으로 판정되지 않는 슬롯
    private CreatePlanAiResponse.PlanScheduleDetail unchangedFirstSlot(
            int id,
            PlaceCandidateContext candidates
    ) {

        if (candidates != null) {
            candidates.record(new PlaceWithRouteResult(
                    true,
                    "장소-" + id,
                    "부산 " + id,
                    "129." + id,
                    "35.1",
                    10,
                    "kakao:" + id,
                    "AT4",
                    "관광"));
        }

        return new CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.ACTIVITY,
                CourseType.ATTRACTION,
                LocalTime.of(9, 0),
                LocalTime.of(10, 0),
                "장소-" + id,
                "부산 " + id,
                "129." + id,
                "35.1",
                null,
                null,
                60,
                null,
                Set.of(),
                null,
                null,
                "kakao:" + id);
    }

    private CreateTravelRequest threeDayRequest() {

        return new CreateTravelRequest(
                "경계 검증 여행-" + UUID.randomUUID(),
                "부산",
                "해운대구",
                date,
                DateType.TWO_NIGHTS_THREE_DAYS,
                Transportation.CAR,
                THREE_DAY_DECIDED_LOCATION,
                List.of(),
                TravelStyle.MATCH_MEAL_TIME,
                TravelTheme.TASTE,
                List.of(),
                List.of(),
                List.of(selectedHealthId));
    }

    private CreatePlanAiResponse threeDayFixture(
            int firstSource,
            int secondSource,
            int thirdSource,
            PlaceCandidateContext candidates
    ) {

        return new CreatePlanAiResponse(List.of(
                day(1, firstSource, candidates, false),
                day(2, secondSource, candidates, false),
                day(3, thirdSource, candidates, false)));
    }

    private void stubCreate(boolean optionalCoordinates) {

        when(handler.createPlanByAi(any(), any()))
                .thenAnswer(invocation -> fixture(1, invocation.getArgument(1), optionalCoordinates));
    }

    private void stubEdit(int firstDay) {

        when(handler.editPlanByAi(any(), any()))
                .thenAnswer(invocation -> new EditPlanAiResponse(
                        request.travelName(),
                        fixture(firstDay, invocation.getArgument(1), false)
                                .planDays(),
                        List.of("장소 변경"),
                        true));
    }

    private CreatePlanAiResponse fixture(
            int firstDay,
            PlaceCandidateContext candidates,
            boolean optionalCoordinates
    ) {

        return new CreatePlanAiResponse(List.of(
                day(1, firstDay, candidates, optionalCoordinates),
                day(2, 2, candidates, optionalCoordinates)));
    }

    private PlanDayDetail day(
            int number,
            int source,
            PlaceCandidateContext candidates,
            boolean optionalCoordinates
    ) {

        return new PlanDayDetail(
                number,
                date.plusDays(number - 1),
                List.of(
                        slot(
                                source * 10 + 1,
                                CourseType.ATTRACTION,
                                9,
                                candidates,
                                optionalCoordinates
                        ),
                        slot(
                                source * 10 + 2,
                                CourseType.RESTAURANT,
                                12,
                                candidates,
                                false
                        ),
                        slot(
                                source * 10 + 3,
                                CourseType.CAFE_REST,
                                14,
                                candidates,
                                optionalCoordinates
                        ),
                        slot(
                                source * 10 + 4,
                                CourseType.ATTRACTION,
                                16,
                                candidates,
                                optionalCoordinates
                        ),
                        slot(
                                source * 10 + 5,
                                CourseType.ATTRACTION,
                                18,
                                candidates,
                                optionalCoordinates
                        )
                ));
    }

    private PlanScheduleDetail slot(
            int id,
            CourseType type,
            int hour,
            PlaceCandidateContext candidates,
            boolean optionalCoordinates
    ) {

        boolean meal = type == CourseType.RESTAURANT;
        String category = meal ? "FD6" : type == CourseType.CAFE_REST ? "CE7" : "AT4";
        String x = optionalCoordinates ? null : "129." + id;
        String y = optionalCoordinates ? null : "35.1";

        if (candidates != null) {
            candidates.record(new PlaceWithRouteResult(
                    true,
                    "장소-" + id,
                    "부산 " + id,
                    x,
                    y,
                    10,
                    "kakao:" + id,
                    category,
                    "관광"));
        }

        RestaurantDetail restaurant = meal ? new RestaurantDetail(
                "메뉴-" + id,
                30.0,
                100.0,
                5.0,
                "09:00~20:00",
                "AI 주소",
                "0",
                "0",
                null) : null;

        return new PlanScheduleDetail(
                meal ? ScheduleType.LUNCH : ScheduleType.ACTIVITY,
                type,
                LocalTime.of(hour, 0),
                LocalTime.of(hour + 1, 0),
                "AI 장소명",
                "AI 주소",
                "0",
                "0",
                null,
                null,
                60,
                10,
                Set.of(),
                null,
                restaurant,
                "kakao:" + id);
    }

    private Long travelId() {

        return travels
                .findAll()
                .stream()
                .filter(travel -> request.travelName()
                        .equals(travel.getTravelName()))
                .findFirst()
                .orElseThrow()
                .getId();
    }

    private List<Long> counts() {

        return List.of(travels.count(), plans.count(), days.count(), schedules.count(), restaurants.count());
    }

    private JsonNode postApi(String path, Object payload) throws Exception {

        MvcResult result = mockMvc
                .perform(post("/api/v1/travel" + path)
                        .header("Authorization", session.accessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status()
                        .isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse()
                .getContentAsString());
    }

    private JsonNode stored(Long id) throws Exception {

        MvcResult result = mockMvc
                .perform(get("/api/v1/travel/get-ai-travel-plan")
                        .param("travelId", id.toString())
                        .header("Authorization", session.accessToken()))
                .andExpect(status()
                        .isOk())
                .andReturn();

        return success(objectMapper.readTree(result.getResponse()
                .getContentAsString()));
    }

    private JsonNode success(JsonNode result) {

        assertThat(result.path("success")
                .asBoolean())
                .withFailMessage(result.toString())
                .isTrue();
        return result.path("data");
    }

    private void assertError(JsonNode result, String code) {

        assertThat(result.path("success")
                .asBoolean())
                .isFalse();
        assertThat(result.path("error")
                .path("errorCode")
                .asText())
                .isEqualTo(code);
        assertThat(result.path("data")
                .isNull())
                .isTrue();
    }
}
