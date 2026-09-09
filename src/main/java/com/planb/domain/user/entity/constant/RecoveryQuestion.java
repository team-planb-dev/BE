package com.planb.domain.user.entity.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 이메일 찾기와 비밀번호 재설정에 사용하는 계정 복구 질문.
 *
 * 질문 문구는 화면에 그대로 노출되므로 상수로 고정하고,
 * 저장은 enum 이름으로 하여 문구가 바뀌어도 기존 사용자의 답변이 유지되도록 한다.
 */
@Getter
@RequiredArgsConstructor
public enum RecoveryQuestion {

    FAVORITE_TEACHER("내가 어릴 때 가장 좋아했던 선생님의 성함은?"),
    FIRST_PET("처음 키웠던 반려동물의 이름은?"),
    FAVORITE_CHILDHOOD_BOOK("어린 시절 가장 좋아했던 책의 제목은?"),
    FIRST_SOLO_TRIP_PLACE("처음 혼자 여행한 장소는?"),
    CHILDHOOD_NICKNAME("내가 가장 좋아했던 학창시절 별명은?"),
    FAVORITE_CHILDHOOD_SNACK("어릴 때 가장 자주 먹었던 간식은?"),
    MEMORABLE_CHILDHOOD_PLACE("가장 기억에 남는 어린 시절 장소는?"),
    FIRST_INSTRUMENT_OR_SPORT("처음 배운 악기나 운동은?");

    private final String question;
}
