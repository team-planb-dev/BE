package com.planb.integration.domain.chat;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.JacksonJsonMessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.AddChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomRequest;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.global.security.dto.request.LoginRequest;
import com.planb.integration.IntegrationTest;
import com.planb.integration.domain.user.dto.LoginResult;

import tools.jackson.databind.ObjectMapper;

import java.lang.reflect.Type;

import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Chat API 통합 테스트
 * 채팅방 생성, 멤버 등록 및 삭제, 채팅방 삭제,
 * WebSocket 연결과 STOMP 메시지 송수신을 검증한다.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT
)
public class ChatIntegrationTest extends IntegrationTest {

    private static final String CREATE_USER_URL =
            "/api/v1/user/create";

    private static final String LOGIN_URL =
            "/login";

    private static final String USER_ME_URL =
            "/api/v1/user/me";

    private static final String CREATE_CHAT_ROOM_URL =
            "/api/v1/chat/room/create";

    private static final String DELETE_CHAT_ROOM_URL =
            "/api/v1/chat/room/delete";

    private static final String ADD_CHAT_MEMBER_URL =
            "/api/v1/chat/member/add";

    private static final String DELETE_CHAT_MEMBER_URL =
            "/api/v1/chat/member/delete";

    private static final String CHAT_SUBSCRIPTION_PREFIX =
            "/sub/api/v1/chat/";

    private static final String CHAT_SEND_PREFIX =
            "/pub/api/v1/chat/";

    private static final String PASSWORD =
            "password123!";

    private static final String NICKNAME_PREFIX =
            "chatNickname-";

    @Autowired
    private ObjectMapper objectMapper;

    @LocalServerPort
    private int port;

    @Test
    @DisplayName("로그인한 사용자가 채팅방 생성 성공")
    void createChatRoomSuccess() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        String chatRoomName =
                "chat-room-" + createUniqueValue();

        CreateChatRoomRequest request =
                new CreateChatRoomRequest(chatRoomName);

