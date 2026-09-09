package com.planb.domain.user.dto.request;

import com.planb.domain.user.entity.constant.RecoveryQuestion;
import com.planb.global.validation.password.ValidPassword;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserCreateRequest(

        @NotBlank(message = "이메일은 필수 입니다.")
        @Email(message = "올바른 이메일 형식이 아닙니다.")
        String username,

        @NotBlank(message = "닉네임은 필수 입니다.")
        String nickname,

        @NotBlank(message = "비밀번호는 필수 입니다.")
        @ValidPassword
        String password,

        @NotNull(message = "계정 복구 질문은 필수 입니다.")
        RecoveryQuestion recoveryQuestion,

        @NotBlank(message = "계정 복구 답변은 필수 입니다.")
        String recoveryAnswer,

        boolean ageRequirementAgreed,
        boolean serviceTermsAgreed,
        boolean privacyCollectionAgreed) {
}
