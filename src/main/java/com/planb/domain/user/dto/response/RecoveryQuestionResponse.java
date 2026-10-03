package com.planb.domain.user.dto.response;

import com.planb.domain.user.entity.constant.RecoveryQuestion;

import java.util.Arrays;
import java.util.List;

/**
 * 계정 복구 질문 선택 목록의 질문 코드와 문구
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
