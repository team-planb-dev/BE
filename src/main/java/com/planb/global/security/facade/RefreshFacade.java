package com.planb.global.security.facade;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.service.RefreshService;

/**
 * Refresh Token 검증과 토큰 재발급 흐름을 조합
 */
@Component
@RequiredArgsConstructor
public class RefreshFacade {

    private final RefreshService refreshService;

    /**
     * Refresh Token 검증 및 토큰 재발급
     */
    public ReissueResponse reissue(HttpServletRequest request) {

        return refreshService.reissue(request); // 토큰 재발급

    }
}
