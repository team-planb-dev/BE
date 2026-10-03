package com.planb.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.chat.dto.request.AddChatUserRequest;
import com.planb.domain.chat.dto.request.DeleteChatUserRequest;
import com.planb.domain.chat.dto.response.AddChatUserResponse;
import com.planb.domain.chat.dto.response.DeleteChatUserResponse;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.chat.repository.ChatRoomMemberRepository;
import com.planb.domain.chat.entity.ChatRoom;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.user.entity.User;
import com.planb.global.config.exception.domain.ForbiddenException;
import com.planb.query.chat.service.ChatRoomMemberQueryService;

@Service
@RequiredArgsConstructor
public class ChatRoomMemberService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final ChatRoomMemberQueryService chatRoomMemberQueryService;

    public void validateRequestedUser(
            Long requestedUserId,
            User authenticatedUser
    ) {

        if (!authenticatedUser.getId().equals(requestedUserId)) {
            throw new ForbiddenException(
                    new Object[]{"다른 사용자의 채팅방 멤버십을 변경할 수 없습니다."}
            );
        }
    }

    public void validateTravelRoomOwner(
            ChatRoom chatRoom,
            User authenticatedUser
    ) {

        Travel travel = chatRoom.getTravel();

        if (travel == null) {
            return;
        }

        if (!travel.getUser().getId().equals(authenticatedUser.getId())) {
            throw new ForbiddenException(
                    new Object[]{"해당 여행 채팅방에 대한 접근 권한이 없습니다."}
            );
        }
    }

    public void ensureMember(
            ChatRoom chatRoom,
            User user
    ) {

        if (chatRoomMemberQueryService.checkSubscriberWithRoomId(
                chatRoom.getId(),
                user.getId()
        )) {
            return;
        }

        addChatUser(new AddChatUserRequest(chatRoom, user));
    }

    public void validateRoomMember(
            Long roomId,
            User user
    ) {

        if (!chatRoomMemberQueryService.checkSubscriberWithRoomId(roomId, user.getId())) {
            throw new ForbiddenException(
                    new Object[]{"해당 채팅방에 대한 접근 권한이 없습니다."}
            );
        }
    }

    public AddChatUserResponse addChatUser(
            ChatRoom chatRoom,
            User user
    ) {

        return addChatUser(new AddChatUserRequest(chatRoom, user));
    }

    public DeleteChatUserResponse deleteChatUser(
            ChatRoomMember chatRoomMember,
            ChatRoom chatRoom,
            User user
    ) {

        return deleteChatUser(new DeleteChatUserRequest(chatRoomMember, chatRoom, user));
    }

    // 채팅방 인원 추가
    public AddChatUserResponse addChatUser(AddChatUserRequest request){

        ChatRoomMember chatRoomMember = ChatRoomMember
                .builder()
                .chatRoom(request.chatRoom())
                .user(request.user())
                .build();

        saveChatRoomMember(chatRoomMember);

        String username = request
                .user()
                .getUsername();

        return new AddChatUserResponse(
                username,
                request
                        .chatRoom()
                        .getId(),
                username+"님이 채팅방에 입장하셨습니다.");

    }

    // 채팅방 인원 삭제
    public DeleteChatUserResponse deleteChatUser
    (DeleteChatUserRequest request){

        chatRoomMemberRepository
                .delete(request.chatRoomMember());

        return new DeleteChatUserResponse(request.chatRoom().getId(),
                request.user().getUsername(),
                request.user().getUsername() + "님이 퇴장하셨습니다.");
    }

    private void saveChatRoomMember(ChatRoomMember chatRoomMember){
        chatRoomMemberRepository.save(chatRoomMember);
    }


}
