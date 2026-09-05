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
    @DisplayName("처리 가능한 요청이면 변경 사항을 줄바꿈으로 이어붙여 반환")
    void makeReplyMessageWhenProcessable() {
        EditPlanAiResponse editPlanAiResponse =
                new EditPlanAiResponse("부산 여행", List.of(),
                        List.of("1일차 카페 변경", "2일차 관광지 추가"), true);
        EditPlanPreviewResponse preview =
                new EditPlanPreviewResponse(null, editPlanAiResponse);

        String result = chatAiReplyMessageHelper.makeReplyMessage(preview);

        assertThat(result).isEqualTo("1일차 카페 변경\n2일차 관광지 추가");
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
}
