package com.planb.unit.domain.chat.facade;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.planb.domain.chat.dto.MessageType;
import com.planb.domain.chat.dto.request.AddChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.AddChatUserRequest;
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomRequest;
import com.planb.domain.chat.dto.request.DeleteChatUserRequest;
import com.planb.domain.chat.dto.request.SendChatMessageRequest;
import com.planb.domain.chat.dto.response.AddChatUserResponse;
import com.planb.domain.chat.dto.response.CreateChatRoomResponse;
import com.planb.domain.chat.dto.response.DeleteChatRoomResponse;
import com.planb.domain.chat.dto.response.DeleteChatUserResponse;
import com.planb.domain.chat.dto.response.SendChatMessageResponse;
import com.planb.domain.chat.entity.ChatMessage;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.domain.chat.service.ChatMessageService;
import com.planb.domain.chat.service.ChatRoomMemberService;
import com.planb.domain.chat.service.ChatRoomService;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.WebSocketExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.query.chat.service.ChatMessageQueryService;
import com.planb.query.chat.service.ChatRoomMemberQueryService;
import com.planb.query.chat.service.ChatRoomQueryService;
import com.planb.query.user.service.UserQueryService;
import com.planb.domain.chat.dto.response.AiReplyContent;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.service.TravelService;
import com.planb.query.travel.service.TravelQueryService;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.domain.user.constant.SystemAccountConstants;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertThrows;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatFacadeTest {

    @Mock
    private ChatRoomQueryService chatRoomQueryService;

    @Mock
    private ChatRoomMemberQueryService chatRoomMemberQueryService;

    @Mock
    private ChatMessageQueryService chatMessageQueryService;

    @Mock
    private UserQueryService userQueryService;

    @Mock
    private ChatRoomService chatRoomService;

    @Mock
    private ChatRoomMemberService chatRoomMemberService;

    @Mock
    private ChatMessageService chatMessageService;

    @Mock
    private TravelQueryService travelQueryService;

    @Mock
    private TravelService travelService;

    @InjectMocks
    private ChatFacade chatFacade;

    @Test
    @DisplayName("채팅방 생성 요청을 ChatRoomService에 위임")
    void createChatRoomSuccess() {

        // given
        CreateChatRoomRequest request =
                mock(CreateChatRoomRequest.class);

        CreateChatRoomResponse expectedResponse =
                mock(CreateChatRoomResponse.class);

        when(chatRoomService
                .createChatRoom(request))
                .thenReturn(expectedResponse);

        // when
        CreateChatRoomResponse result =
                chatFacade.createChatRoom(request);

        // then
        assertThat(result)
                .isSameAs(expectedResponse);

        verify(chatRoomService)
                .createChatRoom(request);

        verifyNoInteractions(
                chatRoomQueryService,
                chatRoomMemberQueryService,
                chatMessageQueryService,
                userQueryService,
                chatRoomMemberService,
                chatMessageService
        );
    }

    @Test
    @DisplayName("중복 참여 여부를 확인한 후 사용자를 채팅방에 추가")
    void addChatUserSuccess() {

        // given
        Long userId = 10L;
        Long roomId = 1L;

        AddChatRoomMemberRequest request =
                mock(AddChatRoomMemberRequest.class);

        User user =
                mock(User.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        AddChatUserResponse expectedResponse =
                mock(AddChatUserResponse.class);

        when(request
                .userId())
                .thenReturn(userId);

        when(request
                .roomId())
                .thenReturn(roomId);

        when(userQueryService
                .findById(userId))
                .thenReturn(user);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoomMemberService
                .addChatUser(any(AddChatUserRequest.class)))
                .thenReturn(expectedResponse);

        ArgumentCaptor<AddChatUserRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        AddChatUserRequest.class
                );

        // when
        AddChatUserResponse result =
                chatFacade.addChatUser(request);

        // then
        assertThat(result)
                .isSameAs(expectedResponse);

        InOrder inOrder = inOrder(
                chatRoomMemberQueryService,
                userQueryService,
                chatRoomQueryService,
                chatRoomMemberService
        );

        inOrder.verify(chatRoomMemberQueryService)
                .validateDuplicateMemberWithRoom(
                        roomId,
                        userId
                );

        inOrder.verify(userQueryService)
                .findById(userId);

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatRoomMemberService)
                .addChatUser(requestCaptor.capture());

        AddChatUserRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest
                .user())
                .isSameAs(user);

        assertThat(capturedRequest
                .chatRoom())
                .isSameAs(chatRoom);

        verifyNoInteractions(
                chatMessageQueryService,
                chatRoomService,
                chatMessageService
        );
    }

    @Test
    @DisplayName("이미 참여한 사용자를 채팅방에 추가하면 예외 발생")
    void addChatUserDuplicateFail() {

        // given
        Long userId = 10L;
        Long roomId = 1L;

        AddChatRoomMemberRequest request =
                mock(AddChatRoomMemberRequest.class);

        when(request
                .userId())
                .thenReturn(userId);

        when(request
                .roomId())
                .thenReturn(roomId);

        doThrow(new BaseException(
                WebSocketExceptionEnum.USER_ROOM_DUPLICATED
        ))
                .when(chatRoomMemberQueryService)
                .validateDuplicateMemberWithRoom(
                        roomId,
                        userId
                );

        // when & then
        assertThatThrownBy(() ->
                chatFacade.addChatUser(request))
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryService)
                .validateDuplicateMemberWithRoom(
                        roomId,
                        userId
                );

        verifyNoInteractions(
                userQueryService,
                chatRoomQueryService,
                chatRoomMemberService,
                chatMessageQueryService,
                chatRoomService,
                chatMessageService
        );
    }

    @Test
    @DisplayName("채팅방 멤버와 채팅방을 조회한 후, 채팅방에서 멤버를 삭제")
    void deleteChatUserSuccess() {

        // given
        Long userId = 10L;
        Long roomId = 1L;

        DeleteChatRoomMemberRequest request =
                mock(DeleteChatRoomMemberRequest.class);

        ChatRoomMember chatRoomMember =
                mock(ChatRoomMember.class);

        User user =
                mock(User.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        DeleteChatUserResponse expectedResponse =
                mock(DeleteChatUserResponse.class);

        when(request
                .userId())
                .thenReturn(userId);

        when(request
                .roomId())
                .thenReturn(roomId);

        when(chatRoomMemberQueryService
                .findByUserId(roomId, userId))
                .thenReturn(chatRoomMember);

        when(userQueryService
                .findById(userId))
                .thenReturn(user);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoomMemberService
                .deleteChatUser(any(DeleteChatUserRequest.class)))
                .thenReturn(expectedResponse);

        ArgumentCaptor<DeleteChatUserRequest> requestCaptor =
                ArgumentCaptor.forClass(
                        DeleteChatUserRequest.class
                );

        // when
        DeleteChatUserResponse result =
                chatFacade.deleteChatUser(request);

        // then
        assertThat(result)
                .isSameAs(expectedResponse);

        InOrder inOrder = inOrder(
                chatRoomMemberQueryService,
                userQueryService,
                chatRoomQueryService,
                chatRoomMemberService
        );

        inOrder.verify(chatRoomMemberQueryService)
                .findByUserId(roomId, userId);

        inOrder.verify(userQueryService)
                .findById(userId);

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatRoomMemberService)
                .deleteChatUser(requestCaptor.capture());

        DeleteChatUserRequest capturedRequest =
                requestCaptor.getValue();

        assertThat(capturedRequest
                .chatRoomMember())
                .isSameAs(chatRoomMember);

        assertThat(capturedRequest
                .user())
                .isSameAs(user);

        assertThat(capturedRequest
                .chatRoom())
                .isSameAs(chatRoom);

        verifyNoInteractions(
                chatMessageQueryService,
                chatRoomService,
                chatMessageService
        );
    }

    @Test
    @DisplayName("채팅방 삭제 시 멤버 관계, 채팅방, 메시지를 순서대로 삭제")
    void deleteChatRoomSuccess() {

        // given
        Long roomId = 1L;
        String chatRoomName = "테스트 채팅방";
        Long deletedMessageCount = 3L;

        DeleteChatRoomRequest request =
                mock(DeleteChatRoomRequest.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        when(request
                .roomId())
                .thenReturn(roomId);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoom
                .getId())
                .thenReturn(roomId);

        when(chatRoom
                .getChatRoomName())
                .thenReturn(chatRoomName);

        when(chatMessageQueryService
                .softDeleteAllMessageInChatRoom(roomId))
                .thenReturn(deletedMessageCount);

        // when
        DeleteChatRoomResponse result =
                chatFacade.deleteChatRoom(request);

        // then
        assertThat(result)
                .isNotNull();

        assertThat(result.chatRoomId())
                .isEqualTo(roomId);

        assertThat(result.chatRoomName())
                .isEqualTo(chatRoomName);

        assertThat(result.message())
                .isEqualTo("채팅방이 삭제되었습니다.");

        InOrder inOrder = inOrder(
                chatRoomQueryService,
                chatRoomMemberQueryService,
                chatRoomService,
                chatMessageQueryService
        );

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatRoomMemberQueryService)
                .deleteAllChatRoomMemberByRoomId(roomId);

        inOrder.verify(chatRoomService)
                .deleteChatRoom(chatRoom);

        inOrder.verify(chatMessageQueryService)
                .softDeleteAllMessageInChatRoom(roomId);

        verifyNoInteractions(
                userQueryService,
                chatRoomMemberService,
                chatMessageService
        );
    }

    @Test
    @DisplayName("일반 채팅 메시지를 생성 & 저장 후, 채팅방에 발행")
    void publishMessageSuccess() {

        // given
        Long roomId = 1L;
        String username = "testUser@example.com";
        String messageContent = "안녕하세요";

        SendChatMessageRequest request =
                mock(SendChatMessageRequest.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        User sender =
                mock(User.class);

        ChatMessage chatMessage =
                mock(ChatMessage.class);

        SendChatMessageResponse response =
                mock(SendChatMessageResponse.class);

        when(request
                .message())
                .thenReturn(messageContent);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(userQueryService
                .findByUsername(username))
                .thenReturn(sender);

        when(chatMessageService
                .createChatMessage(
                        chatRoom,
                        sender,
                        messageContent
                ))
                .thenReturn(chatMessage);

        when(chatMessageService
                .makeChatResponse(
                        roomId,
                        sender,
                        chatMessage
                ))
                .thenReturn(response);

        // when
        chatFacade.publishMessage(
                roomId,
                request,
                username
        );

        // then
        InOrder inOrder = inOrder(
                chatRoomQueryService,
                userQueryService,
                chatMessageService
        );

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(userQueryService)
                .findByUsername(username);

        inOrder.verify(chatMessageService)
                .createChatMessage(
                        chatRoom,
                        sender,
                        messageContent
                );

        inOrder.verify(chatMessageService)
                .saveMessage(chatMessage);

        inOrder.verify(chatMessageService)
                .makeChatResponse(
                        roomId,
                        sender,
                        chatMessage
                );

        inOrder.verify(chatMessageService)
                .publishMessage(
                        roomId,
                        response
                );

        verifyNoInteractions(
                chatRoomMemberQueryService,
                chatMessageQueryService,
                chatRoomService,
                chatRoomMemberService
        );
    }

    @Test
    @DisplayName("입장 시스템 메시지를 생성한 뒤, 채팅방에 발행")
    void publishEnterSystemMessageSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 10L;
        String username = "testUser@example.com";
        String nickname = "테스트유저";
        String systemMessage =
                nickname + "님이 입장했습니다.";

        MessageType messageType =
                MessageType.ENTER;

        User participant =
                mock(User.class);

        when(userQueryService
                .findByUsername(username))
                .thenReturn(participant);

        when(participant
                .getId())
                .thenReturn(userId);

        when(participant
                .getNickname())
                .thenReturn(nickname);

        when(chatMessageService
                .createSystemMessage(
                        messageType,
                        nickname
                ))
                .thenReturn(systemMessage);

        ArgumentCaptor<SendChatMessageResponse> responseCaptor =
                ArgumentCaptor.forClass(
                        SendChatMessageResponse.class
                );

        // when
        chatFacade.publishSystemMessage(
                roomId,
                username,
                messageType
        );

        // then
        verify(userQueryService)
                .findByUsername(username);

        verify(chatMessageService)
                .createSystemMessage(
                        messageType,
                        nickname
                );

        verify(chatMessageService)
                .publishMessage(
                        eq(roomId),
                        responseCaptor.capture()
                );

        SendChatMessageResponse response =
                responseCaptor.getValue();

        assertThat(response)
                .isNotNull();

        assertThat(response.type())
                .isEqualTo(messageType);

        assertThat(response.roomId())
                .isEqualTo(roomId);

        assertThat(response.senderId())
                .isEqualTo(userId);

        assertThat(response.senderNickname())
                .isEqualTo(nickname);

        assertThat(response.message())
                .isEqualTo(systemMessage);

        assertThat(response.sendTime())
                .isNotNull();

        verifyNoInteractions(
                chatRoomQueryService,
                chatRoomMemberQueryService,
                chatMessageQueryService,
                chatRoomService,
                chatRoomMemberService
        );
    }

    @Test
    @DisplayName("퇴장 시스템 메시지를 생성한 뒤 채팅방에 발행")
    void publishLeaveSystemMessageSuccess() {

        // given
        Long roomId = 1L;
        Long userId = 10L;
        String username = "testUser@example.com";
        String nickname = "테스트유저";
        String systemMessage =
                nickname + "님이 퇴장했습니다.";

        MessageType messageType =
                MessageType.LEAVE;

        User participant =
                mock(User.class);

        when(userQueryService
                .findByUsername(username))
                .thenReturn(participant);

        when(participant
                .getId())
                .thenReturn(userId);

        when(participant
                .getNickname())
                .thenReturn(nickname);

        when(chatMessageService
                .createSystemMessage(
                        messageType,
                        nickname
                ))
                .thenReturn(systemMessage);

        ArgumentCaptor<SendChatMessageResponse> responseCaptor =
                ArgumentCaptor.forClass(
                        SendChatMessageResponse.class
                );

        // when
        chatFacade.publishSystemMessage(
                roomId,
                username,
                messageType
        );

        // then
        verify(userQueryService)
                .findByUsername(username);

        verify(chatMessageService)
                .createSystemMessage(
                        messageType,
                        nickname
                );

        verify(chatMessageService)
                .publishMessage(
                        eq(roomId),
                        responseCaptor.capture()
                );

        SendChatMessageResponse response =
                responseCaptor.getValue();

        assertThat(response)
                .isNotNull();

        assertThat(response.type())
                .isEqualTo(messageType);

        assertThat(response.roomId())
                .isEqualTo(roomId);

        assertThat(response.senderId())
                .isEqualTo(userId);

        assertThat(response.senderNickname())
                .isEqualTo(nickname);

        assertThat(response.message())
                .isEqualTo(systemMessage);

        assertThat(response.sendTime())
                .isNotNull();

        verifyNoInteractions(
                chatRoomQueryService,
                chatRoomMemberQueryService,
                chatMessageQueryService,
                chatRoomService,
                chatRoomMemberService
        );
    }

    @Test
    @DisplayName("시스템 메시지는 DB에 저장하지 않고 바로 발행")
    void publishSystemMessageDoesNotSaveMessage() {

        // given
        Long roomId = 1L;
        String username = "testUser@example.com";
        String nickname = "테스트유저";

        User participant =
                mock(User.class);

        when(userQueryService
                .findByUsername(username))
                .thenReturn(participant);

        when(participant
                .getId())
                .thenReturn(10L);

        when(participant
                .getNickname())
                .thenReturn(nickname);

        when(chatMessageService
                .createSystemMessage(
                        MessageType.ENTER,
                        nickname
                ))
                .thenReturn(
                        "테스트유저님이 입장했습니다."
                );

        // when
        chatFacade.publishSystemMessage(
                roomId,
                username,
                MessageType.ENTER
        );

        // then
        verify(chatMessageService, never())
                .saveMessage(any(ChatMessage.class));

        verify(chatMessageService)
                .publishMessage(
                        eq(roomId),
                        any(SendChatMessageResponse.class)
                );
    }

    @Test
    @DisplayName("travel 소유자이고 이미 채팅방이 있으면 기존 채팅방을 반환")
    void findOrCreateTravelChatRoomReturnsExistingRoom() {

        // given
        Long travelId = 1L;
        Long userId = 10L;
        String username = "testUser@example.com";

        User user = mock(User.class);
        ChatRoom chatRoom = mock(ChatRoom.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);

        when(user.getId())
                .thenReturn(userId);

        when(travelQueryService.existsByIdAndUserId(travelId, userId))
                .thenReturn(true);

        when(chatRoomQueryService.findChatRoomByTravelId(travelId))
                .thenReturn(Optional.of(chatRoom));

        when(chatRoom.getId())
                .thenReturn(100L);

        when(chatRoom.getChatRoomName())
                .thenReturn("부산 여행");

        // when
        CreateChatRoomResponse result =
                chatFacade.findOrCreateTravelChatRoom(travelId, username);

        // then
        assertThat(result.chatRoomId())
                .isEqualTo(100L);

        assertThat(result.chatRoomName())
                .isEqualTo("부산 여행");

        assertThat(result.message())
                .isEqualTo("채팅방이 조회되었습니다.");

        verify(travelService, never())
                .findTravelById(any());

        verify(chatRoomService, never())
                .createChatRoomForTravel(any());
    }

    @Test
    @DisplayName("travel 소유자이고 채팅방이 없으면 새로 생성")
    void findOrCreateTravelChatRoomCreatesNewRoom() {

        // given
        Long travelId = 1L;
        Long userId = 10L;
        String username = "testUser@example.com";

        User user = mock(User.class);
        Travel travel = mock(Travel.class);
        ChatRoom chatRoom = mock(ChatRoom.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);

        when(user.getId())
                .thenReturn(userId);

        when(travelQueryService.existsByIdAndUserId(travelId, userId))
                .thenReturn(true);

        when(chatRoomQueryService.findChatRoomByTravelId(travelId))
                .thenReturn(Optional.empty());

        when(travelService.findTravelById(travelId))
                .thenReturn(travel);

        when(chatRoomService.createChatRoomForTravel(travel))
                .thenReturn(chatRoom);

        when(chatRoom.getId())
                .thenReturn(200L);

        when(chatRoom.getChatRoomName())
                .thenReturn("부산 여행");

        // when
        CreateChatRoomResponse result =
                chatFacade.findOrCreateTravelChatRoom(travelId, username);

        // then
        assertThat(result.chatRoomId())
                .isEqualTo(200L);

        assertThat(result.message())
                .isEqualTo("채팅방이 생성되었습니다.");

        verify(travelService)
                .findTravelById(travelId);

        verify(chatRoomService)
                .createChatRoomForTravel(travel);
    }

    @Test
    @DisplayName("travel 소유자가 아니면 예외 발생")
    void findOrCreateTravelChatRoomThrowsWhenNotOwner() {

        // given
        Long travelId = 1L;
        Long userId = 10L;
        String username = "testUser@example.com";

        User user = mock(User.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);

        when(user.getId())
                .thenReturn(userId);

        when(travelQueryService.existsByIdAndUserId(travelId, userId))
                .thenReturn(false);

        // when & then
        assertThatThrownBy(() ->
                chatFacade.findOrCreateTravelChatRoom(travelId, username))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(
                chatRoomQueryService,
                travelService,
                chatRoomService
        );
    }

    @Test
    @DisplayName("채팅방에 연결된 travelId 조회")
    void getTravelIdByRoomIdReturnsTravelId() {

        // given
        Long roomId = 1L;

        Travel travel = mock(Travel.class);
        ChatRoom chatRoom = mock(ChatRoom.class);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoom.getTravel())
                .thenReturn(travel);

        when(travel.getId())
                .thenReturn(50L);

        // when
        Long result = chatFacade.getTravelIdByRoomId(roomId);

        // then
        assertThat(result)
                .isEqualTo(50L);
    }

    @Test
    @DisplayName("채팅방에 travel이 연결되어 있지 않으면 예외 발생")
    void getTravelIdByRoomIdThrowsWhenNotLinked() {

        // given
        Long roomId = 1L;

        ChatRoom chatRoom = mock(ChatRoom.class);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoom.getTravel())
                .thenReturn(null);

        // when & then
        BaseException exception = assertThrows(
                BaseException.class,
                () -> chatFacade.getTravelIdByRoomId(roomId)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(WebSocketExceptionEnum.TRAVEL_NOT_LINKED.getCode());

        assertThat(exception.getMessage())
                .isEqualTo(WebSocketExceptionEnum.TRAVEL_NOT_LINKED.getMessage());
    }

    @Test
    @DisplayName("채팅방에 연결된 travelId를 Optional로 조회")
    void findTravelIdByRoomIdReturnsValueWhenLinked() {

        // given
        Long roomId = 1L;

        Travel travel = mock(Travel.class);
        ChatRoom chatRoom = mock(ChatRoom.class);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoom.getTravel())
                .thenReturn(travel);

        when(travel.getId())
                .thenReturn(50L);

        // when
        Optional<Long> result =
                chatFacade.findTravelIdByRoomId(roomId);

        // then
        assertThat(result)
                .contains(50L);
    }

    @Test
    @DisplayName("채팅방에 travel이 연결되어 있지 않으면 빈 값 반환")
    void findTravelIdByRoomIdReturnsEmptyWhenNotLinked() {

        // given
        Long roomId = 1L;

        ChatRoom chatRoom = mock(ChatRoom.class);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoom.getTravel())
                .thenReturn(null);

        // when
        Optional<Long> result =
                chatFacade.findTravelIdByRoomId(roomId);

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName("AI 봇 명의로 메시지를 생성 & 저장 후, 채팅방에 발행")
    void publishAiReplySuccess() {

        // given
        Long roomId = 1L;
        String message = "변경 사항 없음";

        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse(
                        "부산 여행",
                        List.of(),
                        List.of("변경 사항 없음"),
                        true
                );

        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        User aiUser = mock(User.class);
        ChatRoom chatRoom = mock(ChatRoom.class);
        ChatMessage chatMessage = mock(ChatMessage.class);
        SendChatMessageResponse response = mock(SendChatMessageResponse.class);

        when(userQueryService
                .findByUsername(SystemAccountConstants.AI_BOT_USERNAME))
                .thenReturn(aiUser);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatMessageService
                .createChatMessage(chatRoom, aiUser, message))
                .thenReturn(chatMessage);

        when(chatMessageService
                .makeAiChatResponse(
                        roomId,
                        aiUser,
                        chatMessage,
                        preview,
                        MessageType.TALK
                ))
                .thenReturn(response);

        // when
        chatFacade.publishAiReply(
                roomId,
                message,
                preview,
                MessageType.TALK
        );

        // then
        InOrder inOrder = inOrder(
                userQueryService,
                chatRoomQueryService,
                chatMessageService
        );

        inOrder.verify(userQueryService)
                .findByUsername(SystemAccountConstants.AI_BOT_USERNAME);

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatMessageService)
                .createChatMessage(chatRoom, aiUser, message);

        inOrder.verify(chatMessageService)
                .saveMessage(chatMessage);

        inOrder.verify(chatMessageService)
                .makeAiChatResponse(
                        roomId,
                        aiUser,
                        chatMessage,
                        preview,
                        MessageType.TALK
                );

        inOrder.verify(chatMessageService)
                .publishMessage(roomId, response);
    }

    @Test
    @DisplayName("TALK 응답 컨텐츠를 결정한 뒤 AI 응답으로 발행")
    void publishTalkReplySuccess() {

        // given
        Long roomId = 1L;

        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse(
                        "부산 여행",
                        List.of(),
                        List.of("변경 사항 없음"),
                        true
                );

        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        AiReplyContent content =
                new AiReplyContent("변경 사항 없음", preview);

        User aiUser = mock(User.class);
        ChatRoom chatRoom = mock(ChatRoom.class);
        ChatMessage chatMessage = mock(ChatMessage.class);
        SendChatMessageResponse response = mock(SendChatMessageResponse.class);

        when(chatMessageService.resolveAiReplyContent(preview))
                .thenReturn(content);

        when(userQueryService
                .findByUsername(SystemAccountConstants.AI_BOT_USERNAME))
                .thenReturn(aiUser);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatMessageService
                .createChatMessage(chatRoom, aiUser, "변경 사항 없음"))
                .thenReturn(chatMessage);

        when(chatMessageService
                .makeAiChatResponse(
                        roomId,
                        aiUser,
                        chatMessage,
                        preview,
                        MessageType.TALK
                ))
                .thenReturn(response);

        // when
        chatFacade.publishTalkReply(roomId, preview);

        // then
        verify(chatMessageService)
                .resolveAiReplyContent(preview);

        verify(chatMessageService)
                .publishMessage(roomId, response);
    }
}
