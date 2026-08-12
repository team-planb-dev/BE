package com.planb.domain.user.dto.response;

public record CheckUsernameDuplicationResponse(boolean duplicate,String message) {

    public static CheckUsernameDuplicationResponse result(boolean result) {
        return new CheckUsernameDuplicationResponse(
                result,
                result ? "이미 존재하는 이메일 입니다." : "사용 가능한 이메일 입니다."
        );
    }
}
