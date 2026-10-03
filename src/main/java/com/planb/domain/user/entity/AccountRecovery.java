package com.planb.domain.user.entity;

import com.planb.domain.user.entity.constant.RecoveryQuestion;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;

/**
 * 계정 복구 질문과 단방향 해시로 보관하는 답변
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class AccountRecovery {

    // 사용자가 선택한 계정 복구 질문
    @Enumerated(EnumType.STRING)
    @Column(name = "recovery_question", nullable = false, length = 40)
    private RecoveryQuestion recoveryQuestion;

    // 정규화한 답변의 해시
    @Column(name = "recovery_answer_hash", nullable = false, length = 64)
    private String recoveryAnswerHash;

    public static AccountRecovery of(
            RecoveryQuestion recoveryQuestion,
            String rawAnswer
    ) {

        return new AccountRecovery(
                recoveryQuestion,
                hashAnswer(rawAnswer)
        );
    }

    public boolean matches(
            RecoveryQuestion question,
            String rawAnswer
    ) {

        return recoveryQuestion == question
                && recoveryAnswerHash.equals(hashAnswer(rawAnswer));
    }

    // 앞뒤 공백·대소문자·유니코드 차이를 정규화한 복구 답변 해시
    // ponytail: 짧은 답변의 사전 공격에 약한 pepper 없는 SHA-256
    // 답변 단독 로그인 불가와 비밀번호 재설정 시 이메일 추가 요구
    // 복구 수단 확대 시 pepper 또는 추가 인증 단계 도입
    public static String hashAnswer(String rawAnswer) {

        String normalized = Normalizer
                .normalize(
                        rawAnswer == null ? "" : rawAnswer.trim(),
                        Normalizer.Form.NFKC
                )
                .toLowerCase();

        try {
            return HexFormat
                    .of()
                    .formatHex(MessageDigest
                            .getInstance("SHA-256")
                            .digest(normalized.getBytes(StandardCharsets.UTF_8)));

        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", e);
        }
    }
}
