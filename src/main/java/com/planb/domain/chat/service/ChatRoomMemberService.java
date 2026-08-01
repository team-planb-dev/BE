package com.planb.domain.chat.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.planb.domain.chat.dto.request.AddChatUserRequest;
import com.planb.domain.chat.dto.request.DeleteChatUserRequest;
import com.planb.domain.chat.dto.response.AddChatUserResponse;
import com.planb.domain.chat.dto.response.DeleteChatUserResponse;
import com.planb.domain.chat.entity.ChatRoomMember;
import com.planb.domain.chat.repository.ChatRoomMemberRepository;

@Service
@RequiredArgsConstructor
public class ChatRoomMemberService {

    private final ChatRoomMemberRepository chatRoomMemberRepository;

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
