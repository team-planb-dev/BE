package com.planb.domain.health.controller;

import com.planb.domain.health.dto.request.AddCompanionRequest;
import com.planb.domain.health.dto.request.DeleteCompanionRequest;
import com.planb.domain.health.dto.response.AddCompanionResponse;
import com.planb.domain.health.dto.response.CompanionSummaryResponse;
import com.planb.domain.health.dto.response.DeleteCompanionResponse;
import com.planb.domain.health.facade.HealthFacade;
import com.planb.global.config.exception.dto.ApiResult;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "동행인 API",description = "여행에 함께할 동행인을 관리하는 API")
public class HealthController {

    private final HealthFacade healthFacade;

    // 동행자를 한번에 등록
    @PostMapping("/add-traveler")
    @Operation(summary = "동행자 등록",description = "동행자를 등록합니다.")
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

    // 단일 동행자 삭제 메소드
    @DeleteMapping("/delete-companion")
    @Operation(summary = "동행자 삭제",description = "동행자를 삭제합니다.")
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
    @Operation(summary = "단일 동행자 간단 조회",description = "동행자 리스트에 명시되는 바 형태의 정보를 조회합니다.")
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
}
