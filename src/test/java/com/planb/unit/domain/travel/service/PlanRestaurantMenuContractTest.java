package com.planb.unit.domain.travel.service;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail;
import com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import com.planb.domain.travel.helper.PlanPlaceResolver;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class PlanRestaurantMenuContractTest {

    private final PlanPlaceResolver resolver = new PlanPlaceResolver();

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"B", "\"잘못된 메뉴\"\\경로\n다음 줄"})
    @DisplayName("메뉴 교정 사유의 선택값과 허용 목록을 JSON으로 구분")
    void describesCorrectionWithJsonMenuArray(String selectedMenu) {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                "교동쌈밥 한정식 (A, B, C)",
                "\"특선\" 쌈밥, 된장찌개"
        ));

        String failure = candidates.restaurantMenuFailure("tour:9", selectedMenu);
        JsonMapper mapper = JsonMapper
                .builder()
                .build();

        String selectedValue = failure
                .split("선택 메뉴\\(JSON\\): ", 2)[1]
                .split(" / 선택 가능 메뉴", 2)[0];

        assertThat(mapper.readValue(selectedValue, String.class))
                .isEqualTo(selectedMenu);

        String allowedMenus = failure
                .split("선택 가능 메뉴\\(JSON 배열\\): ", 2)[1]
                .split(" / 배열의 문자열", 2)[0];

        assertThat(JsonMapper
                .builder()
                .build()
                .readTree(allowedMenus))
                .isEqualTo(JsonMapper
                        .builder()
                        .build()
                        .valueToTree(List.of(
                                "교동쌈밥 한정식 (A, B, C)",
                                "\"특선\" 쌈밥",
                                "된장찌개"
                        )));
    }

    @Test
    @DisplayName("선택 식당 원본에 없는 메뉴의 저장 전 검증 거부")
    void rejectsMenuOutsideSelectedRestaurantSource() {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                "닭갈비",
                "막국수"
        ));

        PlanPlaceResolver.Validation result = resolver.validate(
                meal("불고기"),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(result.valid())
                .isFalse();
        assertThat(result.reason())
                .contains("메뉴 원본 불일치");
    }

    @ParameterizedTest
    @MethodSource("availableMenus")
    @DisplayName("대표·판매 메뉴의 목록 분리와 원본 선택 허용")
    void acceptsOnlyListedMenu(
            String firstMenu,
            String treatmentMenu,
            String selectedMenu
    ) {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "00",
                firstMenu,
                treatmentMenu
        ));

        PlanPlaceResolver.Validation result = resolver.validate(
                meal(selectedMenu),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(result.valid())
                .as(result.reason())
                .isTrue();
        assertThat(result
                        .schedule()
                        .restaurantDetail()
                        .menuName())
                .isEqualTo(selectedMenu.strip());
        assertThat(result
                        .schedule()
                        .locationName())
                .isEqualTo("춘천식당");
        assertThat(result
                        .schedule()
                        .longitude())
                .isEqualTo("127.73");
    }

    private static Stream<Arguments> availableMenus() {

        return Stream.of(
                Arguments.of(
                        "교동쌈밥 한정식 (A, B, C)",
                        "",
                        "교동쌈밥 한정식 (A, B, C)"
                ),
                Arguments.of(
                        "한정식 (쌈밥 (A, B), 국/반찬;후식|차), 막국수",
                        "",
                        "한정식 (쌈밥 (A, B), 국/반찬;후식|차)"
                ),
                Arguments.of(
                        "한정식 (쌈밥 (A, B), 국/반찬;후식|차), 막국수",
                        "",
                        "막국수"
                ),
                Arguments.of(
                        "정식 (밥\n국<BR />반찬), 칼국수",
                        "",
                        "정식 (밥\n국<BR />반찬)"
                ),
                Arguments.of(
                        "  정식 (밥, 국)  \r\n막국수;;|/",
                        "정식 (밥, 국), 막국수",
                        " 정식 (밥, 국) "
                ),
                Arguments.of(
                        "닭갈비",
                        "막국수",
                        "닭갈비"
                ),
                Arguments.of(
                        "닭갈비",
                        "막국수",
                        "막국수"
                ),
                Arguments.of(
                        "",
                        "막국수",
                        "막국수"
                ),
                Arguments.of(
                        null,
                        "막국수",
                        " 막국수 "
                ),
                Arguments.of(
                        "닭갈비, 막국수",
                        "쌈밥/백반;냉면|두부\n비빔밥<BR/>칼국수",
                        "칼국수"
                ),
                Arguments.of(
                        "닭갈비, 막국수",
                        "쌈밥/백반;냉면|두부\n비빔밥<br>칼국수",
                        "백반"
                )
        );
    }

    @ParameterizedTest
    @MethodSource("invalidCompoundMenus")
    @DisplayName("괄호 내부 조각과 잘린 메뉴·최상위 메뉴 목록의 선택 거부")
    void rejectsMenuFragments(
            String firstMenu,
            String selectedMenu
    ) {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                firstMenu,
                ""
        ));

        assertThat(resolver
                .validate(
                        meal(selectedMenu),
                        candidates,
                        Set.of(),
                        Set.of()
                )
                .valid())
                .isFalse();
    }

    private static Stream<Arguments> invalidCompoundMenus() {

        return Stream.of(
                Arguments.of(
                        "교동쌈밥 한정식 (A, B, C)",
                        "교동쌈밥 한정식 (A"
                ),
                Arguments.of(
                        "교동쌈밥 한정식 (A, B, C)",
                        "B"
                ),
                Arguments.of(
                        "교동쌈밥 한정식 (A, B, C)",
                        "C)"
                ),
                Arguments.of(
                        "한정식 (A, B), 막국수",
                        "한정식 (A, B), 막국수"
                ),
                Arguments.of(
                        "한정식 (A, B",
                        "한정식 (A"
                ),
                Arguments.of(
                        "한정식 (A, B",
                        "한정식 (A, B"
                ),
                Arguments.of(
                        "한정식 A), 막국수",
                        "막국수"
                )
        );
    }

    @ParameterizedTest
    @MethodSource("partiallyMalformedDetails")
    @DisplayName("불균형 괄호 필드 제외 후 다른 정상 필드의 메뉴 유지")
    void retainsValidMenusAfterMalformedField(
            String firstMenu,
            String treatmentMenu,
            List<String> expectedMenus
    ) {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                firstMenu,
                treatmentMenu
        ));

        assertThat(candidates.restaurantMenus("tour:9", () -> {
            throw new AssertionError("상세 중복 조회");
        }))
                .containsExactlyElementsOf(expectedMenus);
    }

    private static Stream<Arguments> partiallyMalformedDetails() {

        return Stream.of(
                Arguments.of(
                        "한정식 (A, B",
                        "막국수, 칼국수",
                        List.of("막국수", "칼국수")
                ),
                Arguments.of(
                        "정식 (밥, 국)",
                        "막국수, 정식 A)",
                        List.of("정식 (밥, 국)")
                )
        );
    }

    @Test
    @DisplayName("공백·빈 메뉴 제거와 대표·판매 메뉴 중복 제거")
    void keepsOnlyDistinctNonBlankMenus() {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                " /;|\r\n<br> 정식 (밥, 국) ",
                "정식 (밥, 국), 막국수,, "
        ));

        assertThat(candidates.restaurantMenus("tour:9", () -> {
            throw new AssertionError("상세 중복 조회");
        }))
                .containsExactly("정식 (밥, 국)", "막국수");
    }

    @ParameterizedTest
    @MethodSource("unverifiedDetails")
    @DisplayName("음식점 상세의 ID·유형·응답 상태 불일치 거부")
    void rejectsUnverifiedDetails(Kor2RestaurantIntroResponse detail) {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail);

        PlanPlaceResolver.Validation result = resolver.validate(
                meal("막국수"),
                candidates,
                Set.of(),
                Set.of()
        );

        assertThat(result.valid())
                .isFalse();
        assertThat(result.reason())
                .contains("상세 메뉴 미확인");
    }

    private static Stream<Arguments> unverifiedDetails() {

        Kor2RestaurantIntroResponse.Body matchingBody = new Kor2RestaurantIntroResponse.Body(
                new Kor2RestaurantIntroResponse.Items(List.of(
                        new Kor2RestaurantIntroResponse.Item(
                                "9",
                                "39",
                                "막국수",
                                "막국수"
                        )
                )),
                1,
                1,
                1
        );

        return Stream.of(
                Arguments.of((Object) null),
                Arguments.of(new Kor2RestaurantIntroResponse(null)),
                Arguments.of(new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(null, matchingBody))),
                Arguments.of(detail(
                        "9",
                        null,
                        "막국수",
                        "막국수"
                )),
                Arguments.of(detail(
                        "9",
                        "429",
                        "막국수",
                        "막국수"
                )),
                Arguments.of(detail(
                        "10",
                        "0000",
                        "막국수",
                        "막국수"
                )),
                Arguments.of(new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(
                        new Kor2RestaurantIntroResponse.Header("0000", "OK"),
                        new Kor2RestaurantIntroResponse.Body(
                                new Kor2RestaurantIntroResponse.Items(List.of(
                                        new Kor2RestaurantIntroResponse.Item(
                                                "9",
                                                "12",
                                                "막국수",
                                                "막국수"
                                        )
                                )),
                                1,
                                1,
                                1
                        )
                ))),
                Arguments.of(new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(
                        new Kor2RestaurantIntroResponse.Header("0000", "OK"),
                        null
                )))
        );
    }

    @Test
    @DisplayName("부분 문자열과 표준 음식명의 원본 메뉴 대체 거부")
    void rejectsMenuAlias() {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                "메밀막국수",
                ""
        ));

        assertThat(resolver.validate(
                meal("막국수"),
                candidates,
                Set.of(),
                Set.of()
        ).valid())
                .isFalse();
    }

    @Test
    @DisplayName("확인한 원본 메뉴도 일정 내 중복 사용 거부")
    void rejectsPreviouslyUsedMenu() {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                "막국수",
                ""
        ));

        assertThat(resolver.validate(
                meal("막국수"),
                candidates,
                Set.of(),
                Set.of("막국수")
        ).valid())
                .isFalse();
    }

    @Test
    @DisplayName("상세 초기화 및 별도 요청의 원본 메뉴 분리")
    void isolatesMenuEvidenceBetweenRequests() {

        PlaceCandidateContext first = candidates();
        first.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                "막국수",
                ""
        ));
        PlaceCandidateContext second = candidates();

        assertThat(resolver.validate(
                meal("막국수"),
                second,
                Set.of(),
                Set.of()
        ).valid())
                .isFalse();

        first.clear();
        first.record(restaurantItem());

        assertThat(resolver.validate(
                meal("막국수"),
                first,
                Set.of(),
                Set.of()
        ).valid())
                .isFalse();
    }

    @Test
    @DisplayName("실패한 상세 응답의 동일 요청 정상 원본 덮어쓰기 방지")
    void keepsExistingEvidenceAfterFailedDetail() {

        PlaceCandidateContext candidates = candidates();
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "0000",
                "막국수",
                ""
        ));
        candidates.recordRestaurantDetail("tour:9", detail(
                "10",
                "0000",
                "불고기",
                ""
        ));
        candidates.recordRestaurantDetail("tour:9", detail(
                "9",
                "500",
                "불고기",
                ""
        ));

        assertThat(resolver.validate(
                meal("막국수"),
                candidates,
                Set.of(),
                Set.of()
        ).valid())
                .isTrue();
        assertThat(resolver.validate(
                meal("불고기"),
                candidates,
                Set.of(),
                Set.of()
        ).valid())
                .isFalse();
    }

    private PlaceCandidateContext candidates() {

        PlaceCandidateContext candidates = new PlaceCandidateContext();
        candidates.record(restaurantItem());

        return candidates;
    }

    private Kor2KeywordSearchResponse.Item restaurantItem() {

        return new Kor2KeywordSearchResponse.Item(
                "강원특별자치도 춘천시",
                "",
                null,
                "9",
                "39",
                null,
                null,
                null,
                null,
                "127.73",
                "37.88",
                null,
                null,
                null,
                "춘천식당",
                null,
                null,
                null,
                null,
                null
        );
    }

    private static Kor2RestaurantIntroResponse detail(
            String id,
            String code,
            String firstMenu,
            String treatmentMenu
    ) {

        return new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(
                new Kor2RestaurantIntroResponse.Header(code, "응답"),
                new Kor2RestaurantIntroResponse.Body(
                        new Kor2RestaurantIntroResponse.Items(List.of(
                                new Kor2RestaurantIntroResponse.Item(
                                        id,
                                        "39",
                                        firstMenu,
                                        treatmentMenu
                                )
                        )),
                        1,
                        1,
                        1
                )
        ));
    }

    private PlanScheduleDetail meal(String menu) {

        return new PlanScheduleDetail(
                ScheduleType.LUNCH,
                CourseType.RESTAURANT,
                LocalTime.of(12, 0),
                LocalTime.of(13, 0),
                "AI 장소명",
                "AI 주소",
                "127.0",
                "37.0",
                null,
                null,
                60,
                0,
                Set.of(),
                null,
                new RestaurantDetail(
                        menu,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                ),
                "tour:9"
        );
    }
}
