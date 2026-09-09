package com.planb.domain.user.dto.response;

/**
 * 이메일 찾기 결과.
 *
 * 전체 이메일을 그대로 돌려주면 질문/답변만 아는 제3자에게 계정 주소가 노출되므로
 * 본인 확인용으로만 쓸 수 있도록 마스킹한 값을 내려준다.
 *
 * @param maskedUsername 마스킹된 이메일 (예: ye***@gmail.com)
 */
public record FindUsernameResponse(String maskedUsername) {

    public static FindUsernameResponse of(String username) {

        return new FindUsernameResponse(mask(username));
    }

    private static String mask(String username) {

        int atIndex = username.indexOf('@');

        if (atIndex <= 0) {
            return "***";
        }

        String localPart = username.substring(0, atIndex);
        String domainPart = username.substring(atIndex);

        int visibleLength = Math.min(2, localPart.length());

        return localPart.substring(0, visibleLength)
                + "***"
                + domainPart;
    }
}
