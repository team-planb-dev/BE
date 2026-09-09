package com.planb.integration.domain.chat;

import com.jayway.jsonpath.JsonPath;
import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.constant.WalkType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.PlanEditContext;
import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.dto.response.PlanEditScope;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.DateType;
import com.planb.domain.travel.entity.constant.RecommendationTag;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.entity.constant.TravelStyle;
import com.planb.domain.travel.entity.constant.TravelTheme;
import com.planb.domain.travel.repository.TravelRepository;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import com.planb.integration.domain.chat.helper.ChatIntegrationTestSupport;
import com.planb.integration.domain.chat.helper.StompTestClientHelper;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat 도메인 AI 일정 수정 STOMP 통합 테스트.
 * 최초 입장 인사, TALK 발행 시 AI 미리보기 응답 발행, CONFIRM/CANCEL 처리 검증
 * 실제 AI 호출은 TravelRecommendHandler Mock으로 대체(결정적 검증, 외부 API 미의존)
 */
public class ChatAiEditPlanIntegrationTest
        extends ChatIntegrationTestSupport {

    private static final String CHAT_SEND_PREFIX =
            "/pub/api/v1/chat/";

    private static final String ADD_WITH_RECOMMEND_URL =
            "/api/v1/travel/add-with-recommend";

    private static final String GET_AI_PLAN_URL =
            "/api/v1/travel/get-ai-travel-plan";

    private static final String FIND_OR_CREATE_TRAVEL_CHAT_ROOM_URL =
            "/api/v1/chat/room/travel/";

    private static final String AI_BOT_NICKNAME =
            "AI 비서";

    private static final String ORIGINAL_CAFE_NAME =
            "테스트카페";

    private static final String EDITED_CAFE_NAME =
            "스타벅스 하버타운점";

    private static final String ATTRACTION_CANDIDATE_ID =
            "kakao:haeundae-beach";

    private static final String SECOND_ATTRACTION_NAME =
            "동백섬";

    private static final String SECOND_ATTRACTION_CANDIDATE_ID =
            "kakao:dongbaek-island";

    private static final String ORIGINAL_CAFE_CANDIDATE_ID =
            "kakao:test-cafe";

    private static final String EDITED_CAFE_CANDIDATE_ID =
            "kakao:starbucks-harbor-town";

    @LocalServerPort
    private int port;

    @Autowired
    private TravelRepository travelRepository;

    @MockitoBean
    private TravelRecommendHandler travelRecommendHandler;

    @MockitoBean
    private KakaoMapServiceHandler kakaoMapServiceHandler;

    private StompTestClientHelper stompHelper;

    @BeforeEach
    void setUpStompHelper() {

        stompHelper =
                new StompTestClientHelper(
                        port
                );

        when(kakaoMapServiceHandler.getRoute(
                any(),
                any(),
                any(),
                any(),
                any(),
                any(),
                any()
        ))
                .thenReturn(Mono.just(new KakaoRouteResult(
                        null,
                        null,
                        null,
                        10
                )));

        when(travelRecommendHandler.classifyEditScope(any(PlanEditContext.class)))
                .thenReturn(new PlanEditScope(List.of()));
    }

    @Test
    @DisplayName("여행 채팅방 조회/생성 후 별도 멤버 추가 없이 최초 구독 시 AI 인사 메시지 2건이 발행됨")
    void firstSubscribeToTravelChatRoomPublishesGreetingMessages() throws Exception {

        // given
        stubCreatePlan();

        TestUser testUser =
                createAuthenticatedUser();

        Long travelId =
                createTravel(
                        testUser.accessToken(),
                        "AI 인사 여행-" + createUniqueValue()
                );

        Long roomId =
                findOrCreateTravelChatRoom(
                        testUser.accessToken(),
                        travelId
                );

        Long existingRoomId =
                findOrCreateTravelChatRoom(
                        testUser.accessToken(),
                        travelId
                );

        assertThat(existingRoomId)
                .isEqualTo(roomId);

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            // when
            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            SendChatMessageResponse firstGreeting =
                    stompHelper.awaitMessage(
                            messages,
                            message -> AI_BOT_NICKNAME.equals(message.senderNickname())
                    );

            SendChatMessageResponse secondGreeting =
                    stompHelper.awaitMessage(
                            messages,
                            message -> AI_BOT_NICKNAME.equals(message.senderNickname())
                    );

            // then
            assertThat(firstGreeting)
                    .isNotNull();

            assertThat(firstGreeting.type())
                    .isEqualTo(MessageType.TALK);

            assertThat(firstGreeting.message())
                    .isEqualTo(
                            "안녕하세요. " + testUser.nickname()
                                    + "님의 여행 일정을 계획해줄 " + AI_BOT_NICKNAME + "예요."
                    );

            assertThat(secondGreeting)
                    .isNotNull();

            assertThat(secondGreeting.message())
                    .isEqualTo("일정을 어떻게 수정하고 싶나요?");

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    @Test
    @DisplayName("TALK 발행 시 AI 수정 미리보기 응답이 봇 명의로 발행됨")
    void talkPublishesAiEditPreviewReply() throws Exception {

        // given
        stubCreatePlan();

        TestUser testUser =
                createAuthenticatedUser();

        Long travelId =
                createTravel(
                        testUser.accessToken(),
                        "AI 수정 미리보기 여행-" + createUniqueValue()
                );

        Long roomId =
                findOrCreateTravelChatRoom(
                        testUser.accessToken(),
                        travelId
                );

        stubEditPlan(
                List.of("1일차 카페를 " + EDITED_CAFE_NAME + "으로 변경"),
                true
        );

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            stompHelper.drainMessagesMatching(
                    messages,
                    message -> message.type() == MessageType.ENTER
                            || AI_BOT_NICKNAME.equals(message.senderNickname())
            );

            // when
            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            MessageType.TALK,
                            "카페를 스타벅스로 바꿔줘"
                    )
            );

            SendChatMessageResponse response =
                    stompHelper.awaitMessage(
                            messages,
                            message -> AI_BOT_NICKNAME.equals(message.senderNickname())
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.type())
                    .isEqualTo(MessageType.TALK);

            assertThat(response.roomId())
                    .isEqualTo(roomId);

            assertThat(response.senderNickname())
                    .isEqualTo(AI_BOT_NICKNAME);

            assertThat(response.message())
                    .isEqualTo("일정 수정을 완료했어요!");

            assertThat(response.editPreview())
                    .isNotNull();

            assertThat(response.editPreview().after().processable())
                    .isTrue();

            assertThat(response.editPreview().after().changes())
                    .contains("1일차 카페를 " + EDITED_CAFE_NAME + "으로 변경");

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    @Test
    @DisplayName("TALK 처리 불가능 요청이면 고정 거절 메시지 발행")
    void talkPublishesFallbackMessageWhenNotProcessable() throws Exception {

        // given
        stubCreatePlan();

        TestUser testUser =
                createAuthenticatedUser();

        Long travelId =
                createTravel(
                        testUser.accessToken(),
                        "AI 거절 여행-" + createUniqueValue()
                );

        Long roomId =
                findOrCreateTravelChatRoom(
                        testUser.accessToken(),
                        travelId
                );

        stubEditPlan(
                List.of(),
                false
        );

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            stompHelper.drainMessagesMatching(
                    messages,
                    message -> message.type() == MessageType.ENTER
                            || AI_BOT_NICKNAME.equals(message.senderNickname())
            );

            // when
            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            MessageType.TALK,
                            "일정 전체를 이해할 수 없는 요청으로 바꿔줘"
                    )
            );

            SendChatMessageResponse response =
                    stompHelper.awaitMessage(
                            messages,
                            message -> AI_BOT_NICKNAME.equals(message.senderNickname())
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.message())
                    .isEqualTo("해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.");

            assertThat(response.editPreview())
                    .isNull();

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    @Test
    @DisplayName("CONFIRM 발행 시 수정안이 실제 일정에 반영되고 고정 메시지 발행")
    void confirmEditPlanAppliesChangeAndPublishesFixedMessage() throws Exception {

        // given
        stubCreatePlan();

        TestUser testUser =
                createAuthenticatedUser();

        Long travelId =
                createTravel(
                        testUser.accessToken(),
                        "AI 확정 여행-" + createUniqueValue()
                );

        Long roomId =
                findOrCreateTravelChatRoom(
                        testUser.accessToken(),
                        travelId
                );

        stubEditPlan(
                List.of("1일차 카페를 " + EDITED_CAFE_NAME + "으로 변경"),
                true
        );

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            stompHelper.drainMessagesMatching(
                    messages,
                    message -> message.type() == MessageType.ENTER
                            || AI_BOT_NICKNAME.equals(message.senderNickname())
            );

            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            MessageType.TALK,
                            "카페를 스타벅스로 바꿔줘"
                    )
            );

            SendChatMessageResponse previewResponse =
                    stompHelper.awaitMessage(
                            messages,
                            message -> AI_BOT_NICKNAME.equals(message.senderNickname())
                    );

            assertThat(previewResponse)
                    .isNotNull();

            // when
            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            MessageType.CONFIRM,
                            null
                    )
            );

            SendChatMessageResponse response =
                    stompHelper.awaitMessageType(
                            messages,
                            MessageType.CONFIRM
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.senderNickname())
                    .isEqualTo(AI_BOT_NICKNAME);

            assertThat(response.message())
                    .isEqualTo("일정을 저장했어요!");

            mockMvc.perform(
                            get(GET_AI_PLAN_URL)
                                    .param(
                                            "travelId",
                                            String.valueOf(travelId)
                                    )
                                    .header(
                                            "Authorization",
                                            testUser.accessToken()
                                    )
                    )
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$.data.planDays[0].schedules[2].locationName")
                                    .value(EDITED_CAFE_NAME)
                    );

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    @Test
    @DisplayName("CANCEL 발행 시 기존 일정이 유지되고 고정 메시지 발행")
    void cancelEditPlanKeepsOriginalPlanAndPublishesFixedMessage() throws Exception {

        // given
        stubCreatePlan();

        TestUser testUser =
                createAuthenticatedUser();

        Long travelId =
                createTravel(
                        testUser.accessToken(),
                        "AI 취소 여행-" + createUniqueValue()
                );

        Long roomId =
                findOrCreateTravelChatRoom(
                        testUser.accessToken(),
                        travelId
                );

        stubEditPlan(
                List.of("1일차 카페를 " + EDITED_CAFE_NAME + "으로 변경"),
                true
        );

        WebSocketStompClient stompClient =
                stompHelper.createStompClient();

        StompSession session = null;

        try {
            session =
                    stompHelper.connect(
                            stompClient,
                            testUser.accessToken()
                    );

            BlockingQueue<SendChatMessageResponse> messages =
                    new LinkedBlockingQueue<>();

            stompHelper.subscribe(
                    session,
                    roomId,
                    messages
            );

            stompHelper.drainMessagesMatching(
                    messages,
                    message -> message.type() == MessageType.ENTER
                            || AI_BOT_NICKNAME.equals(message.senderNickname())
            );

            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            MessageType.TALK,
                            "카페를 스타벅스로 바꿔줘"
                    )
            );

            SendChatMessageResponse previewResponse =
                    stompHelper.awaitMessage(
                            messages,
                            message -> AI_BOT_NICKNAME.equals(message.senderNickname())
                    );

            assertThat(previewResponse)
                    .isNotNull();

            // when
            session.send(
                    CHAT_SEND_PREFIX
                            + roomId
                            + "/send",
                    new SendChatMessageRequest(
                            MessageType.CANCEL,
                            null
                    )
            );

            SendChatMessageResponse response =
                    stompHelper.awaitMessageType(
                            messages,
                            MessageType.CANCEL
                    );

            // then
            assertThat(response)
                    .isNotNull();

            assertThat(response.senderNickname())
                    .isEqualTo(AI_BOT_NICKNAME);

            assertThat(response.message())
                    .isEqualTo("기존 일정을 유지했어요!");

            mockMvc.perform(
                            get(GET_AI_PLAN_URL)
                                    .param(
                                            "travelId",
                                            String.valueOf(travelId)
                                    )
                                    .header(
                                            "Authorization",
                                            testUser.accessToken()
                                    )
                    )
                    .andExpect(status().isOk())
                    .andExpect(
                            jsonPath("$.data.planDays[0].schedules[2].locationName")
                                    .value(ORIGINAL_CAFE_NAME)
                    );

        } finally {
            stompHelper.disconnect(session);
            stompHelper.stop(stompClient);
        }
    }

    // travelId 기준 채팅방 조회/생성 후 roomId 반환
    private Long findOrCreateTravelChatRoom(
            String accessToken,
            Long travelId
    ) throws Exception {

        MvcResult result =
                mockMvc.perform(
                                get(FIND_OR_CREATE_TRAVEL_CHAT_ROOM_URL + travelId)
                                        .header(
                                                "Authorization",
                                                accessToken
                                        )
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andReturn();

        Number roomId =
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.data.chatRoomId"
                );

        return roomId.longValue();
    }

    // Travel 생성 후 travelId 반환 (AI 호출은 travelRecommendHandler Mock으로 대체)
    // 여행 생성에 필요한 동행인을 등록하고 healthId 반환
    private Long addCompanion(
            String accessToken,
            String travelerName
    ) throws Exception {

        AddCompanionRequest addCompanionRequest =
                new AddCompanionRequest(
                        travelerName,
                        true,
                        false,

                        new AddCompanionRequest.HealthInfo(
                                DiseaseType.DIABETES,
                                WalkType.MINIMAL
                        ),

                        new AddCompanionRequest.MealInfo(
                                true,
                                true,
                                LocalTime.of(8, 0),
                                true,
                                LocalTime.of(12, 0),
                                true,
                                LocalTime.of(18, 0)
                        ),

                        List.of(),
                        List.of()
                );

        mockMvc.perform(
                        post("/api/v1/health/add-traveler")
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                addCompanionRequest
                                        )
                                )
                )
                .andExpect(status().isOk());

        MvcResult result =
                mockMvc.perform(
                                get("/api/v1/health/get-companion-summary")
                                        .header(
                                                "Authorization",
                                                accessToken
                                        )
                        )
                        .andExpect(status().isOk())
                        .andReturn();

        List<Map<String, Object>> companions =
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.data.companionList[?(@.travelerName == '" + travelerName + "')]"
                );

        return ((Number) companions
                .get(0)
                .get("healthId"))
                .longValue();
    }

    private Long createTravel(
            String accessToken,
            String travelName
    ) throws Exception {

        Long healthId = addCompanion(accessToken, travelName + " 동행인");

        CreateTravelRequest createTravelRequest =
                new CreateTravelRequest(
                        travelName,
                        "부산",
                        "해운대구",
                        LocalDate.now().plusDays(7),
                        DateType.ONE_NIGHT_TWO_DAYS,
                        Transportation.TRANSIT,
                        "해운대",
                        List.of(
                                new CreateTravelRequest.PlannedPlaceDetail(
                                        "해운대해수욕장",
                                        "부산광역시 해운대구"
                                )
                        ),
                        TravelStyle.MATCH_MEAL_TIME,
                        TravelTheme.TASTE,
                        List.of("돼지국밥"),
                        List.of(
                                "밀면",
                                "복국",
                                "씨앗호떡",
                                "어묵",
                                "낙지볶음"
                        ),
                        List.of(healthId)
                );

        mockMvc.perform(
                        post(ADD_WITH_RECOMMEND_URL)
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                createTravelRequest
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(
                        jsonPath("$.success")
                                .value(true)
                );

        Travel travel =
                travelRepository.findAll().stream()
                        .filter(t -> t.getTravelName().equals(travelName))
                        .findFirst()
                        .orElseThrow();

        return travel.getId();
    }

    private void stubCreatePlan() {

        when(travelRecommendHandler.createPlanByAi(
                any(TravelPlanContext.class),
                any(PlaceCandidateContext.class)
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates =
                            invocation.getArgument(1);

                    recordPlanCandidates(
                            candidates,
                            ORIGINAL_CAFE_NAME,
                            ORIGINAL_CAFE_CANDIDATE_ID
                    );

                    return baseCreatePlanAiResponse();
                });
    }

    private void stubEditPlan(
            List<String> changes,
            boolean processable
    ) {

        when(travelRecommendHandler.editPlanByAi(
                any(PlanEditContext.class),
                any(PlaceCandidateContext.class)
        ))
                .thenAnswer(invocation -> {
                    PlaceCandidateContext candidates =
                            invocation.getArgument(1);

                    recordPlanCandidates(
                            candidates,
                            processable ? EDITED_CAFE_NAME : ORIGINAL_CAFE_NAME,
                            processable ? EDITED_CAFE_CANDIDATE_ID : ORIGINAL_CAFE_CANDIDATE_ID
                    );

                    return editPlanAiResponse(
                            changes,
                            processable
                    );
                });
    }

    private void recordPlanCandidates(
            PlaceCandidateContext candidates,
            String cafeName,
            String cafeCandidateId
    ) {

        candidates.record(new PlaceWithRouteResult(
                true,
                "해운대해수욕장",
                "부산광역시 해운대구",
                "129.1603",
                "35.1587",
                null,
                ATTRACTION_CANDIDATE_ID,
                "AT4",
                "여행 > 관광명소 > 해수욕장"
        ));

        candidates.record(new PlaceWithRouteResult(
                true,
                SECOND_ATTRACTION_NAME,
                "부산광역시 해운대구",
                "129.1520",
                "35.1531",
                null,
                SECOND_ATTRACTION_CANDIDATE_ID,
                "AT4",
                "여행 > 관광명소 > 섬"
        ));

        candidates.record(new PlaceWithRouteResult(
                true,
                cafeName,
                "부산광역시 해운대구",
                "129.1608",
                "35.1592",
                null,
                cafeCandidateId,
                "CE7",
                "음식점 > 카페"
        ));
    }

    // 초기 일정 AI 응답 고정값 (관광지 2곳·카페 1곳, 경로 조회 결과는 Kakao Handler Mock으로 고정)
    // 관광지 수는 TravelRecommendHandler의 기대 개수(걷기 수준 MINIMAL이면 2곳)를 만족해야 한다.
    private CreatePlanAiResponse baseCreatePlanAiResponse() {

        CreatePlanAiResponse.PlanScheduleDetail attraction =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 30),
                        "해운대해수욕장",
                        "부산광역시 해운대구",
                        null,
                        null,
                        null,
                        null,
                        90,
                        15,
                        Set.of(RecommendationTag.NATURAL_SCENERY),
                        null,
                        null,
                        ATTRACTION_CANDIDATE_ID
                );

        CreatePlanAiResponse.PlanScheduleDetail secondAttraction =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(11, 0),
                        LocalTime.of(12, 0),
                        SECOND_ATTRACTION_NAME,
                        "부산광역시 해운대구",
                        null,
                        null,
                        null,
                        null,
                        60,
                        15,
                        Set.of(RecommendationTag.NATURAL_SCENERY),
                        null,
                        null,
                        SECOND_ATTRACTION_CANDIDATE_ID
                );

        CreatePlanAiResponse.PlanScheduleDetail cafe =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.CAFE_REST,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        ORIGINAL_CAFE_NAME,
                        "부산광역시 해운대구",
                        null,
                        null,
                        null,
                        null,
                        60,
                        10,
                        Set.of(RecommendationTag.REST_POINT),
                        null,
                        null,
                        ORIGINAL_CAFE_CANDIDATE_ID
                );

        CreatePlanAiResponse.PlanDayDetail day1 =
                new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.now().plusDays(7),
                        List.of(attraction, secondAttraction, cafe)
                );

        return new CreatePlanAiResponse(
                List.of(day1)
        );
    }

    // 수정 요청 AI 응답 고정값 생성 (processable=true면 카페 이름만 변경)
    private EditPlanAiResponse editPlanAiResponse(
            List<String> changes,
            boolean processable
    ) {

        if (!processable) {
            return new EditPlanAiResponse(
                    null,
                    baseCreatePlanAiResponse().planDays(),
                    changes,
                    false
            );
        }

        CreatePlanAiResponse.PlanScheduleDetail attraction =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(9, 0),
                        LocalTime.of(10, 30),
                        "해운대해수욕장",
                        "부산광역시 해운대구",
                        null,
                        null,
                        null,
                        null,
                        90,
                        15,
                        Set.of(RecommendationTag.NATURAL_SCENERY),
                        null,
                        null,
                        ATTRACTION_CANDIDATE_ID
                );

        CreatePlanAiResponse.PlanScheduleDetail secondAttraction =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.ATTRACTION,
                        LocalTime.of(11, 0),
                        LocalTime.of(12, 0),
                        SECOND_ATTRACTION_NAME,
                        "부산광역시 해운대구",
                        null,
                        null,
                        null,
                        null,
                        60,
                        15,
                        Set.of(RecommendationTag.NATURAL_SCENERY),
                        null,
                        null,
                        SECOND_ATTRACTION_CANDIDATE_ID
                );

        CreatePlanAiResponse.PlanScheduleDetail editedCafe =
                new CreatePlanAiResponse.PlanScheduleDetail(
                        ScheduleType.ACTIVITY,
                        CourseType.CAFE_REST,
                        LocalTime.of(13, 0),
                        LocalTime.of(14, 0),
                        EDITED_CAFE_NAME,
                        "부산광역시 해운대구",
                        null,
                        null,
                        null,
                        null,
                        60,
                        10,
                        Set.of(RecommendationTag.REST_POINT),
                        null,
                        null,
                        EDITED_CAFE_CANDIDATE_ID
                );

        CreatePlanAiResponse.PlanDayDetail editedDay1 =
                new CreatePlanAiResponse.PlanDayDetail(
                        1,
                        LocalDate.now().plusDays(7),
                        List.of(attraction, secondAttraction, editedCafe)
                );

        return new EditPlanAiResponse(
                null,
                List.of(editedDay1),
                changes,
                true
        );
    }
}
