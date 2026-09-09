package com.planb.domain.user.controller;

import com.planb.domain.user.dto.request.CheckNicknameDuplicationRequest;
import com.planb.domain.user.dto.request.CheckUsernameDuplicationRequest;
import com.planb.domain.user.dto.request.FindUsernameRequest;
import com.planb.domain.user.dto.request.ResetPasswordRequest;
import com.planb.domain.user.dto.response.CheckNicknameDuplicationResponse;
import com.planb.domain.user.dto.response.CheckUsernameDuplicationResponse;
import com.planb.domain.user.dto.response.FindUsernameResponse;
import com.planb.domain.user.dto.response.RecoveryQuestionResponse;
import com.planb.domain.user.dto.response.ResetPasswordResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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

import java.util.List;

@Tag(name = "user", description = "유저 API")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade userFacade;

    @Operation(summary = "유저 생성", description = "유저를 생성합니다.")
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

    @Operation(summary = "유저 조회", description = "해당 유저를 조회합니다.이때 , RDB가 아닌 Redis Cache에서 조회를 진행합니다.")
    @SecurityRequirement(name = "JWT")
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

    @Operation(summary = "유저 삭제", description = "해당 유저를 삭제합니다.")
    @SecurityRequirement(name = "JWT")
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

    @Operation(summary = "username(email) 중복 조회", description = "id로 사용되는 email의 중복을 체크합니다.")
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

    @Operation(summary = "nickname 중복 조회", description = "기존 nickname과의 중복 여부를 검사합니다.")
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

    @Operation(summary = "계정 복구 질문 목록 조회",
            description = "회원가입과 계정 찾기 화면에서 선택할 수 있는 복구 질문 목록을 조회합니다.")
    @GetMapping("/recovery/questions")
    public ResponseEntity<ApiResult<List<RecoveryQuestionResponse>>> readRecoveryQuestions(){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .findRecoveryQuestions()));
    }

    @Operation(summary = "이메일 찾기",
            description = "가입할 때 등록한 계정 복구 질문과 답변으로 이메일을 찾습니다. 이메일은 마스킹되어 반환됩니다.")
    @PostMapping("/recovery/username")
    public ResponseEntity<ApiResult<FindUsernameResponse>> findUsername
            (@Valid @RequestBody FindUsernameRequest findUsernameRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .findUsername(findUsernameRequest)));
    }

    @Operation(summary = "비밀번호 재설정",
            description = "이메일과 계정 복구 질문/답변을 확인한 뒤 비밀번호를 재설정합니다. 재설정 후 기존 로그인 세션은 모두 만료됩니다.")
    @PatchMapping("/recovery/password")
    public ResponseEntity<ApiResult<ResetPasswordResponse>> resetPassword
            (@Valid @RequestBody ResetPasswordRequest resetPasswordRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(userFacade
                                .resetPassword(resetPasswordRequest)));
    }
}
