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
import io.swagger.v3.oas.annotations.responses.ApiResponse;
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

@Tag(name = "사용자 API", description = "회원가입, 사용자 조회, 계정 복구를 처리합니다.")
@RestController
@RequestMapping("/api/v1/user")
@RequiredArgsConstructor
public class UserController {

    private final UserFacade userFacade;

    @Operation(
            summary = "회원가입",
            description = """
                    이메일, 닉네임, 비밀번호, 계정 복구 정보와 약관 동의 여부를 등록합니다.
                    이메일과 닉네임 중복 여부는 중복 확인 API로 먼저 확인해 주세요.
                    요청값 검증에 실패하면 `BASE.EXCEPTION.EXCEPTION_VALIDATION`을 반환합니다.
                    """,
            responses = @ApiResponse(
                    responseCode = "201",
                    description = "회원가입 성공",
                    useReturnTypeSchema = true
            )
    )
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

    @Operation(
            summary = "내 인증 정보 조회",
            description = "로그인한 사용자의 ID, 이메일과 권한 정보를 반환합니다. Access Token을 전달해 주세요."
    )
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

    @Operation(
            summary = "회원 탈퇴",
            description = "로그인한 사용자를 탈퇴 상태로 변경하고 인증 캐시와 Refresh Token을 삭제합니다. 탈퇴 후 기존 인증 정보는 사용할 수 없습니다."
    )
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

    @Operation(
            summary = "이메일 중복 확인",
            description = "회원가입 ID로 사용할 이메일을 요청 본문에 전달해 주세요. `duplicate`로 중복 여부를 반환합니다."
    )
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

    @Operation(
            summary = "닉네임 중복 확인",
            description = "사용할 닉네임을 요청 본문에 전달해 주세요. `duplicate`로 중복 여부를 반환합니다."
    )
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
            description = "회원가입과 계정 복구 화면에서 사용할 질문 코드와 표시 문구를 반환합니다.")
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
            description = "가입할 때 등록한 계정 복구 질문과 답변을 전달해 주세요. 이메일은 마스킹된 값으로 반환됩니다.")
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
            description = "이메일과 계정 복구 질문 및 답변을 확인한 뒤 비밀번호를 재설정합니다. 재설정 후 기존 로그인 세션은 모두 만료됩니다.")
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
