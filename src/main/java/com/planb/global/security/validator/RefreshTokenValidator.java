package com.planb.global.security.validator;

import io.micrometer.common.util.StringUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.global.security.util.JwtUtil;

@Component
@RequiredArgsConstructor
public class RefreshTokenValidator {

    private final JwtUtil jwtUtil;

    public boolean isInvalid(String refresh) {

        return isBlank(refresh)
                || isExpired(refresh)
                || isNotRefreshCategory(refresh);

    }

    // null 여부 검사
    private boolean isBlank(String refresh) {
        return StringUtils.isBlank(refresh);

    }

    // 파기 여부 검사
    private boolean isExpired(String refresh) {
        return jwtUtil.isExpired(refresh);

    }

    // refresh 토큰 카테고리 검사
    private boolean isNotRefreshCategory(String refresh) {
        return !"refresh".equals(jwtUtil.getCategory(refresh));

    }

}
