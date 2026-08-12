package com.planb.domain.user.entity;

import com.planb.global.converter.BooleanToYNConverter;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;

@Embeddable
public class TermsAgreement {

    // 만 14세 이상 확인
    @Convert(converter = BooleanToYNConverter.class)
    @Column(nullable = false, length = 1)
    private boolean ageRequirementAgreed;

    // 서비스 이용약관 동의
    @Convert(converter = BooleanToYNConverter.class)
    @Column(nullable = false, length = 1)
    private boolean serviceTermsAgreed;

    // 개인정보 수집·이용 동의
    @Convert(converter = BooleanToYNConverter.class)
    @Column(nullable = false, length = 1)
    private boolean privacyCollectionAgreed;

}