package com.planb.domain.health.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeleteCompanionRequest(
        @Schema(description = "삭제할 동행인 ID", example = "1")
        Long healthId
) {
}
