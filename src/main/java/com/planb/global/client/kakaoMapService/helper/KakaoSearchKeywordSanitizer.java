package com.planb.global.client.kakaoMapService.helper;

import org.springframework.stereotype.Component;

@Component
public class KakaoSearchKeywordSanitizer {

    // 카카오맵 키워드 검색이 인식하지 못하는 괄호 부가 설명을 제거
    // 예) "해운대 그린레일웨이 (미포~송정 구간)" -> "해운대 그린레일웨이"
    public String sanitize(String keyword) {
        if (keyword == null) {
            return null;
        }

        String sanitized = keyword.replaceAll("\\s*\\([^)]*\\)\\s*", " ").trim();

        return sanitized.isEmpty() ? keyword : sanitized;
    }
}