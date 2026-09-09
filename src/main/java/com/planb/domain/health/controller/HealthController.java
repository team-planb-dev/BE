package com.planb.domain.health.controller;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.DeleteCompanionRequest;
import com.planb.domain.health.dto.request.UpdateCompanionRequest;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionDetailResponse;
import com.planb.domain.health.dto.response.CompanionSummaryResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.dto.response.UpdateCompanionResponse;
import com.planb.domain.health.facade.HealthFacade;
import com.planb.global.config.exception.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Tag(name = "동행인 API", description = "여행 구성원의 건강, 식사, 음식 제한과 복약 정보를 관리합니다.")
public class HealthController {

    private final HealthFacade healthFacade;

    // 동행자를 한번에 등록
    @PostMapping("/add-traveler")
    @Operation(
            summary = "동행인 등록",
            description = """
                    로그인한 사용자의 여행 구성원을 등록합니다.
                    민감정보 동의 여부와 건강, 식사, 음식 제한 및 복약 정보를 함께 전달해 주세요.
                    등록 후 동행인 목록 요약 조회에서 여행 생성에 사용할 `healthId`를 확인해 주세요.
                    """
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<AddCompanionResponse>> addTraveler
            (@AuthenticationPrincipal UserDetails userDetails,
             @RequestBody AddCompanionRequest addCompanionRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(healthFacade
                                .addCompanion(
                                        addCompanionRequest,
                                        userDetails.getUsername())));
    }

    // 단일 동행자 수정 메소드
    @PutMapping("/update-companion")
    @Operation(
            summary = "동행인 수정",
            description = """
                    로그인한 사용자가 소유한 동행인의 정보를 수정합니다.
                    건강, 식사, 음식 제한과 복약 정보는 요청값으로 덮어쓰며,
                    음식과 복약 목록은 전달한 목록으로 전체 교체됩니다.
                    """
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<UpdateCompanionResponse>> updateCompanion
            (@AuthenticationPrincipal UserDetails userDetails,
             @RequestBody UpdateCompanionRequest updateCompanionRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(healthFacade
                                .updateCompanion(
                                        updateCompanionRequest,
                                        userDetails
                                                .getUsername())));
    }

    // 단일 동행자 삭제 메소드
    @DeleteMapping("/delete-companion")
    @Operation(
            summary = "동행인 삭제",
            description = "로그인한 사용자가 소유한 `healthId`의 동행인을 삭제합니다. 대상이 없으면 `HEALTH.EXCEPTION.HEALTH_NOUT_FOUND`를 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<DeleteCompanionResponse>> deleteCompanion
    (@AuthenticationPrincipal UserDetails userDetails,
     @RequestBody DeleteCompanionRequest deleteCompanionRequest){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(healthFacade
                                .deleteCompanion(
                                        deleteCompanionRequest,
                                        userDetails
                                                .getUsername())));
    }


    // 단일 동행자 조회 메소드
    @GetMapping("/get-companion-summary")
    @Operation(
            summary = "동행인 목록 요약 조회",
            description = "로그인한 사용자가 등록한 동행인의 이름, 질환, 복약 및 알레르기 여부를 목록 형태로 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<CompanionSummaryResponse>> getCompanion
    (@AuthenticationPrincipal UserDetails userDetails){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(healthFacade
                                .getCompanionSummary(
                                        userDetails
                                                .getUsername())));
    }

    // 단일 동행자 상세 조회 메소드
    @GetMapping("/get-companion-detail")
    @Operation(
            summary = "동행인 상세 조회",
            description = "수정할 동행인의 `healthId`를 전달해 주세요. 건강, 식사, 음식 제한과 복약 정보를 함께 반환합니다."
    )
    @SecurityRequirement(name = "JWT")
    public ResponseEntity<ApiResult<CompanionDetailResponse>> getCompanionDetail
            (@AuthenticationPrincipal UserDetails userDetails,
             @Parameter(description = "조회할 동행인 ID", example = "1")
             @RequestParam Long healthId){

        return ResponseEntity
                .status(HttpStatus
                        .OK)
                .body(ApiResult
                        .success(healthFacade
                                .getCompanionDetail(
                                        healthId,
                                        userDetails
                                                .getUsername())));
    }
}
