package com.planb.domain.user.dto.response;

/**
 * 마스킹된 이메일 찾기 결과
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
