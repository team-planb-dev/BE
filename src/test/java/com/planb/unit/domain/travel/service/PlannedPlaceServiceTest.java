package com.planb.unit.domain.travel.service;

import com.planb.domain.travel.dto.request.CreatePlannedPlaceRequest;
import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.PlannedPlace;
import com.planb.domain.travel.entity.Travel;
import com.planb.domain.travel.repository.PlannedPlaceRepository;
import com.planb.domain.travel.service.PlannedPlaceService;
import com.planb.global.client.kor2Service.dto.response.Kor2KeywordSearchResponse;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PlannedPlaceServiceTest {

    @Mock
    private PlannedPlaceRepository plannedPlaceRepository;

    @Mock
    private Kor2ServiceHandler kor2ServiceHandler;

    @InjectMocks
    private PlannedPlaceService plannedPlaceService;

    @Test
    @DisplayName("Kor2Service 검색 결과 PlannedPlace 응답 변환")
    void searchPlannedPlace() {

        SearchPlannedPlaceRequest request =
                new SearchPlannedPlaceRequest(
                        "경복궁"
                );

        Kor2KeywordSearchResponse.Item item =
                new Kor2KeywordSearchResponse.Item(
                        "서울특별시 종로구 사직로 161",
                        null,
                        null,
                        "126508",
                        "12",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "경복궁",
                        null,
                        null,
                        null,
                        null,
                        null
                );

        Kor2KeywordSearchResponse response =
                new Kor2KeywordSearchResponse(
                        new Kor2KeywordSearchResponse.Response(
                                new Kor2KeywordSearchResponse.Header(
                                        "0000",
                                        "OK"
                                ),
                                new Kor2KeywordSearchResponse.Body(
                                        new Kor2KeywordSearchResponse.Items(
                                                List.of(item)
                                        ),
                                        10,
                                        1,
                                        1
                                )
                        )
                );

        when(
                kor2ServiceHandler.searchKeywordOnly(
                        request.searchText()
                )
        ).thenReturn(
                Mono.just(response)
        );

        SearchPlannedPlaceResponse result =
                plannedPlaceService
                        .searchPlannedPlace(request)
                        .block();

        assertNotNull(result);

        assertEquals(
                1,
                result.plannedPlaces().size()
        );

        assertEquals(
                "경복궁",
                result.plannedPlaces()
                        .get(0)
                        .locationName()
        );

        assertEquals(
                "서울특별시 종로구 사직로 161",
                result.plannedPlaces()
                        .get(0)
                        .location()
        );

        verify(kor2ServiceHandler)
                .searchKeywordOnly("경복궁");
    }

    @Test
    @DisplayName("PlannedPlace 객체 리스트 생성")
    void makePlannedPlace() {

        Travel travel =
                Travel.builder()
                        .travelName("서울 여행")
                        .build();

        List<CreatePlannedPlaceRequest.PlannedPlaceDetail> details =
                List.of(
                        new CreatePlannedPlaceRequest.PlannedPlaceDetail(
                                "경복궁",
                                "서울특별시 종로구"
                        ),
                        new CreatePlannedPlaceRequest.PlannedPlaceDetail(
                                "남산서울타워",
                                "서울특별시 용산구"
                        )
                );

        CreatePlannedPlaceRequest request =
                new CreatePlannedPlaceRequest(
                        travel,
                        details
                );

        List<PlannedPlace> result =
                plannedPlaceService.makePlannedPlace(
                        request
                );

        assertEquals(
                2,
                result.size()
        );

        assertSame(
                travel,
                result.get(0)
                        .getTravel()
        );

        assertEquals(
                "경복궁",
                result.get(0)
                        .getLocationName()
        );

        assertEquals(
                "서울특별시 종로구",
                result.get(0)
                        .getLocation()
        );

        assertSame(
                travel,
                result.get(1)
                        .getTravel()
        );

        assertEquals(
                "남산서울타워",
                result.get(1)
                        .getLocationName()
        );

        assertEquals(
                "서울특별시 용산구",
                result.get(1)
                        .getLocation()
        );
    }

    @Test
    @DisplayName("PlannedPlace 객체 리스트 일괄 저장")
    void savePlannedPlaceList() {

        List<PlannedPlace> plannedPlaceList =
                List.of(
                        PlannedPlace.builder()
                                .locationName("경복궁")
                                .build(),
                        PlannedPlace.builder()
                                .locationName("남산서울타워")
                                .build()
                );

        plannedPlaceService.savePlannedPlaceList(
                plannedPlaceList
        );

        verify(plannedPlaceRepository)
                .saveAll(plannedPlaceList);
    }

    @Test
    @DisplayName("PlannedPlace 객체 리스트 일괄 삭제")
    void deletePlannedPlaceList() {

        List<PlannedPlace> plannedPlaceList =
                List.of(
                        PlannedPlace.builder()
                                .locationName("경복궁")
                                .build(),
                        PlannedPlace.builder()
                                .locationName("남산서울타워")
                                .build()
                );

        plannedPlaceService.deletePlannedPlaceList(
                plannedPlaceList
        );

        verify(plannedPlaceRepository)
                .deleteAll(plannedPlaceList);
    }
}