package com.planb.domain.user.dto.response;

import com.planb.domain.user.entity.constant.RecoveryQuestion;

import java.util.Arrays;
import java.util.List;

/**
 * 계정 복구 질문 선택 목록의 단일 항목.
 *
 * @param code     서버에 다시 전달할 질문 코드
 * @param question 사용자에게 보여줄 질문 문구
 */
public record RecoveryQuestionResponse(

        RecoveryQuestion code,
        String question
) {

    public static List<RecoveryQuestionResponse> all() {

        return Arrays
                .stream(RecoveryQuestion.values())
                .map(recoveryQuestion -> new RecoveryQuestionResponse(
                        recoveryQuestion,
                        recoveryQuestion.getQuestion()
                ))
                .toList();
    }
}
