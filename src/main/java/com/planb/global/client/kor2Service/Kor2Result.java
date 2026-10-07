package com.planb.global.client.kor2Service;

/**
 * TourAPI 응답 본문의 결과 코드 노출
 */
public interface Kor2Result {

    // response.header.resultCode, 없으면 null
    String resultCode();
}
