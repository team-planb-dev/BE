package com.planb.unit.domain.chat.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.chat.helper.ChatAiReplyMessageHelper;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;
import com.planb.global.config.exception.AiExceptionEnum;
import com.planb.global.config.exception.AiFailure;
import com.planb.global.config.exception.PlanEditExceptionEnum;
import com.planb.global.config.exception.domain.AiOrchestrationException;
import com.planb.global.config.exception.domain.BaseException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatAiReplyMessageHelperTest {

    private final ChatAiReplyMessageHelper chatAiReplyMessageHelper =
            new ChatAiReplyMessageHelper();

    @Test
    @DisplayName("처리 가능한 요청이면 고정 완료 메시지 반환")
    void makeReplyMessageWhenProcessable() {
        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse("부산 여행", List.of(),
                        List.of("1일차 카페 변경", "2일차 관광지 추가"), true);
        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        String result = chatAiReplyMessageHelper.makeReplyMessage(preview);

        assertThat(result)
                .isEqualTo("일정 수정을 완료했어요!");
    }

    @Test
    @DisplayName("처리 불가능한 요청이면 고정 거절 메시지 반환")
    void makeReplyMessageWhenNotProcessable() {
        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse("부산 여행", List.of(), List.of(), false);
        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        String result = chatAiReplyMessageHelper.makeReplyMessage(preview);

        assertThat(result)
                .isEqualTo("해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.");
    }

    @Test
    @DisplayName("일시적인 AI 편집 실패의 재시도 안내 메시지")
    void makeEditFailedMessageWhenAiTemporarilyUnavailable() {

        String result =
                chatAiReplyMessageHelper.makeEditFailedMessage(
                        new AiOrchestrationException(
                                AiFailure.UPSTREAM_CALL_FAILED
                        )
                );

        assertThat(result)
                .isEqualTo(
                        AiExceptionEnum.AI_TEMPORARILY_UNAVAILABLE
                                .getMessage()
                );
    }

    @Test
    @DisplayName("AI 응답 검증 실패의 결과 생성 실패 안내 메시지")
    void makeEditFailedMessageWhenAiResponseRejected() {

        String result =
                chatAiReplyMessageHelper.makeEditFailedMessage(
                        new AiOrchestrationException(
                                AiFailure.RESPONSE_INVALID
                        )
                );

        assertThat(result)
                .isEqualTo(
                        AiExceptionEnum.AI_RESPONSE_REJECTED
                                .getMessage()
                );
    }

    @Test
    @DisplayName("일정 변경 검증 실패의 안전한 안내 메시지")
    void makeEditFailedMessageWhenEditNotApplied() {

        String result =
                chatAiReplyMessageHelper.makeEditFailedMessage(
                        new BaseException(
                                PlanEditExceptionEnum.EDIT_NOT_APPLIED,
                                new Object[]{"노출하면 안 되는 내부 검증 사유"}
                        )
                );

        assertThat(result)
                .isEqualTo(
                        AiExceptionEnum.AI_RESPONSE_REJECTED
                                .getMessage()
                )
                .doesNotContain("내부 검증 사유");
    }

    @Test
    @DisplayName("만료된 수정 결과의 재요청 안내 메시지")
    void makeEditFailedMessageWhenEditResultExpired() {

        String result =
                chatAiReplyMessageHelper.makeEditFailedMessage(
                        new BaseException(
                                PlanEditExceptionEnum.EDIT_RESULT_NOT_FOUND
                        )
                );

        assertThat(result)
                .isEqualTo(
                        "수정 결과가 만료되었어요. 일정을 다시 수정해 주세요."
                );
    }

    @Test
    @DisplayName("알 수 없는 편집 실패의 공통 안내 메시지")
    void makeEditFailedMessageWhenUnknownFailure() {

        String result =
                chatAiReplyMessageHelper.makeEditFailedMessage(
                        new RuntimeException("노출하면 안 되는 내부 오류")
                );

        assertThat(result)
                .isEqualTo(
                        "일정 수정 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요."
                )
                .doesNotContain("내부 오류");
    }

    @Test
    @DisplayName("CONFIRM 완료 고정 메시지 반환")
    void makeConfirmMessage() {

        String result = chatAiReplyMessageHelper.makeConfirmMessage();

        assertThat(result)
                .isEqualTo("일정을 저장했어요!");
    }

    @Test
    @DisplayName("CANCEL 완료 고정 메시지 반환")
    void makeCancelMessage() {

        String result = chatAiReplyMessageHelper.makeCancelMessage();

        assertThat(result)
                .isEqualTo("기존 일정을 유지했어요!");
    }

    @Test
    @DisplayName("사용자·AI 닉네임 기준 인사 메시지 2건 생성")
    void makeGreetingMessages() {

        String userNickname = "우주";
        String aiNickname = "AI 비서";

        List<String> result =
                chatAiReplyMessageHelper.makeGreetingMessages(userNickname, aiNickname);

        assertThat(result)
                .containsExactly(
                "안녕하세요. " + userNickname + "님의 여행 일정을 계획해줄 " + aiNickname + "예요.",
                "일정을 어떻게 수정하고 싶나요?"
        );
    }
}
