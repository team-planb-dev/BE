package com.planb.unit.domain.chat.helper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import com.planb.ai.dto.response.EditPlanAiResponse;
import com.planb.domain.chat.helper.ChatAiReplyMessageHelper;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;

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

        assertThat(result).isEqualTo("일정 수정을 완료했어요!");
    }

    @Test
    @DisplayName("처리 불가능한 요청이면 고정 거절 메시지 반환")
    void makeReplyMessageWhenNotProcessable() {
        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse("부산 여행", List.of(), List.of(), false);
        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        String result = chatAiReplyMessageHelper.makeReplyMessage(preview);

        assertThat(result).isEqualTo("해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.");
    }

    @Test
    @DisplayName("CONFIRM 완료 고정 메시지 반환")
    void makeConfirmMessage() {

        String result = chatAiReplyMessageHelper.makeConfirmMessage();

        assertThat(result).isEqualTo("일정을 저장했어요!");
    }

    @Test
    @DisplayName("CANCEL 완료 고정 메시지 반환")
    void makeCancelMessage() {

        String result = chatAiReplyMessageHelper.makeCancelMessage();

        assertThat(result).isEqualTo("기존 일정을 유지했어요!");
    }

    @Test
    @DisplayName("사용자·AI 닉네임 기준 인사 메시지 2건 생성")
    void makeGreetingMessages() {

        String userNickname = "우주";
        String aiNickname = "AI 비서";

        List<String> result =
                chatAiReplyMessageHelper.makeGreetingMessages(userNickname, aiNickname);

        assertThat(result).containsExactly(
                "안녕하세요. " + userNickname + "님의 여행 일정을 계획해줄 " + aiNickname + "예요.",
                "일정을 어떻게 수정하고 싶나요?"
        );
    }
}