        // when & then
        mockMvc.perform(
                        post(CREATE_CHAT_ROOM_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.chatRoomId").isNumber())
                .andExpect(
                        jsonPath("$.data.chatRoomName")
                                .value(chatRoomName)
                )
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(
                        jsonPath("$.data.message")
                                .value("채팅방이 생성되었습니다.")
                )
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("Access Token 없이 채팅방 생성 시 인증 실패")
    void createChatRoomUnauthorized() throws Exception {

        // given
        CreateChatRoomRequest request =
                new CreateChatRoomRequest(
                        "unauthorized-room"
                );

        // when & then
        mockMvc.perform(
                        post(CREATE_CHAT_ROOM_URL)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("채팅방 생성 후 사용자를 채팅방 멤버로 추가 성공")
    void addChatRoomMemberSuccess() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                "member-add-room-" + createUniqueValue()
        );

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        testUser.userId()
                );

        // when & then
        mockMvc.perform(
                        post(ADD_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.username")
                                .value(testUser.username())
                )
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        testUser.username()
                                                + "님이 채팅방에 입장하셨습니다."
                                )
                )
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("이미 참여한 사용자를 같은 채팅방에 추가하면 중복 예외")
    void addDuplicateChatRoomMemberFail() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                "duplicate-room-" + createUniqueValue()
        );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        testUser.userId()
                );

        // when & then
        mockMvc.perform(
                        post(ADD_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                /*
                 * 현재 ApiExceptionHandler의 BaseException 처리 메소드에
                 * @ResponseStatus가 없어서 HTTP 상태는 200으로 반환된다.
                 */
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(
                        jsonPath("$.error.errorCode")
                                .value(
                                        "WEBSOCKET.EXCEPTION.USER_ROOM_DUPLICATED"
                                )
                )
                .andExpect(
                        jsonPath("$.error.message")
                                .value(
                                        "해당 유저는 이미 해당 방과 등록 되어있습니다."
                                )
                );
    }

    @Test
    @DisplayName("채팅방에 참여한 사용자 삭제 성공")
    void deleteChatRoomMemberSuccess() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                "member-delete-room-" + createUniqueValue()
        );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        DeleteChatRoomMemberRequest request =
                new DeleteChatRoomMemberRequest(
                        roomId,
                        testUser.userId()
                );

        // when & then
        mockMvc.perform(
                        delete(DELETE_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                )
                .andExpect(
                        jsonPath("$.data.username")
                                .value(testUser.username())
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        testUser.username()
                                                + "님이 퇴장하셨습니다."
                                )
                )
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("채팅방 삭제 시 채팅방과 멤버 관계 삭제 성공")
    void deleteChatRoomSuccess() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        String chatRoomName =
                "delete-room-" + createUniqueValue();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                chatRoomName
        );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        DeleteChatRoomRequest request =
                new DeleteChatRoomRequest(roomId);

        // when & then
        mockMvc.perform(
                        delete(DELETE_CHAT_ROOM_URL)
                                .header(
                                        "Authorization",
                                        testUser.accessToken()
                                )
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                )
                .andExpect(
                        jsonPath("$.data.chatRoomName")
                                .value(chatRoomName)
                )
                .andExpect(
                        jsonPath("$.data.chatRoomDescription")
                                .value("삭제 보관된 메시지 갯수: 0")
                )
                .andExpect(
                        jsonPath("$.data.message")
                                .value("채팅방이 삭제되었습니다.")
                )
                .andExpect(jsonPath("$.error").isEmpty());
    }

    @Test
    @DisplayName("STOMP 연결 후 채팅 메시지 전송 및 수신 성공")
    void sendAndReceiveChatMessageSuccess() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                "stomp-message-room-" + createUniqueValue()
        );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        WebSocketStompClient stompClient =
                createStompClient();

        StompSession session =
                connectStomp(
                        stompClient,
                        testUser.accessToken()
                );

        BlockingQueue<SendChatMessageResponse> messages =
                new LinkedBlockingQueue<>();

        subscribe(
                session,
                roomId,
                messages
        );

        /*
         * SUBSCRIBE 이벤트에서 ENTER 메시지가 먼저 올 수 있으므로
         * TALK 타입 메시지를 필터링해서 검증한다.
         */
        String messageContent =
                "통합 테스트 메시지";

        // when
        session.send(
                CHAT_SEND_PREFIX
                        + roomId
                        + "/send",
                new SendChatMessageRequest(
                        messageContent
                )
        );

        SendChatMessageResponse response =
                awaitMessageType(
                        messages,
                        MessageType.TALK
                );

        // then
        assertThat(response)
                .isNotNull();

        assertThat(response.type())
                .isEqualTo(MessageType.TALK);

        assertThat(response.roomId())
                .isEqualTo(roomId);

        assertThat(response.senderId())
                .isEqualTo(testUser.userId());

        assertThat(response.senderNickname())
                .isEqualTo(testUser.nickname());

        assertThat(response.message())
                .isEqualTo(messageContent);

        assertThat(response.sendTime())
                .isNotNull();

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("STOMP 채팅방 구독 시 입장 시스템 메시지 자동 발행")
    void subscribePublishesEnterMessage() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                "subscribe-room-" + createUniqueValue()
        );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        WebSocketStompClient stompClient =
                createStompClient();

        StompSession session =
                connectStomp(
                        stompClient,
                        testUser.accessToken()
                );

        BlockingQueue<SendChatMessageResponse> messages =
                new LinkedBlockingQueue<>();

        // when
        subscribe(
                session,
                roomId,
                messages
        );

        SendChatMessageResponse response =
                awaitMessageType(
                        messages,
                        MessageType.ENTER
                );

        // then
        assertThat(response)
                .isNotNull();

        assertThat(response.type())
                .isEqualTo(MessageType.ENTER);

        assertThat(response.roomId())
                .isEqualTo(roomId);

        assertThat(response.senderId())
                .isEqualTo(testUser.userId());

        assertThat(response.senderNickname())
                .isEqualTo(testUser.nickname());

        assertThat(response.message())
                .isEqualTo(
                        testUser.nickname()
                                + "님이 입장했습니다."
                );

        assertThat(response.sendTime())
                .isNotNull();

        session.disconnect();
        stompClient.stop();
    }

    @Test
    @DisplayName("STOMP 연결 종료 시 다른 구독자에게 퇴장 메시지 자동 발행")
    void disconnectPublishesLeaveMessage() throws Exception {

        // given
        TestUser testUser = createAuthenticatedUser();

        Long roomId = createChatRoom(
                testUser.accessToken(),
                "disconnect-room-" + createUniqueValue()
        );

        addChatRoomMember(
                testUser.accessToken(),
                roomId,
                testUser.userId()
        );

        WebSocketStompClient observerClient =
                createStompClient();

        WebSocketStompClient disconnectClient =
                createStompClient();

        StompSession observerSession =
                connectStomp(
                        observerClient,
                        testUser.accessToken()
                );

        StompSession disconnectSession =
                connectStomp(
                        disconnectClient,
                        testUser.accessToken()
                );

        BlockingQueue<SendChatMessageResponse> observerMessages =
                new LinkedBlockingQueue<>();

        BlockingQueue<SendChatMessageResponse> disconnectMessages =
                new LinkedBlockingQueue<>();

        subscribe(
                observerSession,
                roomId,
                observerMessages
        );

        subscribe(
                disconnectSession,
                roomId,
                disconnectMessages
        );

        /*
         * 두 세션의 구독으로 발생한 ENTER 메시지를 제거한다.
         */
        drainPresenceMessages(observerMessages);

        // when
        disconnectSession.disconnect();

        SendChatMessageResponse response =
                awaitMessageType(
                        observerMessages,
                        MessageType.LEAVE
                );

        // then
        assertThat(response)
                .isNotNull();

        assertThat(response.type())
                .isEqualTo(MessageType.LEAVE);

        assertThat(response.roomId())
                .isEqualTo(roomId);

        assertThat(response.senderId())
                .isEqualTo(testUser.userId());

        assertThat(response.senderNickname())
                .isEqualTo(testUser.nickname());

        assertThat(response.message())
                .isEqualTo(
                        testUser.nickname()
                                + "님이 퇴장했습니다."
                );

        assertThat(response.sendTime())
                .isNotNull();

        observerSession.disconnect();

        observerClient.stop();
        disconnectClient.stop();
    }

    private TestUser createAuthenticatedUser()
            throws Exception {

        String uniqueValue =
                createUniqueValue();

        String username =
                "chat-user-" + uniqueValue;

        String nickname =
                NICKNAME_PREFIX + uniqueValue;

        createUser(
                username,
                nickname,
                PASSWORD
        );

        LoginResult loginResult =
                login(
                        username,
                        PASSWORD
                );

        Long userId =
                getUserId(
                        loginResult.accessToken()
                );

        return new TestUser(
                userId,
                username,
                nickname,
                loginResult.accessToken()
        );
    }

    private void createUser(
            String username,
            String nickname,
            String password
    ) throws Exception {

        UserCreateRequest request =
                new UserCreateRequest(
                        username,
                        nickname,
                        password
                );

        mockMvc.perform(
                        post(CREATE_USER_URL)
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.username")
                                .value(username)
                );
    }

    private LoginResult login(
            String username,
            String password
    ) throws Exception {

        LoginRequest request =
                new LoginRequest(
                        username,
                        password
                );

        MvcResult result =
                mockMvc.perform(
                                post(LOGIN_URL)
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(status().isOk())
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andExpect(
                                jsonPath("$.data.username")
                                        .value(username)
                        )
                        .andExpect(
                                header().string(
                                        "Authorization",
                                        startsWith("Bearer ")
                                )
                        )
                        .andExpect(
                                cookie().exists(
                                        "refreshToken"
                                )
                        )
                        .andReturn();

        String accessToken =
                result.getResponse()
                        .getHeader("Authorization");

        Cookie refreshTokenCookie =
                result.getResponse()
                        .getCookie("refreshToken");

        assertThat(accessToken)
                .isNotBlank()
                .startsWith("Bearer ");

        assertThat(refreshTokenCookie)
                .isNotNull();

        return new LoginResult(
                accessToken,
                refreshTokenCookie
        );
    }

    private Long getUserId(
            String accessToken
    ) throws Exception {

        MvcResult result =
                mockMvc.perform(
                                get(USER_ME_URL)
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
                        .andExpect(
                                jsonPath("$.data.userId")
                                        .isNumber()
                        )
                        .andReturn();

        Number userId =
                JsonPath.read(
                        result.getResponse()
                                .getContentAsString(),
                        "$.data.userId"
                );

        return userId.longValue();
    }

    private Long createChatRoom(
            String accessToken,
            String chatRoomName
    ) throws Exception {

        CreateChatRoomRequest request =
                new CreateChatRoomRequest(
                        chatRoomName
                );

        MvcResult result =
                mockMvc.perform(
                                post(CREATE_CHAT_ROOM_URL)
                                        .header(
                                                "Authorization",
                                                accessToken
                                        )
                                        .contentType(
                                                MediaType.APPLICATION_JSON
                                        )
                                        .content(
                                                objectMapper.writeValueAsString(
                                                        request
                                                )
                                        )
                        )
                        .andExpect(status().isCreated())
                        .andExpect(
                                jsonPath("$.success")
                                        .value(true)
                        )
                        .andExpect(
                                jsonPath("$.data.chatRoomId")
                                        .isNumber()
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

    private void addChatRoomMember(
            String accessToken,
            Long roomId,
            Long userId
    ) throws Exception {

        AddChatRoomMemberRequest request =
                new AddChatRoomMemberRequest(
                        roomId,
                        userId
                );

        mockMvc.perform(
                        post(ADD_CHAT_MEMBER_URL)
                                .header(
                                        "Authorization",
                                        accessToken
                                )
                                .contentType(
                                        MediaType.APPLICATION_JSON
                                )
                                .content(
                                        objectMapper.writeValueAsString(
                                                request
                                        )
                                )
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(
                        jsonPath("$.data.chatRoomId")
                                .value(roomId)
                );
    }

    private WebSocketStompClient createStompClient() {

        WebSocketStompClient stompClient =
                new WebSocketStompClient(
                        new StandardWebSocketClient()
                );

        stompClient.setMessageConverter(
                new JacksonJsonMessageConverter()
        );

        return stompClient;
    }

    private StompSession connectStomp(
            WebSocketStompClient stompClient,
            String accessToken
    ) throws Exception {

        String webSocketUrl =
                "ws://localhost:"
                        + port
                        + "/ws-stomp";

        StompHeaders connectHeaders =
                new StompHeaders();

        connectHeaders.add(
                "Authorization",
                accessToken
        );

        return stompClient
                .connectAsync(
                        webSocketUrl,
                        new WebSocketHttpHeaders(),
                        connectHeaders,
                        new StompSessionHandlerAdapter() {
                        }
                )
                .get(
                        5,
                        TimeUnit.SECONDS
                );
    }

    private void subscribe(
            StompSession session,
            Long roomId,
            BlockingQueue<SendChatMessageResponse> messages
    ) {

        session.subscribe(
                CHAT_SUBSCRIPTION_PREFIX + roomId,
                new StompFrameHandler() {

                    @Override
                    public Type getPayloadType(
                            StompHeaders headers
                    ) {
                        return SendChatMessageResponse.class;
                    }

                    @Override
                    public void handleFrame(
                            StompHeaders headers,
                            Object payload
                    ) {

                        messages.offer(
                                (SendChatMessageResponse) payload
                        );
                    }
                }
        );
    }

    private SendChatMessageResponse awaitMessageType(
            BlockingQueue<SendChatMessageResponse> messages,
            MessageType expectedType
    ) throws InterruptedException {

        long timeoutAt =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(5);

        while (System.nanoTime() < timeoutAt) {

            SendChatMessageResponse response =
                    messages.poll(
                            500,
                            TimeUnit.MILLISECONDS
                    );

            if (response == null) {
                continue;
            }

            if (response.type() == expectedType) {
                return response;
            }
        }

        return null;
    }

    private void drainPresenceMessages(
            BlockingQueue<SendChatMessageResponse> messages
    ) throws InterruptedException {

        long timeoutAt =
                System.nanoTime()
                        + TimeUnit.SECONDS.toNanos(1);

        while (System.nanoTime() < timeoutAt) {

            SendChatMessageResponse response =
                    messages.poll(
                            100,
                            TimeUnit.MILLISECONDS
                    );

            if (response == null) {
                continue;
            }

            if (response.type() != MessageType.ENTER) {
                messages.offer(response);
                return;
            }
        }
    }

    private String createUniqueValue() {

        return UUID.randomUUID()
                .toString()
                .substring(0, 8);
    }

    private record TestUser(
            Long userId,
            String username,
            String nickname,
            String accessToken
    ) {
    }
}