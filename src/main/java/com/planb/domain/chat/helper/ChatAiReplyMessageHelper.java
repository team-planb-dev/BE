package com.planb.domain.chat.helper;

import org.springframework.stereotype.Component;
import com.planb.domain.travel.dto.response.EditPlanPreviewResponse;

import java.util.List;

@Component
public class ChatAiReplyMessageHelper {

    private static final String UNSUPPORTED_REQUEST_MESSAGE =
            "해당 요청은 처리하기 어렵습니다! 다른 요청 부탁드려요.";

    private static final String EDIT_COMPLETED_MESSAGE =
            "일정 수정을 완료했어요!";

    private static final String CONFIRM_COMPLETED_MESSAGE =
            "일정을 저장했어요!";

    private static final String CANCEL_COMPLETED_MESSAGE =
            "기존 일정을 유지했어요!";

    private static final String GREETING_QUESTION_MESSAGE =
            "일정을 어떻게 수정하고 싶나요?";

    private static final String EDIT_FAILED_MESSAGE =
            "일정 수정 중 문제가 발생했어요. 잠시 후 다시 시도해 주세요.";

    // 편집 미리보기 결과 기준 AI 응답 메시지 가공
    public String makeReplyMessage(EditPlanPreviewResponse preview){

        if (!preview.after().processable()) {
            return UNSUPPORTED_REQUEST_MESSAGE;
        }

        return EDIT_COMPLETED_MESSAGE;
    }

    // 일정 수정 처리 실패 안내 메시지 생성
    // 실패 사유는 로그에만 남기고 사용자에게는 고정 문구만 보낸다.
    public String makeEditFailedMessage(){

        return EDIT_FAILED_MESSAGE;
    }

    // 수정 확정(CONFIRM) 완료 메시지 생성
    public String makeConfirmMessage(){

        return CONFIRM_COMPLETED_MESSAGE;
    }

    // 수정 취소(CANCEL) 완료 메시지 생성
    public String makeCancelMessage(){

        return CANCEL_COMPLETED_MESSAGE;
    }

    // 채팅방 입장 시 AI 인사 메시지 목록 생성
    public List<String> makeGreetingMessages(String userNickname, String aiNickname){

        String introMessage =
                "안녕하세요. " + userNickname + "님의 여행 일정을 계획해줄 "
                        + aiNickname + "예요.";

        return List.of(introMessage, GREETING_QUESTION_MESSAGE);
    }
}
