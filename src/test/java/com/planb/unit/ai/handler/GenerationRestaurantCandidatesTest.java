package com.planb.unit.ai.handler;

import com.planb.ai.context.PlaceCandidateContext;
import com.planb.ai.context.TravelHealthContext;
import com.planb.ai.handler.GenerationRestaurantCandidates;
import com.planb.ai.mcp.TourismTool;
import com.planb.domain.health.entity.constant.FoodType;
import com.planb.domain.health.entity.constant.WalkType;
import com.planb.domain.travel.entity.constant.ScheduleType;
import java.time.LocalTime;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.dto.response.Kor2RestaurantIntroResponse;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.ArgumentMatchers.anyString;

class GenerationRestaurantCandidatesTest {

    @Test
    void removesAllergyMenusWithoutRemovingSafeMenuFromSameRestaurant() {

        TourismTool tourism = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(
                tourism,
                candidates,
                List.of(new TravelHealthContext(
                        "여행자",
                        List.of(),
                        WalkType.ACTIVE,
                        null,
                        List.of(new TravelHealthContext.FoodInfoContext("새우", FoodType.ALLERGY)),
                        List.of()
                ))
        );
        Kor2KeywordSearchResponse source = search("1");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천"))
                .thenReturn(source);
        when(tourism.getRestaurantDetail("1"))
                .thenReturn(detail("1", "쉬림프 국수 / 막국수", null));

        var result = restaurants.search("국수", "강원", "춘천");
        assertThat(result.getFirst().menus()).containsExactly("막국수");
    }

    @Test
    void limitsDetailLookupToFourDistinctCandidatesPerSearch() {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(
                tourism,
                new PlaceCandidateContext()
        );
        List<Kor2KeywordSearchResponse.Item> items = java.util.stream.IntStream
                .rangeClosed(1, 5)
                .mapToObj(index -> search(Integer.toString(index)).response().body().items().item().getFirst())
                .toList();
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천"))
                .thenReturn(new Kor2KeywordSearchResponse(new Kor2KeywordSearchResponse.Response(
                        null,
                        new Kor2KeywordSearchResponse.Body(new Kor2KeywordSearchResponse.Items(items), 5, 1, 5)
                )));
        for (int i = 1; i <= 4; i++) {
            when(tourism.getRestaurantDetail(Integer.toString(i)))
                    .thenReturn(detail(Integer.toString(i), "막국수" + i, null));
        }

        assertThat(restaurants.search("국수", "강원", "춘천")).hasSize(4);
        verify(tourism, never()).getRestaurantDetail("5");
    }

