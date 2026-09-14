package com.planb.domain.user.dto.request;

import com.planb.domain.user.entity.constant.RecoveryQuestion;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 이메일 찾기 요청.
 *
 * 복구 질문과 답변만으로는 계정을 하나로 좁힐 수 없다. 흔한 답변은 여러 계정에 걸린다.
 * 닉네임은 계정마다 유일하므로 계정을 특정하고, 질문과 답변은 본인 확인에 쓴다.
 */
public record FindUsernameRequest(

        @NotBlank(message = "닉네임은 필수 입니다.")
        String nickname,

        @NotNull(message = "계정 복구 질문은 필수 입니다.")
        RecoveryQuestion recoveryQuestion,

        @NotBlank(message = "계정 복구 답변은 필수 입니다.")
        String recoveryAnswer) {
}
