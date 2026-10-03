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
import static org.mockito.Mockito.times;
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
        String username = "testUser@example.com";

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
                .findByUsername(username))
                .thenReturn(user);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoomMemberService
                .addChatUser(chatRoom, user))
                .thenReturn(expectedResponse);

        // when
        AddChatUserResponse result =
                chatFacade.addChatUser(
                        request,
                        username
                );

        // then
        assertThat(result)
                .isSameAs(expectedResponse);

        InOrder inOrder = inOrder(
                userQueryService,
                chatRoomMemberQueryService,
                chatRoomQueryService,
                chatRoomMemberService
        );

        inOrder.verify(userQueryService)
                .findByUsername(username);

        inOrder.verify(chatRoomMemberService)
                .validateRequestedUser(userId, user);

        inOrder.verify(chatRoomMemberQueryService)
                .validateDuplicateMemberWithRoom(
                        roomId,
                        userId
                );

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatRoomMemberService)
                .validateTravelRoomOwner(chatRoom, user);

        inOrder.verify(chatRoomMemberService)
                .addChatUser(chatRoom, user);

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
        String username = "testUser@example.com";

        AddChatRoomMemberRequest request =
                mock(AddChatRoomMemberRequest.class);

        when(request
                .userId())
                .thenReturn(userId);

        when(request
                .roomId())
                .thenReturn(roomId);

        User user =
                mock(User.class);

        when(userQueryService
                .findByUsername(username))
                .thenReturn(user);

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
                chatFacade.addChatUser(
                        request,
                        username
                ))
                .isInstanceOf(BaseException.class);

        verify(chatRoomMemberQueryService)
                .validateDuplicateMemberWithRoom(
                        roomId,
                        userId
                );

        verifyNoInteractions(
                chatRoomQueryService,
                chatMessageQueryService,
                chatRoomService,
                chatMessageService
        );

        verify(chatRoomMemberService)
                .validateRequestedUser(userId, user);
    }

    @Test
    @DisplayName("채팅방 멤버와 채팅방을 조회한 후, 채팅방에서 멤버를 삭제")
    void deleteChatUserSuccess() {

        // given
        Long userId = 10L;
        Long roomId = 1L;
        String username = "testUser@example.com";

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

        when(userQueryService
                .findByUsername(username))
                .thenReturn(user);

        when(chatRoomMemberQueryService
                .findByUserId(roomId, userId))
                .thenReturn(chatRoomMember);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoomMemberService
                .deleteChatUser(chatRoomMember, chatRoom, user))
                .thenReturn(expectedResponse);

        // when
        DeleteChatUserResponse result =
                chatFacade.deleteChatUser(
                        request,
                        username
                );

        // then
        assertThat(result)
                .isSameAs(expectedResponse);

        InOrder inOrder = inOrder(
                userQueryService,
                chatRoomMemberQueryService,
                chatRoomQueryService,
                chatRoomMemberService
        );

        inOrder.verify(userQueryService)
                .findByUsername(username);

        inOrder.verify(chatRoomMemberService)
                .validateRequestedUser(userId, user);

        inOrder.verify(chatRoomMemberQueryService)
                .findByUserId(roomId, userId);

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatRoomMemberService)
                .deleteChatUser(chatRoomMember, chatRoom, user);

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
        String username = "testUser@example.com";
        Long deletedMessageCount = 3L;

        DeleteChatRoomRequest request =
                mock(DeleteChatRoomRequest.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        User user =
                mock(User.class);

        when(request
                .roomId())
                .thenReturn(roomId);

        when(userQueryService
                .findByUsername(username))
                .thenReturn(user);

        when(chatRoomQueryService
                .findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        when(chatRoom.getId())
                .thenReturn(roomId);

        when(chatMessageQueryService
                .softDeleteAllMessageInChatRoom(roomId))
                .thenReturn(deletedMessageCount);

        DeleteChatRoomResponse expectedResponse =
                new DeleteChatRoomResponse(
                        roomId,
                        chatRoomName,
                        "삭제 보관된 메시지 갯수: 3",
                        "채팅방이 삭제되었습니다."
                );

        when(chatRoomService.deletedChatRoomResponse(chatRoom, deletedMessageCount))
                .thenReturn(expectedResponse);

        // when
        DeleteChatRoomResponse result =
                chatFacade.deleteChatRoom(
                        request,
                        username
                );

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
                userQueryService,
                chatRoomQueryService,
                chatRoomMemberQueryService,
                chatRoomMemberService,
                chatRoomService,
                chatMessageQueryService
        );

        inOrder.verify(userQueryService)
                .findByUsername(username);

        inOrder.verify(chatRoomMemberService)
                .validateRoomMember(roomId, user);

        inOrder.verify(chatRoomQueryService)
                .findChatRoomByRoomId(roomId);

        inOrder.verify(chatRoomMemberService)
                .validateTravelRoomOwner(chatRoom, user);

        inOrder.verify(chatRoomMemberQueryService)
                .deleteAllChatRoomMemberByRoomId(roomId);

        inOrder.verify(chatRoomService)
                .deleteChatRoom(chatRoom);

        inOrder.verify(chatMessageQueryService)
                .softDeleteAllMessageInChatRoom(roomId);

        inOrder.verify(chatRoomService)
                .deletedChatRoomResponse(chatRoom, deletedMessageCount);

        verifyNoInteractions(
                chatMessageService
        );
    }

    @Test
    @DisplayName("사용자 메시지 저장 및 발행 위임")
    void publishMessageSuccess() {

        SendChatMessageRequest request =
                new SendChatMessageRequest(MessageType.TALK, "안녕하세요");

        chatFacade.publishMessage(1L, request, "user@example.com");

        verify(chatMessageService)
                .publishUserMessage(1L, "안녕하세요", "user@example.com");
    }

    @Test
    @DisplayName("입장 시스템 메시지 발행 위임")
    void publishEnterSystemMessageSuccess() {

        chatFacade.publishSystemMessage(
                1L,
                "user@example.com",
                MessageType.ENTER
        );

        verify(chatMessageService)
                .publishSystemMessage(1L, "user@example.com", MessageType.ENTER);
    }

    @Test
    @DisplayName("퇴장 시스템 메시지 발행 위임")
    void publishLeaveSystemMessageSuccess() {

        chatFacade.publishSystemMessage(
                1L,
                "user@example.com",
                MessageType.LEAVE
        );

        verify(chatMessageService)
                .publishSystemMessage(1L, "user@example.com", MessageType.LEAVE);
    }

    @Test
    @DisplayName("시스템 메시지 Service 위임")
    void publishSystemMessageDoesNotSaveMessage() {

        chatFacade.publishSystemMessage(
                1L,
                "user@example.com",
                MessageType.ENTER
        );

        verify(chatMessageService)
                .publishSystemMessage(1L, "user@example.com", MessageType.ENTER);
        verifyNoInteractions(chatMessageQueryService);
    }

    @Test
    @DisplayName("travel 소유자이고 이미 채팅방이 있으면 기존 채팅방을 반환")
    void findOrCreateTravelChatRoomReturnsExistingRoom() {

        Long travelId = 1L;
        String username = "testUser@example.com";
        User user = mock(User.class);
        ChatRoom room = mock(ChatRoom.class);
        CreateChatRoomResponse expected = mock(CreateChatRoomResponse.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);
        when(user.getId())
                .thenReturn(10L);
        when(chatRoomQueryService.findChatRoomByTravelId(travelId))
                .thenReturn(Optional.of(room));
        when(chatRoomService.travelChatRoomResponse(room, true))
                .thenReturn(expected);

        CreateChatRoomResponse result = chatFacade.findOrCreateTravelChatRoom(
                travelId,
                username
        );

        assertThat(result).isSameAs(expected);
        verify(travelQueryService)
                .validateOwner(travelId, 10L);
        verify(chatRoomMemberService)
                .ensureMember(room, user);
        verify(chatRoomService, never())
                .createChatRoomForTravel(any());
    }

    @Test
    @DisplayName("travel 소유자이고 채팅방이 없으면 새로 생성")
    void findOrCreateTravelChatRoomCreatesNewRoom() {

        Long travelId = 1L;
        String username = "testUser@example.com";
        User user = mock(User.class);
        Travel travel = mock(Travel.class);
        ChatRoom room = mock(ChatRoom.class);
        CreateChatRoomResponse expected = mock(CreateChatRoomResponse.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);
        when(user.getId())
                .thenReturn(10L);
        when(chatRoomQueryService.findChatRoomByTravelId(travelId))
                .thenReturn(Optional.empty());
        when(travelService.findTravelById(travelId))
                .thenReturn(travel);
        when(chatRoomService.createChatRoomForTravel(travel))
                .thenReturn(room);
        when(chatRoomService.travelChatRoomResponse(room, false))
                .thenReturn(expected);

        CreateChatRoomResponse result = chatFacade.findOrCreateTravelChatRoom(
                travelId,
                username
        );

        assertThat(result).isSameAs(expected);
        verify(travelQueryService)
                .validateOwner(travelId, 10L);
        verify(chatRoomMemberService)
                .ensureMember(room, user);
    }

    @Test
    @DisplayName("여행 채팅방 멤버십 보장 위임")
    void findOrCreateTravelChatRoomDoesNotAddExistingMember() {

        Long travelId = 1L;
        String username = "testUser@example.com";
        User user = mock(User.class);
        ChatRoom room = mock(ChatRoom.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);
        when(user.getId())
                .thenReturn(10L);
        when(chatRoomQueryService.findChatRoomByTravelId(travelId))
                .thenReturn(Optional.of(room));

        chatFacade.findOrCreateTravelChatRoom(travelId, username);

        verify(chatRoomMemberService)
                .ensureMember(room, user);
        verify(chatRoomMemberService, never())
                .addChatUser(any());
    }

    @Test
    @DisplayName("travel 소유자가 아니면 예외 발생")
    void findOrCreateTravelChatRoomThrowsWhenNotOwner() {

        Long travelId = 1L;
        String username = "testUser@example.com";
        User user = mock(User.class);

        when(userQueryService.findByUsername(username))
                .thenReturn(user);
        when(user.getId())
                .thenReturn(10L);
        org.mockito.Mockito.doThrow(new ForbiddenException(new Object[]{"권한 없음"}))
                .when(travelQueryService)
                .validateOwner(travelId, 10L);

        assertThatThrownBy(() -> chatFacade.findOrCreateTravelChatRoom(
                travelId,
                username
        ))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(chatRoomQueryService, chatRoomService, chatRoomMemberService);
    }

    @Test
    @DisplayName("채팅방에 연결된 travelId 조회")
    void getTravelIdByRoomIdReturnsTravelId() {

        when(chatRoomQueryService.getTravelIdByRoomId(1L))
                .thenReturn(10L);

        assertThat(chatFacade.getTravelIdByRoomId(1L))
                .isEqualTo(10L);

        verify(chatRoomQueryService)
                .getTravelIdByRoomId(1L);
    }

    @Test
    @DisplayName("채팅방에 travel이 연결되어 있지 않으면 예외 발생")
    void getTravelIdByRoomIdThrowsWhenNotLinked() {

        when(chatRoomQueryService.getTravelIdByRoomId(1L))
                .thenThrow(new BaseException(WebSocketExceptionEnum.TRAVEL_NOT_LINKED));

        BaseException exception = assertThrows(
                BaseException.class,
                () -> chatFacade.getTravelIdByRoomId(1L)
        );

        assertThat(exception.getErrorCode())
                .isEqualTo(WebSocketExceptionEnum.TRAVEL_NOT_LINKED.getCode());

        assertThat(exception.getMessage())
                .isEqualTo(WebSocketExceptionEnum.TRAVEL_NOT_LINKED.getMessage());
    }

    @Test
    @DisplayName("채팅방에 연결된 travelId를 Optional로 조회")
    void findTravelIdByRoomIdReturnsValueWhenLinked() {

        Long roomId = 1L;

        when(chatRoomQueryService.findTravelIdByRoomId(roomId))
                .thenReturn(Optional.of(50L));

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

        Long roomId = 1L;

        when(chatRoomQueryService.findTravelIdByRoomId(roomId))
                .thenReturn(Optional.empty());

        // when
        Optional<Long> result =
                chatFacade.findTravelIdByRoomId(roomId);

        // then
        assertThat(result)
                .isEmpty();
    }

    @Test
    @DisplayName("AI 응답 저장 및 발행 위임")
    void publishAiReplySuccess() {

        EditPlanPreviewResponse preview = mock(EditPlanPreviewResponse.class);

        chatFacade.publishAiReply(1L, "수정안", preview, MessageType.TALK);

        verify(chatMessageService)
                .publishAiReply(1L, "수정안", preview, MessageType.TALK);
    }

    @Test
    @DisplayName("대화 응답 발행 위임")
    void publishTalkReplySuccess() {

        EditPlanPreviewResponse preview = mock(EditPlanPreviewResponse.class);

        chatFacade.publishTalkReply(1L, preview);

        verify(chatMessageService)
                .publishTalkReply(1L, preview);
    }

    @Test
    @DisplayName("AI 인사 조건 확인 위임")
    void publishAiGreetingIfNeededSkipsWhenNotTravelLinked() {

        chatFacade.publishAiGreetingIfNeeded(1L, "user@example.com");

        verify(chatMessageService)
                .publishAiGreetingIfNeeded(1L, "user@example.com");
    }

    @Test
    @DisplayName("기존 메시지 확인 위임")
    void publishAiGreetingIfNeededSkipsWhenMessageExists() {

        chatFacade.publishAiGreetingIfNeeded(1L, "user@example.com");

        verify(chatMessageService)
                .publishAiGreetingIfNeeded(1L, "user@example.com");
    }

    @Test
    @DisplayName("첫 AI 인사 발행 위임")
    void publishAiGreetingIfNeededPublishesGreetings() {

        chatFacade.publishAiGreetingIfNeeded(1L, "user@example.com");

        verify(chatMessageService)
                .publishAiGreetingIfNeeded(1L, "user@example.com");
    }

    @Test
    @DisplayName("확정 응답 발행 위임")
    void publishConfirmReplySuccess() {

        chatFacade.publishConfirmReply(1L);

        verify(chatMessageService)
                .publishConfirmReply(1L);
    }

    @Test
    @DisplayName("취소 응답 발행 위임")
    void publishCancelReplySuccess() {

        chatFacade.publishCancelReply(1L);

        verify(chatMessageService)
                .publishCancelReply(1L);
    }

    @Test
    @DisplayName("여행 소유자가 아닌 사용자의 여행 채팅방 가입 거부")
    void addTravelChatRoomMemberByNonOwnerForbidden() {

        // given
        Long roomId = 1L;
        Long userId = 10L;
        String username = "requester@example.com";

        AddChatRoomMemberRequest request =
                mock(AddChatRoomMemberRequest.class);

        User requester =
                mock(User.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        when(request.userId())
                .thenReturn(userId);

        when(request.roomId())
                .thenReturn(roomId);

        when(userQueryService.findByUsername(username))
                .thenReturn(requester);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        doThrow(new ForbiddenException(
                new Object[]{"해당 여행 채팅방에 대한 접근 권한이 없습니다."}
        ))
                .when(chatRoomMemberService)
                .validateTravelRoomOwner(chatRoom, requester);

        // when & then
        assertThatThrownBy(() ->
                chatFacade.addChatUser(
                        request,
                        username
                ))
                .isInstanceOf(ForbiddenException.class);

        verify(chatRoomMemberService, never())
                .addChatUser(any(ChatRoom.class), any(User.class));
    }

    @Test
    @DisplayName("여행 소유자가 아닌 채팅방 멤버의 여행 채팅방 삭제 거부")
    void deleteTravelChatRoomByNonOwnerForbidden() {

        // given
        Long roomId = 1L;
        Long userId = 10L;
        String username = "requester@example.com";

        DeleteChatRoomRequest request =
                mock(DeleteChatRoomRequest.class);

        User requester =
                mock(User.class);

        ChatRoom chatRoom =
                mock(ChatRoom.class);

        when(request.roomId())
                .thenReturn(roomId);

        when(userQueryService.findByUsername(username))
                .thenReturn(requester);

        when(chatRoomQueryService.findChatRoomByRoomId(roomId))
                .thenReturn(chatRoom);

        doThrow(new ForbiddenException(
                new Object[]{"해당 여행 채팅방에 대한 접근 권한이 없습니다."}
        ))
                .when(chatRoomMemberService)
                .validateTravelRoomOwner(chatRoom, requester);

        // when & then
        assertThatThrownBy(() ->
                chatFacade.deleteChatRoom(
                        request,
                        username
                ))
                .isInstanceOf(ForbiddenException.class);

        verify(chatRoomService, never())
                .deleteChatRoom(any());
    }
}