    @Test
    void stopsAfterEightDifferentSearchesAndCachesEmptyResults() {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, new PlaceCandidateContext());
        for (int i = 0; i < 9; i++) {
            assertThat(restaurants.search("음식" + i, "강원", "춘천")).isEmpty();
        }
        restaurants.search(" 음식0 ", "강원", "춘천");
        verify(tourism, times(8)).searchRestaurantsByLocation(anyString(), anyString(), anyString());
    }

    @Test
    void excludesDefiniteTimeConflictsAndRelaxesOnlyMealsWithNoCandidates() {

        TourismTool tourism = mock(TourismTool.class);
        TravelHealthContext health = new TravelHealthContext(
                "여행자",
                List.of(),
                WalkType.ACTIVE,
                new TravelHealthContext.MealInfoContext(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(19, 0)),
                List.of(),
                List.of()
        );
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, new PlaceCandidateContext(), List.of(health));
        var first = search("1").response().body().items().item().getFirst();
        var second = search("2").response().body().items().item().getFirst();
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천"))
                .thenReturn(new Kor2KeywordSearchResponse(new Kor2KeywordSearchResponse.Response(
                        null,
                        new Kor2KeywordSearchResponse.Body(new Kor2KeywordSearchResponse.Items(List.of(first, second)), 2, 1, 2)
                )));
        when(tourism.getRestaurantDetail("1")).thenReturn(detailWithHours("1", "막국수", "11:00~13:00"));
        when(tourism.getRestaurantDetail("2")).thenReturn(detailWithHours("2", "닭갈비", "17:00~22:00"));

        var result = restaurants.search("국수", "강원", "춘천");
        assertThat(result.getFirst().eligibleMeals()).contains(ScheduleType.BREAKFAST, ScheduleType.LUNCH).doesNotContain(ScheduleType.DINNER);
        assertThat(result.getFirst().relaxedMeals()).containsExactly(ScheduleType.BREAKFAST);
        assertThat(result.getLast().eligibleMeals()).contains(ScheduleType.BREAKFAST, ScheduleType.DINNER).doesNotContain(ScheduleType.LUNCH);
    }

    @Test
    void bindsSearchesToRequestRegionAndCountsFailedSearchAttempts() {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(
                tourism,
                new PlaceCandidateContext(),
                List.of(),
                "강원",
                "춘천"
        );
        assertThat(restaurants.search("국수", "강원", "강릉")).isEmpty();
        org.mockito.Mockito.verifyNoInteractions(tourism);
        when(tourism.searchRestaurantsByLocation(anyString(), anyString(), anyString()))
                .thenThrow(new IllegalStateException("upstream"));
        for (int i = 0; i < 8; i++) {
            String keyword = "메뉴" + i;
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> restaurants.search(keyword, "강원", "춘천"))
                    .isInstanceOf(IllegalStateException.class);
        }
        assertThat(restaurants.search("추가", "강원", "춘천")).isEmpty();
        assertThat(restaurants.search(" 메뉴0 ", "강원", "춘천")).isEmpty();
        verify(tourism, times(8)).searchRestaurantsByLocation(anyString(), anyString(), anyString());
    }

    @Test
    void rejectsInventedMenusAndReusesPreparedMenuForMealCompletion() {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, new PlaceCandidateContext());
        var source = search("1");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천")).thenReturn(source);
        when(tourism.getRestaurantDetail("1")).thenReturn(detail("1", "막국수", null));
        restaurants.search("국수", "강원", "춘천");

        assertThat(restaurants.accepts("tour:1", "가짜 메뉴", ScheduleType.LUNCH)).isFalse();
        assertThat(restaurants.accepts("tour:1", "막국수", ScheduleType.LUNCH)).isTrue();
        assertThat(restaurants.menu("tour:1", ScheduleType.LUNCH, java.util.Set.of())).isEqualTo("막국수");
        assertThat(restaurants.menu("tour:1", ScheduleType.LUNCH, java.util.Set.of("막국수"))).isNull();
        verify(tourism).getRestaurantDetail("1");
    }

    @Test
    void rejectsInventedLocalFoodMenuAndDetectsUnknownOpeningHours() {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, new PlaceCandidateContext());
        var source = search("1");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천")).thenReturn(source);
        when(tourism.getRestaurantDetail("1")).thenReturn(detail("1", "막국수", null));
        restaurants.search("국수", "강원", "춘천");
        var slot = new com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.LUNCH,
                com.planb.domain.travel.entity.constant.CourseType.LOCAL_FOOD,
                LocalTime.NOON,
                LocalTime.of(13, 0),
                "식당1", "주소", "127", "37", null, null, 60, 0, java.util.Set.of(), null,
                new com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail("가짜", null, null, null, null, null, null, null, null),
                "tour:1"
        );
        var response = new com.planb.ai.dto.response.CreatePlanAiResponse(List.of(
                new com.planb.ai.dto.response.CreatePlanAiResponse.PlanDayDetail(1, java.time.LocalDate.of(2030, 1, 1), List.of(slot))
        ));
        assertThat(restaurants.violations(response)).hasSize(1);
        assertThat(restaurants.hoursOutcome("tour:1", ScheduleType.LUNCH, LocalTime.NOON)).isEqualTo("unknown");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "11:00~13:00,12:00,compatible",
            "11:00~13:00,13:00,definite_mismatch",
            "22:00~02:00,23:00,compatible",
            "22:00~02:00,01:00,compatible",
            "22:00~02:00,02:00,definite_mismatch",
            "09:00~24:00,23:59,compatible",
            "브레이크타임 15시,12:00,unknown",
            "29:00~30:00,12:00,unknown",
            "00:00~00:00,12:00,unknown"
    })
    void classifiesFinalTimeWithoutTreatingUnknownHoursAsConfirmed(
            String hours,
            String time,
            String expected
    ) {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, new PlaceCandidateContext());
        var source = search("1");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천")).thenReturn(source);
        when(tourism.getRestaurantDetail("1")).thenReturn(detailWithHours("1", "막국수", hours));
        restaurants.search("국수", "강원", "춘천");

        assertThat(restaurants.hoursOutcome("tour:1", ScheduleType.LUNCH, LocalTime.parse(time))).isEqualTo(expected);
    }

    @Test
    void rejectsMismatchedDetailIdentityAndCachesMissingMenuUntilReset() {

        TourismTool tourism = mock(TourismTool.class);
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, new PlaceCandidateContext());
        var source = search("1");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천")).thenReturn(source);
        when(tourism.getRestaurantDetail("1"))
                .thenReturn(detail("other", "막국수", null))
                .thenReturn(detail("1", null, "막국수; 닭갈비<br>막국수"));
        assertThat(restaurants.search("국수", "강원", "춘천")).isEmpty();
        assertThat(restaurants.search("국수", "강원", "춘천")).isEmpty();
        verify(tourism).getRestaurantDetail("1");

        restaurants.clear();
        assertThat(restaurants.search("국수", "강원", "춘천").getFirst().menus())
                .containsExactly("막국수", "닭갈비");
    }

    @Test
    void refreshesEligibilityAfterRegionalFallbackIntroducesOpenRestaurant() {

        TourismTool tourism = mock(TourismTool.class);
        TravelHealthContext health = new TravelHealthContext(
                "여행자", List.of(), WalkType.ACTIVE,
                new TravelHealthContext.MealInfoContext(LocalTime.of(8, 0), LocalTime.NOON, LocalTime.of(19, 0)),
                List.of(), List.of()
        );
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(
                tourism, new PlaceCandidateContext(), List.of(health), "강원", "춘천"
        );
        var first = search("1");
        var second = search("2");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천")).thenReturn(first);
        when(tourism.searchRestaurantCandidatesByRegion("강원", "춘천")).thenReturn(second);
        when(tourism.getRestaurantDetail("1")).thenReturn(detailWithHours("1", "막국수", "11:00~13:00"));
        when(tourism.getRestaurantDetail("2")).thenReturn(detailWithHours("2", "닭갈비", "07:00~21:00"));
        assertThat(restaurants.accepts("tour:1", "막국수", ScheduleType.BREAKFAST)).isFalse();
        assertThat(restaurants.search("국수", "강원", "춘천").getFirst().relaxedMeals()).contains(ScheduleType.BREAKFAST);
        restaurants.collectRegional();
        restaurants.collectRegional();
        assertThat(restaurants.search("국수", "강원", "춘천").getFirst().eligibleMeals()).doesNotContain(ScheduleType.BREAKFAST);
        assertThat(restaurants.accepts("tour:1", "막국수", ScheduleType.BREAKFAST)).isFalse();
        verify(tourism).searchRestaurantCandidatesByRegion("강원", "춘천");
    }

    @Test
    void replacesModelOpeningHoursWithPreparedSourceAtPlaceValidationBoundary() {

        TourismTool tourism = mock(TourismTool.class);
        PlaceCandidateContext candidates = new PlaceCandidateContext();
        GenerationRestaurantCandidates restaurants = new GenerationRestaurantCandidates(tourism, candidates);
        candidates.generationRestaurants(restaurants);
        var source = search("1");
        when(tourism.searchRestaurantsByLocation("국수", "강원", "춘천")).thenReturn(source);
        when(tourism.getRestaurantDetail("1")).thenReturn(detailWithHours("1", "막국수", "11:00~13:00"));
        restaurants.search("국수", "강원", "춘천");
        var slot = new com.planb.ai.dto.response.CreatePlanAiResponse.PlanScheduleDetail(
                ScheduleType.LUNCH,
                com.planb.domain.travel.entity.constant.CourseType.LOCAL_FOOD,
                LocalTime.NOON, LocalTime.of(13, 0), "식당1", "주소", "127", "37",
                null, null, 60, 0, java.util.Set.of(), null,
                new com.planb.ai.dto.response.CreatePlanAiResponse.RestaurantDetail("막국수", null, null, null, "24 hours", null, null, null, null),
                "tour:1"
        );
        var result = new com.planb.domain.travel.helper.PlanPlaceResolver().validate(slot, candidates, java.util.Set.of(), java.util.Set.of());
        assertThat(result.valid()).isTrue();
        assertThat(result.schedule().restaurantDetail().openTime()).isEqualTo("11:00~13:00");
    }

    private static Kor2RestaurantIntroResponse detailWithHours(
            String id,
            String menu,
            String hours
    ) {

        return new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(
                null,
                new Kor2RestaurantIntroResponse.Body(new Kor2RestaurantIntroResponse.Items(List.of(
                        new Kor2RestaurantIntroResponse.Item(id, "39", menu, null, hours)
                )), 1, 1, 1)
        ));
    }

    private static Kor2KeywordSearchResponse search(String id) {

        Kor2KeywordSearchResponse.Item item = mock(Kor2KeywordSearchResponse.Item.class);
        when(item.contentid()).thenReturn(id);
        when(item.contenttypeid()).thenReturn("39");
        when(item.title()).thenReturn("식당" + id);
        when(item.addr1()).thenReturn("주소");
        when(item.mapx()).thenReturn("127");
        when(item.mapy()).thenReturn("37");
        return new Kor2KeywordSearchResponse(new Kor2KeywordSearchResponse.Response(
                null,
                new Kor2KeywordSearchResponse.Body(new Kor2KeywordSearchResponse.Items(List.of(item)), 1, 1, 1)
        ));
    }

    private static Kor2RestaurantIntroResponse detail(
            String id,
            String firstMenu,
            String treatMenu
    ) {

        return new Kor2RestaurantIntroResponse(new Kor2RestaurantIntroResponse.Response(
                null,
                new Kor2RestaurantIntroResponse.Body(new Kor2RestaurantIntroResponse.Items(List.of(
                        new Kor2RestaurantIntroResponse.Item(id, "39", firstMenu, treatMenu)
                )), 1, 1, 1)
        ));
    }
}
