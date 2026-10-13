package com.planb.unit.domain.travel.policy;

import com.planb.ai.context.TravelHealthContext;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.policy.AllergenPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

// 등록 알레르기 식재료와 승인 분류어의 식당·메뉴 제외 규칙
class AllergenTestPolicy {

    @Test
    @DisplayName("새우 알레르기의 해물 메뉴는 분류어 위반")
    void rejectsSeafoodCategoryMenuForShrimpAllergy() {

        assertThat(AllergenPolicy.violation(
                allergy("새우"),
                "가야밀냉면해물칼국수",
                "해물손칼국수"
        ))
                .hasValueSatisfying(reason -> assertThat(reason)
                        .contains(
                                "새우",
                                "해물"
                        ));
    }

    @Test
    @DisplayName("업소명에만 분류어가 있어도 위반")
    void rejectsCategoryWordInPlaceName() {

        assertThat(AllergenPolicy.violation(
                allergy("새우"),
                "해물왕창칼국수",
                "칼국수"
        ))
                .isPresent();
    }

    @Test
    @DisplayName("등록 식재료 이름 자체와 공백 섞인 표기도 위반")
    void rejectsRegisteredNameIgnoringSpaces() {

        assertThat(AllergenPolicy.violation(
                allergy("새우"),
                "분식집",
                "새우 튀김"
        ))
                .isPresent();

        assertThat(AllergenPolicy.violation(
                allergy("새우"),
                "칼국수집",
                "해 물 칼국수"
        ))
                .isPresent();
    }

    @Test
    @DisplayName("분류어 사전에 없는 알레르기는 등록 이름만 판정")
    void usesRegisteredNameOnlyWithoutCategoryEntry() {

        assertThat(AllergenPolicy.violation(
                allergy("땅콩"),
                "국수집",
                "땅콩국수"
        ))
                .isPresent();

        assertThat(AllergenPolicy.violation(
                allergy("땅콩"),
                "전집",
                "해물파전"
        ))
                .isEmpty();
    }

    @Test
    @DisplayName("한 글자 등록 이름도 포함 여부로 보수 판정")
    void rejectsSingleCharacterAllergenConservatively() {

        assertThat(AllergenPolicy.violation(
                allergy("게"),
                "탕집",
                "꽃게탕"
        ))
                .isPresent();
    }

    @Test
    @DisplayName("관련 없는 메뉴와 피하는 음식 등록은 위반 아님")
    void ignoresUnrelatedMenuAndAvoidFood() {

        assertThat(AllergenPolicy.violation(
                allergy("새우"),
                "할매국밥",
                "돼지국밥"
        ))
                .isEmpty();

        assertThat(AllergenPolicy.violation(
                List.of(traveler(new TravelHealthContext.FoodInfoContext(
                        "새우",
                        FoodType.AVOID
                ))),
                "해물칼국수",
                "해물칼국수"
        ))
                .isEmpty();

        assertThat(AllergenPolicy.violation(
                List.of(),
                "해물칼국수",
                null
        ))
                .isEmpty();
    }

    @Test
    @DisplayName("등록 이름이 사전 항목을 포함하면 분류어까지 적용")
    void appliesCategoryWordsWhenRegisteredNameContainsKey() {

        assertThat(AllergenPolicy.violation(
                allergy("새우류"),
                "칼국수집",
                "해물칼국수"
        ))
                .isPresent();

        assertThat(AllergenPolicy.violation(
                allergy(" 새우 "),
                "씨푸드 뷔페",
                "모둠"
        ))
                .isPresent();
    }

    @Test
    @DisplayName("영문 대소문자 차이 무시")
    void ignoresLetterCase() {

        assertThat(AllergenPolicy.violation(
                allergy("Shrimp"),
                "버거집",
                "shrimp burger"
        ))
                .isPresent();
    }

    @Test
    @DisplayName("게 알레르기의 본가게장은 위반, 가게 표기 오탐은 안전 우선으로 허용")
    void keepsCrabDishBehindStoreWord() {

        assertThat(AllergenPolicy.violation(
                allergy("게"),
                "본가게장",
                "정식"
        ))
                .isPresent();

        // 알려진 오탐: 식재료 의미 없는 '가게' 표기도 제외 대상
        assertThat(AllergenPolicy.violation(
                allergy("게"),
                "동네 가게",
                "김치찌개"
        ))
                .isPresent();
    }

    @Test
    @DisplayName("여러 여행자 중 한 명의 알레르기와 업소명 누락 처리")
    void checksEveryTravelerAndMissingPlaceName() {

        assertThat(AllergenPolicy.violation(
                List.of(
                        traveler(new TravelHealthContext.FoodInfoContext(
                                "땅콩",
                                FoodType.ALLERGY
                        )),
                        traveler(new TravelHealthContext.FoodInfoContext(
                                "새우",
                                FoodType.ALLERGY
                        ))
                ),
                null,
                "짬뽕"
        ))
                .isPresent();
    }

    private List<TravelHealthContext> allergy(String foodName) {

        return List.of(traveler(new TravelHealthContext.FoodInfoContext(
                foodName,
                FoodType.ALLERGY
        )));
    }

    private TravelHealthContext traveler(TravelHealthContext.FoodInfoContext food) {

        return new TravelHealthContext(
                "동행인",
                List.of(),
                WalkType.MODERATE,
                null,
                List.of(food),
                List.of()
        );
    }
}
