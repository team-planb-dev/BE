package com.planb.domain.user.dto.response;

public record CheckNicknameDuplicationResponse(boolean duplicate,
                                               String message) {

    public static CheckNicknameDuplicationResponse result(boolean result) {
        return new CheckNicknameDuplicationResponse(
                result,
                result ? "이미 존재하는 닉네임 입니다." : "사용 가능한 닉네임 입니다."
        );
    }
}
