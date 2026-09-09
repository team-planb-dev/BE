package com.planb.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.planb.domain.chat.dto.request.AddChatRoomMemberRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomMemberRequest;
import com.planb.domain.chat.dto.response.AddChatUserResponse;
import com.planb.domain.chat.dto.response.DeleteChatUserResponse;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.global.config.exception.dto.ApiResult;

@Tag(name = "채팅방 멤버 API", description = "일반 채팅방의 사용자 참여와 퇴장을 관리합니다.")
@RestController
@RequestMapping("/api/v1/chat/member")
@RequiredArgsConstructor
public class ChatRoomMemberController {

    private final ChatFacade chatFacade;

    @Operation(
            summary = "채팅방 멤버 추가",
            description = """
                    로그인한 사용자를 일반 채팅방 멤버로 등록합니다.
                    요청 본문의 `userId`는 로그인한 사용자의 ID와 같아야 합니다.
                    여행 채팅방은 여행 소유자만 등록할 수 있으며 중복 등록은 거부됩니다.
                    """
    )
    @PostMapping("/add")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<AddChatUserResponse>> addMember
            (@AuthenticationPrincipal UserDetails userDetails,
             @RequestBody AddChatRoomMemberRequest addChatUserRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(chatFacade
                                .addChatUser(
                                        addChatUserRequest,
                                        userDetails.getUsername()
                                )));
    }

    @Operation(
            summary = "채팅방 멤버 삭제",
            description = """
                    로그인한 사용자를 채팅방 멤버에서 삭제합니다.
                    요청 본문의 `userId`는 로그인한 사용자의 ID와 같아야 하며,
                    해당 채팅방에 등록된 멤버만 삭제할 수 있습니다.
                    """
    )
    @DeleteMapping("/delete")
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<DeleteChatUserResponse>> deleteMember
            (@AuthenticationPrincipal UserDetails userDetails,
             @RequestBody DeleteChatRoomMemberRequest deleteChatRoomMemberRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(chatFacade
                                .deleteChatUser(
                                        deleteChatRoomMemberRequest,
                                        userDetails.getUsername()
                                )));
    }
}
