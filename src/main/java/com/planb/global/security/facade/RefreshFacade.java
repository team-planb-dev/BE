package com.planb.global.security.facade;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.planb.global.config.exception.BaseExceptionEnum;
import com.planb.global.config.exception.domain.BaseException;
import com.planb.global.security.dto.response.ReissueResponse;
import com.planb.global.security.service.RefreshService;

/**
 * Refresh Token 검증 결과를 토큰 재발급 흐름으로 변환하는 Facade.
 *
 * Refresh Token 상태별 실패 사유를 공통 예외 계약으로 변환해
 * Controller가 보안 정책의 분기 조건을 알지 않도록 한다.
 */
@Component
@RequiredArgsConstructor
public class RefreshFacade {

    private final RefreshService refreshService;

    /**
     * 요청의 Refresh Token을 검증하고 유효한 경우 토큰을 재발급한다.
     *
     * @param request Refresh Token Cookie를 포함한 HTTP 요청
     * @return 재발급된 토큰 정보
     * @throws BaseException Refresh Token이 없거나 만료된 경우
     */
    public ReissueResponse reissue(HttpServletRequest request){

        ReissueResponse response = refreshService.refreshCookies(request);

        return switch (response.status()) {

            case REFRESH_EXPIRED ->
                    throw new BaseException(BaseExceptionEnum.REFRESH_TOKEN_NOT_FOUND);

            case REFRESH_NULL ->
                    throw new BaseException(BaseExceptionEnum.REFRESH_TOKEN_EXPIRED);

            default ->
                    response;
        };

    }
}
