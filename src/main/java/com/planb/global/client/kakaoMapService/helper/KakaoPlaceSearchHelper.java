package com.planb.global.client.kakaoMapService.helper;

import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class KakaoPlaceSearchHelper {

    // 카카오맵 장소 검색 결과에 실제 항목이 하나라도 있는지 확인
    public boolean hasResult(KakaoPlaceSearchResponse response) {
        return Optional.ofNullable(response)
                .map(KakaoPlaceSearchResponse::documents)
                .filter(documents -> !documents.isEmpty())
                .isPresent();
    }

    // 검색 결과의 대표 장소명이 이미 사용된 이름 목록(excludeNames)에 포함되는지 확인.
    // LLM이 excludeNames를 정확히 전달하지 않았더라도, 실제 검색 결과를 기준으로 다시 한번 중복 여부를 검증하는 최종 방어선
    public boolean isExcluded(
            KakaoPlaceSearchResponse response,
            List<String> excludeNames
    ) {
        String placeName = normalize(firstPlaceName(response));

        return Optional.ofNullable(excludeNames)
                .orElseGet(List::of)
                .stream()
                .map(this::normalize)
                .anyMatch(placeName::equals);
    }

    // 검색 결과 중 가장 관련도 높은(첫 번째) 장소의 상호명(또는 장소명) 추출
    public String firstPlaceName(KakaoPlaceSearchResponse response) {
        return firstPlace(response).place_name();
    }

    // 검색 결과가 없을 때 반환할 "존재하지 않음" 결과 생성
    public PlaceWithRouteResult notFound() {
        return new PlaceWithRouteResult(false, null, null, null, null, null);
    }

    // 검색 결과(+선택적으로 조회한 이동시간)를 PlaceWithRouteResult로 변환.
    // 도로명주소(road_address_name)가 있으면 우선 사용하고, 없으면 지번주소(address_name)로 대체.
    // 카카오 로컬 검색 결과의 x(경도)/y(위도)를 좌표값으로 함께 반환한다.
    public PlaceWithRouteResult toResult(
            KakaoPlaceSearchResponse response,
            Integer travelMinutes
    ) {
        KakaoPlaceSearchResponse.Document place = firstPlace(response);

        String address = Optional.ofNullable(place.road_address_name())
                .filter(name -> !name.isBlank())
                .orElse(place.address_name());

        return new PlaceWithRouteResult(
                true,
                place.place_name(),
                address,
                place.x(),
                place.y(),
                travelMinutes
        );
    }

    // 이름 비교용 정규화(공백 트리밍). null은 빈 문자열로 취급
    private String normalize(String name) {
        return Optional.ofNullable(name)
                .map(String::trim)
                .orElse("");
    }

    // 검색 결과 중 첫 번째 장소 항목 추출
    private KakaoPlaceSearchResponse.Document firstPlace(KakaoPlaceSearchResponse response) {
        return response.documents().get(0);
    }
}