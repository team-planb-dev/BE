package com.planb.domain.user.dto.request;

import com.planb.domain.user.entity.constant.RecoveryQuestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 닉네임과 복구 질문·답변을 이용한 이메일 찾기 요청
 */
public record FindUsernameRequest(

        @NotBlank(message = "닉네임은 필수 입니다.")
        String nickname,

        @NotNull(message = "계정 복구 질문은 필수 입니다.")
        RecoveryQuestion recoveryQuestion,

        @NotBlank(message = "계정 복구 답변은 필수 입니다.")
        String recoveryAnswer) {
}
