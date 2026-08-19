package com.planb.domain.travel.service;

import com.planb.domain.travel.dto.request.CreatePlannedPlaceRequest;
import com.planb.domain.travel.dto.request.SearchPlannedPlaceRequest;
import com.planb.domain.travel.dto.response.SearchPlannedPlaceResponse;
import com.planb.domain.travel.entity.PlannedPlace;
import com.planb.domain.travel.entity.constant.PlaceType;
import com.planb.domain.travel.repository.PlannedPlaceRepository;
import com.planb.global.client.kor2Service.handler.Kor2ServiceHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlannedPlaceService {

    /*
    Repository
     */
    private final PlannedPlaceRepository plannedPlaceRepository;

    /*
    Handler
     */
    private final Kor2ServiceHandler kor2ServiceHandler;


    // kor2Service API 호출 결과 렌더링
    public Mono<SearchPlannedPlaceResponse> searchPlannedPlace
    (SearchPlannedPlaceRequest searchPlannedPlaceRequest) {

        return kor2ServiceHandler
                .searchKeyword(searchPlannedPlaceRequest.searchText())
                .map(response -> {

                    List<SearchPlannedPlaceResponse.PlannedPlaceDetail> plannedPlaces =
                            response.response()
                                    .body()
                                    .items()
                                    .item()
                                    .stream()
                                    .map(item ->
                                            new SearchPlannedPlaceResponse.PlannedPlaceDetail(
                                                    item.title(),
                                                    item.addr1(),
                                                    PlaceType
                                                            .fromContentTypeId(
                                                                    item
                                                                            .contenttypeid()
                                                    )
                                            )
                                    )
                                    .toList();

                    return new SearchPlannedPlaceResponse(
                            plannedPlaces
                    );
                });
    }

    // PlannedPlace 객체 리스트 생성하기
    public List<PlannedPlace> makePlannedPlace
    (CreatePlannedPlaceRequest createPlannedPlaceRequest){

        return createPlannedPlaceRequest
                .plannedPlaceList()
                .stream()
                .map(detail ->
                        PlannedPlace.builder()
                                .travel(createPlannedPlaceRequest
                                        .travel())
                                .locationName(detail
                                        .locationName())
                                .location(detail
                                        .location())
                                .build()
                )
                .toList();
    }



    /*
    기본 CRUD 모음
     */

    // PlanedPlace 리스트 저장하기
    public void savePlannedPlaceList(List<PlannedPlace> plannedPlaceList){

        plannedPlaceRepository.saveAll(plannedPlaceList);
    }

    // PlannedPlace 리스트 삭제하기
    public void deletePlannedPlaceList(List<PlannedPlace> plannedPlaceList){

        plannedPlaceRepository.deleteAll(plannedPlaceList);
    }


}
