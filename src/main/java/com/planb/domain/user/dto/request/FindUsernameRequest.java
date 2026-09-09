package com.planb.domain.user.dto.request;

import com.planb.domain.user.entity.constant.RecoveryQuestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record FindUsernameRequest(

        @NotNull(message = "계정 복구 질문은 필수 입니다.")
        RecoveryQuestion recoveryQuestion,

        @NotBlank(message = "계정 복구 답변은 필수 입니다.")
        String recoveryAnswer) {
}
