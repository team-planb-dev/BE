package com.planb.domain.user.dto.response;

import com.planb.domain.user.entity.User;

/**
 * 마이페이지에서 보여줄 회원 정보.
 *
 * 인증 캐시(UserAuthCache)를 그대로 내보내지 않는다. 그 값에는 세션 식별자처럼
 * 화면이 쓸 일 없는 내부 정보가 들어 있어, 응답 타입을 따로 둔다.
 */
public record UserReadResponse(Long userId,
                               String username,
                               String nickname,
                               String role) {

    public static UserReadResponse from(User user) {

        return new UserReadResponse(
                user.getId(),
                user.getUsername(),
                user.getNickname(),
                user.getRole()
        );
    }
}
