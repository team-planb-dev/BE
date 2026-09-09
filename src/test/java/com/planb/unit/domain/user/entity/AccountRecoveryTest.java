package com.planb.unit.domain.user.entity;

import com.planb.domain.user.dto.response.FindUsernameResponse;
import com.planb.domain.user.entity.AccountRecovery;
import com.planb.domain.user.entity.constant.RecoveryQuestion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountRecoveryTest {

    @Test
    @DisplayName("답변의 앞뒤 공백과 대소문자 차이 무시 후 일치 판정")
    void matchesIgnoringWhitespaceAndCase() {

        AccountRecovery accountRecovery = AccountRecovery
                .of(RecoveryQuestion.FIRST_PET, "Kongi");

        assertThat(accountRecovery
                .matches(RecoveryQuestion.FIRST_PET, "  kongi  "))
                .isTrue();
    }

    @Test
    @DisplayName("질문이 다른 경우 동일 답변 불일치")
    void doesNotMatchWhenQuestionDiffers() {

        AccountRecovery accountRecovery = AccountRecovery
                .of(RecoveryQuestion.FIRST_PET, "콩이");

        assertThat(accountRecovery
                .matches(RecoveryQuestion.CHILDHOOD_NICKNAME, "콩이"))
                .isFalse();
    }

    @Test
    @DisplayName("답변 해시의 64자리 16진수 형식")
    void hashesAnswerToHex() {

        assertThat(AccountRecovery
                .hashAnswer("콩이"))
                .hasSize(64)
                .matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("이메일 찾기 응답의 아이디 앞 두 글자만 노출")
    void masksUsername() {

        assertThat(FindUsernameResponse
                .of("yeonwoo@gmail.com")
                .maskedUsername())
                .isEqualTo("ye***@gmail.com");
    }
}
