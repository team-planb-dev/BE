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
import com.planb.domain.chat.dto.request.CreateChatRoomRequest;
import com.planb.domain.chat.dto.request.DeleteChatRoomRequest;
import com.planb.domain.chat.dto.response.CreateChatRoomResponse;
import com.planb.domain.chat.dto.response.DeleteChatRoomResponse;
import com.planb.domain.chat.facade.ChatFacade;
import com.planb.global.config.exception.dto.ApiResult;

@Tag(name="chatRoom",description = "채팅방 생성 및 삭제 API")
@RestController
@RequestMapping("/api/v1/chat/room")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatFacade chatFacade;

    // 채팅방 생성하기
    @Operation(summary = "채팅방 생성",
            description = "AccessToken을 검증한 뒤, 채팅방을 생성합니다.")
    @SecurityRequirement(name = "JWT")
    @PostMapping("/create")
    public ResponseEntity<ApiResult<CreateChatRoomResponse>> createChatRoom
    (@AuthenticationPrincipal UserDetails userDetails,
     @RequestBody CreateChatRoomRequest request){

        return ResponseEntity
                .status(HttpStatus
                        .CREATED)
                .body(ApiResult
                        .success(chatFacade
                                .createChatRoom(request)));
    }



    // 채팅방 삭제하기
    @Operation(summary = "채팅방 삭제",description = "AccessToken을 검증한 뒤, 채팅방을 삭제합니다.")
    @SecurityRequirement(name = "JWT")
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResult<DeleteChatRoomResponse>> deleteChatRoom
    (@AuthenticationPrincipal UserDetails userDetails,
     @RequestBody DeleteChatRoomRequest request){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(chatFacade
                                .deleteChatRoom(request)));
    }


}
