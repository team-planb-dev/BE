package com.planb.domain.chat.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "채팅방 API", description = "일반 채팅방과 여행 일정에 연결된 채팅방을 관리합니다.")
@RestController
@RequestMapping("/api/v1/chat/room")
@RequiredArgsConstructor
public class ChatRoomController {

    private final ChatFacade chatFacade;

    // 채팅방 생성하기
    @Operation(
            summary = "일반 채팅방 생성",
            description = "채팅방 이름을 전달해 일반 채팅방을 생성합니다. 생성 결과로 채팅방 ID와 생성 시각을 반환합니다.",
            responses = @ApiResponse(
                    responseCode = "201",
                    description = "채팅방 생성 성공",
                    useReturnTypeSchema = true
            )
    )
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


    // travel 기준으로 채팅방 조회/생성하기
    @Operation(
            summary = "여행 채팅방 조회 또는 생성",
            description = """
                    로그인한 사용자가 소유한 여행 ID를 전달해 주세요.
                    해당 여행에 연결된 채팅방이 있으면 기존 채팅방을 반환하고, 없으면 생성합니다.
                    요청 사용자는 채팅방 멤버로 등록되며 첫 입장 시 AI 안내 메시지가 발행될 수 있습니다.
                    여행 소유자가 아니면 HTTP 403으로 응답합니다.
                    """
    )
    @SecurityRequirement(name = "JWT")
    @GetMapping("/travel/{travelId}")
    public ResponseEntity<ApiResult<CreateChatRoomResponse>> findOrCreateTravelChatRoom
    (@AuthenticationPrincipal UserDetails userDetails,
     @Parameter(description = "채팅방을 조회하거나 생성할 여행 ID", example = "1")
     @PathVariable Long travelId){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(chatFacade
                                .findOrCreateTravelChatRoom(
                                        travelId,
                                        userDetails.getUsername())));
    }


    // 채팅방 삭제하기
    @Operation(
            summary = "채팅방 삭제",
            description = "로그인한 사용자가 참여 중인 채팅방 ID를 전달해 주세요. 멤버 관계와 채팅방을 삭제하고 메시지는 삭제 보관 처리합니다."
    )
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
                                .deleteChatRoom(
                                        request,
                                        userDetails.getUsername()
                                )));
    }


}
