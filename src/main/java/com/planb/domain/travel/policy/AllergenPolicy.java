package com.planb.domain.travel.policy;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.domain.health.entity.constant.FoodType;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 등록 알레르기 식재료와 승인 분류어의 식당·메뉴 제외 규칙
 * 원재료 미상 메뉴의 보수적 제외, 성분 추정·확정 판정 아님
 */
public final class AllergenPolicy {

    // 등록 식재료별 원재료 미상 분류어, 사용자 승인 범위(2026-10-13)
    // ponytail: 갑각류 3개 항목만, 다른 알레르기는 등록 이름만 판정, 필요 시 항목 추가
    private static final Map<String, List<String>> CATEGORY_WORDS = Map.of(
            "새우",
            List.of(
                    "대하",
                    "쉬림프",
                    "감바스",
                    "해물",
                    "해산물",
                    "짬뽕",
                    "해천",
                    "씨푸드",
                    "시푸드"
            ),
            "게",
            List.of(
                    "크랩",
                    "해물",
                    "해산물",
                    "짬뽕",
                    "씨푸드",
                    "시푸드"
            ),
            "갑각류",
            List.of(
                    "새우",
                    "게",
                    "대하",
                    "랍스터",
                    "크랩",
                    "쉬림프",
                    "감바스",
                    "해물",
                    "해산물",
                    "짬뽕",
                    "해천",
                    "씨푸드",
                    "시푸드"
            )
    );

    private AllergenPolicy() {
    }

    // 여행자 중 한 명이라도 등록한 알레르기 식재료 또는 분류어가 업소명·메뉴명에 있으면 위반 사유
    public static Optional<String> violation(
            List<TravelHealthContext> healthContexts,
            String placeName,
            String menuName
    ) {

        if (healthContexts == null) {
            return Optional.empty();
        }

        String place = comparable(placeName);

        String menu = comparable(menuName);

        return healthContexts
                .stream()
                .filter(Objects::nonNull)
                .map(TravelHealthContext::foodInfos)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .filter(food -> food != null && food.foodType() == FoodType.ALLERGY)
                .map(food -> comparable(food.foodName()))
                .filter(allergen -> !allergen.isEmpty())
                .distinct()
                .flatMap(allergen -> words(allergen)
                        .filter(word -> place.contains(word) || menu.contains(word))
                        .map(word -> "등록 알레르기 " + allergen + " 관련 표현 포함: " + word
                                + " (업소명=" + placeName + ", 메뉴=" + menuName + ")"))
                .findFirst();
    }

    // 등록 이름과 등록 이름에 포함된 사전 항목의 이름·분류어
    private static Stream<String> words(String allergen) {

        return Stream
                .concat(
                        Stream.of(allergen),
                        CATEGORY_WORDS
                                .entrySet()
                                .stream()
                                .sorted(Map.Entry.comparingByKey())
                                .filter(entry -> allergen.contains(entry.getKey()))
                                .flatMap(entry -> Stream.concat(
                                        Stream.of(entry.getKey()),
                                        entry
                                                .getValue()
                                                .stream()
                                ))
                )
                .distinct();
    }

    // 공백·대소문자 무시, '게' 포함 판정의 '가게' 표기 오탐은 안전 우선으로 허용
    private static String comparable(String text) {

        return PlaceCandidateContext
                .normalizeName(text)
                .toLowerCase(Locale.ROOT);
    }
}
