package com.planb.global.client.kakaoMapService.dto.response;

/**
 * 카카오 경로 조회 실패의 단계·유형·공급자 응답과 실제 요청 좌표
 */
public record KakaoRouteFailure(
        Stage stage,
        Kind kind,
        Integer httpStatus,
        String providerCode,
        String providerMessage,

        // 원인 예외 클래스명, 메시지·stack trace 제외
        String exceptionType,
        String startX,
        String startY,
        String endX,
        String endY
) {

    // 실패가 발생한 조회 단계
    public enum Stage {
        POINT_SEARCH,
        ROUTE
    }

    // 실패 유형
    public enum Kind {
        HTTP_ERROR,
        NO_RESULT,
        INVALID_MINUTES,
        TIMEOUT,
        CONNECTION,
        DECODE,
        MISSING_POINT,
        OTHER
    }
}
