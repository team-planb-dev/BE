package com.planb.ai.prompt;

public record VerifiedPlacePrompt(AiPrompt delegate) implements AiPrompt {
    @Override
    public String system() {

        return delegate.system() + """

                [Java 장소 검증 계약 — 앞선 장소 확정 규칙보다 우선]
                실제 장소가 있는 모든 슬롯은 이번 호출의 검색 Tool 결과 candidateId를 반환해야 합니다.
                장소명/주소/좌표/이미지는 Java가 검색 원본으로 확정합니다. 기존 일정도 다시 검색합니다.
                ATTRACTION/MUST_HAVE는 tour 유형 12 또는 kakao AT4만 허용합니다.
                PARK_WALK는 공원 카테고리가 확인된 kakao AT4만 허용합니다.
                CAFE_REST는 kakao CE7만, RESTAURANT/LOCAL_FOOD는 tour 39 또는 kakao FD6만 허용합니다.
                MEDICATION/TRANSPORTATION은 candidateId=null입니다.
                음식점을 관광지로 선택하거나 존재 확인만으로 관광지라고 판단하지 않습니다.
                getRoute에는 장소명이 아니라 이번 호출의 검색 Tool이 반환한 두 candidateId를 전달합니다.
                """;
    }

    @Override
    public String user() {

        return delegate.user();
    }
}
