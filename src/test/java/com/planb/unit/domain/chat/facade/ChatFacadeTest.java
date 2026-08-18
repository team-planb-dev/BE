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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
}