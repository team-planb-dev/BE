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
 * 이메일 찾기와 비밀번호 재설정에 사용하는 계정 복구 질문과 답변.
 *
 * 답변은 이메일 찾기에서 질문과 함께 조회 조건이 되므로 단방향 해시로 보관한다.
 * BCrypt는 저장할 때마다 salt가 달라 1:N 조회를 할 수 없어 사용하지 않는다.
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

    // 답변은 사람이 입력하므로 앞뒤 공백, 대소문자, 유니코드 표기 차이를 흡수한 뒤 해시한다.
    // ponytail: pepper 없는 SHA-256이라 답변이 짧으면 사전 공격에 약하다.
    // 답변만으로는 로그인할 수 없고 비밀번호 재설정에는 이메일이 추가로 필요하다.
    // 복구 수단을 늘릴 때 pepper나 별도 인증 단계를 함께 올린다.
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
