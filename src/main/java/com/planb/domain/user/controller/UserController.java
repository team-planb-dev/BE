package com.planb.domain.user.controller;

import com.planb.domain.user.dto.request.CheckNicknameDuplicationRequest;
import com.planb.domain.user.dto.request.CheckUsernameDuplicationRequest;
import com.planb.domain.user.dto.response.CheckNicknameDuplicationResponse;
import com.planb.domain.user.dto.response.CheckUsernameDuplicationResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import com.planb.domain.user.dto.request.UserCreateRequest;
import com.planb.domain.user.dto.response.UserCreateResponse;
import com.planb.domain.user.dto.response.UserDeleteResponse;
import com.planb.domain.user.facade.UserFacade;
import com.planb.global.config.exception.dto.ApiResult;
import com.planb.global.security.dto.UserAuthCache;

@Tag(name = "user",description = "유저 API")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade userFacade;

    @Operation(summary = "유저 생성",description = "유저를 생성합니다.")
    @PostMapping("/create")
    public ResponseEntity<ApiResult<UserCreateResponse>> create
            (@Valid @RequestBody UserCreateRequest userCreateRequest){

        return ResponseEntity
                .status(HttpStatus
                        .CREATED)
                .body(ApiResult
                        .success(userFacade
                                .create(userCreateRequest)));
    }

    @Operation(summary = "유저 조회",description = "해당 유저를 조회합니다.이때 , RDB가 아닌 Redis Cache에서 조회를 진행합니다.")
    @GetMapping("/me")
    public ResponseEntity<ApiResult<UserAuthCache>> read
            (@AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .findByUsername(userDetails
                                        .getUsername())));

    }

    @Operation(summary = "유저 삭제",description = "해당 유저를 삭제합니다.")
    @DeleteMapping("/delete")
    public ResponseEntity<ApiResult<UserDeleteResponse>> delete
            (@AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .delete(userDetails
                                        .getUsername())));

    }

    @Operation(summary = "username(email) 중복 조회",description = "id로 사용되는 email의 중복을 체크합니다.")
    @GetMapping("/check/duplication/username")
    public ResponseEntity<ApiResult<CheckUsernameDuplicationResponse>> checkUsernameDuplication
            (@RequestBody CheckUsernameDuplicationRequest checkUsernameDuplicationRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .checkUsernameDuplication(checkUsernameDuplicationRequest)));
    }

    @Operation(summary = "nickname 중복 조회",description = "기존 nickname과의 중복 여부를 검사합니다.")
    @GetMapping("/check/duplication/nickname")
    public ResponseEntity<ApiResult<CheckNicknameDuplicationResponse>> checkNicknameDuplication
            (@RequestBody CheckNicknameDuplicationRequest checkNicknameDuplicationRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .checkNicknameDuplication(checkNicknameDuplicationRequest)));
    }


}
