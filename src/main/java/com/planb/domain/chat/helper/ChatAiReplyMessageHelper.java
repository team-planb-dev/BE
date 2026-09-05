package com.planb.domain.chat.helper;

import org.springframework.stereotype.Component;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;

@Component
public class ChatAiReplyMessageHelper {

    private static final String UNSUPPORTED_REQUEST_MESSAGE =
            "해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.";

    // 편집 미리보기 결과 기준 AI 응답 메시지 가공
    public String makeReplyMessage(EditPlanPreviewResponse preview){

        if (!preview.after().processable()) {
            return UNSUPPORTED_REQUEST_MESSAGE;
        }

        return String.join(
                "\n",
                preview.after().changes()
        );
    }
}
