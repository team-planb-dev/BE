// src/main/java/com/planb/global/client/kakaoMapService/handler/KakaoMapServiceHandler.java
package com.planb.global.client.kakaoMapService.handler;

import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.global.client.kakaoMapService.KakaoMapServiceClient;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPlaceSearchResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoPublicTrafficRouteResponse;
import com.planb.global.client.kakaoMapService.dto.response.KakaoRouteFailure;
import com.planb.global.client.kakaoMapService.helper.KakaoMapRouteHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoPlaceSearchHelper;
import com.planb.global.client.kakaoMapService.helper.KakaoSearchKeywordSanitizer;
import com.planb.global.client.kakaoMobilityService.KakaoMobilityServiceClient;
import com.planb.global.client.kakaoMobilityService.dto.response.KakaoCarRouteResponse;
import com.planb.global.client.kakaoMobilityService.helper.KakaoMobilityRouteHelper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.codec.CodecException;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.PrematureCloseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeoutException;

@Slf4j
@Component
@RequiredArgsConstructor
public class KakaoMapServiceHandler {

    private final KakaoMapServiceClient kakaoMapServiceClient;
    private final KakaoMobilityServiceClient kakaoMobilityServiceClient;

    private final KakaoMapRouteHelper kakaoMapRouteHelper;
    private final KakaoMobilityRouteHelper kakaoMobilityRouteHelper;
    private final KakaoSearchKeywordSanitizer kakaoSearchKeywordSanitizer;
    private final KakaoPlaceSearchHelper kakaoPlaceSearchHelper;
    private final MeterRegistry meterRegistry;

    // 공급자 오류 메시지의 로그·진단 보존 길이 상한
    private static final int MAX_PROVIDER_MESSAGE_LENGTH = 120;

    private static final int MAX_CAUSE_DEPTH = 10;

    private static final JsonMapper ERROR_MAPPER = JsonMapper
            .builder()
            .build();

    // 카카오맵 API : 키워드 기반 장소 검색
    public Mono<KakaoPlaceSearchResponse> searchPlace(
            String keyword
    ) {

        String sanitizedKeyword = kakaoSearchKeywordSanitizer.sanitize(keyword);

        return kakaoMapServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v2/local/search/keyword.json")
                        .queryParam(
                                "query",
                                sanitizedKeyword
                        )
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMapServiceClient.serviceKey()
                ),
                KakaoPlaceSearchResponse.class
        );
    }

    // 카카오맵 API : 대중교통 경로 조회
    public Mono<KakaoPublicTrafficRouteResponse> getPublicTrafficRoute(
            String startX,
            String startY,
            String endX,
            String endY
    ) {

        return kakaoMapServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v2/routing/publictraffic")
                        .queryParam("start_x", startX)
                        .queryParam("start_y", startY)
                        .queryParam("end_x", endX)
                        .queryParam("end_y", endY)
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMapServiceClient.serviceKey()
                ),
                KakaoPublicTrafficRouteResponse.class
        );
    }

    // 카카오모빌리티 API : 자동차 경로 조회
    public Mono<KakaoCarRouteResponse> getCarRoute(
            String startX,
            String startY,
            String endX,
            String endY
    ) {

        return kakaoMobilityServiceClient.get(
                uriBuilder -> uriBuilder
                        .path("/v1/directions")
                        .queryParam("origin", startX + "," + startY)
                        .queryParam("destination", endX + "," + endY)
                        .queryParam("priority", "TIME")
                        .queryParam("summary", true)
                        .build(),
                headers -> headers.set(
                        "Authorization",
                        "KakaoAK "
                                + kakaoMobilityServiceClient.serviceKey()
                ),
                KakaoCarRouteResponse.class
        );
    }

    // 장소 검색 후 이동수단에 따른 경로 조회
    // 장소를 찾지 못하거나 경로 조회(대중교통/자동차)가 실패해도 예외를 던지지 않고,
    // travelMinutes/distanceMeters가 null인 결과로 대체(STEP 7 정책)
    public Mono<KakaoRouteResult> getRoute(
            String origin,
            String destination,
            Transportation transportation
    ) {

        return getRoute(
                origin,
                destination,
                transportation,
                null,
                null,
                null,
                null
        );
    }

    // 확정 좌표 우선 사용과 좌표 누락 지점의 이름 검색
    // 실패 시 단계·유형·공급자 응답을 담은 실패 결과 반환
    public Mono<KakaoRouteResult> getRoute(
            String origin,
            String destination,
            Transportation transportation,
            String originX,
            String originY,
            String destinationX,
            String destinationY
    ) {

        return Mono
                .zip(
                        routeCoordinates(
                                origin,
                                originX,
                                originY
                        ),
                        routeCoordinates(
                                destination,
                                destinationX,
                                destinationY
                        ))
                .flatMap(points -> route(
                        origin,
                        destination,
                        transportation,
                        new KakaoMapRouteHelper.RoutePoints(
                                points
                                        .getT1()
                                        .get(0),
                                points
                                        .getT1()
                                        .get(1),
                                points
                                        .getT2()
                                        .get(0),
                                points
                                        .getT2()
                                        .get(1)
                        )
                ))
                .doOnNext(route -> recordRoute(
                        transportation,
                        "ok"
                ))
                .onErrorResume(exception -> {
                    KakaoRouteFailure failure = exception instanceof RouteLookupException lookup
                            ? lookup.failure
                            : routeFailure(
                                    KakaoRouteFailure.Stage.ROUTE,
                                    exception,
                                    null
                            );

                    // 좌표 검색 실패의 확정 입력 좌표 보존
                    if (failure.stage() == KakaoRouteFailure.Stage.POINT_SEARCH) {
                        failure = new KakaoRouteFailure(
                                failure.stage(),
                                failure.kind(),
                                failure.httpStatus(),
                                failure.providerCode(),
                                failure.providerMessage(),
                                failure.exceptionType(),
                                originX,
                                originY,
                                destinationX,
                                destinationY
                        );
                    }

                    recordRoute(
                            transportation,
                            outcome(failure)
                    );

                    log.warn(
                            "[ROUTE LOOKUP FAILED] origin={}, destination={}, transportation={}, stage={}, kind={}, "
                                    + "status={}, providerCode={}, providerMessage={}, exceptionType={}, "
                                    + "requestStart={},{}, requestEnd={},{}",
                            origin,
                            destination,
                            transportation,
                            failure.stage(),
                            failure.kind(),
                            failure.httpStatus(),
                            failure.providerCode(),
                            failure.providerMessage(),
                            failure.exceptionType(),
                            failure.startX(),
                            failure.startY(),
                            failure.endX(),
                            failure.endY()
                    );

                    return Mono.just(new KakaoRouteResult(
                            origin,
                            destination,
                            null,
                            null,
                            failure
                    ));
                });
    }

    // 확정 좌표의 이동수단별 경로 요청과 유효 이동시간 확인
    private Mono<KakaoRouteResult> route(
            String origin,
            String destination,
            Transportation transportation,
            KakaoMapRouteHelper.RoutePoints points
    ) {

        Mono<KakaoRouteResult> route = switch (transportation) {
            case TRANSIT -> getPublicTrafficRoute(
                    points.startX(),
                    points.startY(),
                    points.endX(),
                    points.endY()
            )
                    .map(response -> {
                        try {
                            return kakaoMapRouteHelper.makePublicTrafficRouteResult(
                                    origin,
                                    destination,
                                    response,
                                    points
                            );
                        } catch (IllegalStateException exception) {
                            throw noResult(
                                    response.status(),
                                    null,
                                    points,
                                    exception
                            );
                        }
                    });

            case CAR -> getCarRoute(
                    points.startX(),
                    points.startY(),
                    points.endX(),
                    points.endY()
            )
                    .map(response -> {
                        try {
                            return kakaoMobilityRouteHelper.makeCarRouteResult(
                                    origin,
                                    destination,
                                    response
                            );
                        } catch (IllegalStateException exception) {
                            KakaoCarRouteResponse.Route first = response.routes() == null || response
                                    .routes()
                                    .isEmpty()
                                    ? null
                                    : response
                                            .routes()
                                            .getFirst();

                            throw noResult(
                                    first == null || first.result_code() == null
                                            ? null
                                            : String.valueOf(first.result_code()),
                                    first == null
                                            ? null
                                            : first.result_msg(),
                                    points,
                                    exception
                            );
                        }
                    });
        };

        return route
                .switchIfEmpty(Mono.error(new IllegalStateException("경로 조회 응답 없음")))
                .map(result -> {
                    if (result.travelMinutes() == null || result.travelMinutes() < 0) {
                        throw new RouteLookupException(new KakaoRouteFailure(
                                KakaoRouteFailure.Stage.ROUTE,
                                KakaoRouteFailure.Kind.INVALID_MINUTES,
                                null,
                                null,
                                null,
                                null,
                                points.startX(),
                                points.startY(),
                                points.endX(),
                                points.endY()
                        ));
                    }

                    return result;
                })
                .onErrorMap(
                        exception -> !(exception instanceof RouteLookupException),
                        exception -> new RouteLookupException(routeFailure(
                                KakaoRouteFailure.Stage.ROUTE,
                                exception,
                                points
                        ))
                );
    }

    // 경로 조회 시도 결과(좌표 검색 단계 포함), 논리 구간 결과와 별도 계수
    private void recordRoute(
            Transportation transportation,
            String outcome
    ) {

        Counter
                .builder("planb.external.kakao.route")
                .tag(
                        "transportation",
                        transportation.name()
                )
                .tag(
                        "outcome",
                        outcome
                )
                .register(meterRegistry)
                .increment();
    }

    // 고정 범위의 단계·유형·HTTP 상태 결과값
    private static String outcome(KakaoRouteFailure failure) {

        String stage = failure.stage() == KakaoRouteFailure.Stage.ROUTE
                ? "route"
                : "point_search";

        return failure.kind() == KakaoRouteFailure.Kind.HTTP_ERROR
                ? stage + "_http_" + failure.httpStatus()
                : stage + "_" + failure
                        .kind()
                        .name()
                        .toLowerCase(Locale.ROOT);
    }

    // 200 응답 경로 없음, 공급자 상태·결과 코드 보존
    private static RouteLookupException noResult(
            String providerCode,
            String providerMessage,
            KakaoMapRouteHelper.RoutePoints points,
            IllegalStateException exception
    ) {

        return new RouteLookupException(new KakaoRouteFailure(
                KakaoRouteFailure.Stage.ROUTE,
                KakaoRouteFailure.Kind.NO_RESULT,
                null,
                providerCode,
                providerMessage == null ? null : safeMessage(providerMessage),
                exception
                        .getClass()
                        .getSimpleName(),
                points.startX(),
                points.startY(),
                points.endX(),
                points.endY()
        ));
    }

    // 예외 유형별 실패 분류, 응답 본문은 공급자 코드·제한 길이 메시지만 보존
    // 2xx 본문 단계 오류는 HTTP 오류가 아닌 원인 기준 분류
    private static KakaoRouteFailure routeFailure(
            KakaoRouteFailure.Stage stage,
            Throwable exception,
            KakaoMapRouteHelper.RoutePoints points
    ) {

        KakaoRouteFailure.Kind kind;
        Integer status = null;
        String code = null;
        String message = null;

        if (hasCause(
                exception,
                TimeoutException.class
        )
                || hasCause(
                        exception,
                        io.netty.handler.timeout.TimeoutException.class
                )) {
            kind = KakaoRouteFailure.Kind.TIMEOUT;
        } else if (exception instanceof WebClientResponseException response
                && response
                        .getStatusCode()
                        .isError()) {
            kind = KakaoRouteFailure.Kind.HTTP_ERROR;
            status = response
                    .getStatusCode()
                    .value();

            ProviderError error = providerError(response.getResponseBodyAsString());

            code = error.code();
            message = error.message();
        } else if (exception instanceof CodecException
                || hasCause(
                        exception,
                        DataBufferLimitException.class
                )) {
            kind = KakaoRouteFailure.Kind.DECODE;
        } else if (exception instanceof WebClientRequestException
                || hasCause(
                        exception,
                        PrematureCloseException.class
                )) {
            kind = KakaoRouteFailure.Kind.CONNECTION;
        } else if (exception instanceof IllegalArgumentException) {
            kind = KakaoRouteFailure.Kind.MISSING_POINT;
        } else if (exception instanceof IllegalStateException) {
            kind = KakaoRouteFailure.Kind.NO_RESULT;
        } else {
            kind = KakaoRouteFailure.Kind.OTHER;
        }

        return new KakaoRouteFailure(
                stage,
                kind,
                status,
                code,
                message,
                rootCause(exception)
                        .getClass()
                        .getSimpleName(),
                points == null ? null : points.startX(),
                points == null ? null : points.startY(),
                points == null ? null : points.endX(),
                points == null ? null : points.endY()
        );
    }

    private static boolean hasCause(
            Throwable exception,
            Class<? extends Throwable> type
    ) {

        Throwable current = exception;

        // 순환 원인 대비 탐색 깊이 상한
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (type.isInstance(current)) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    // 래핑 예외 아래의 실제 원인
    private static Throwable rootCause(Throwable exception) {

        Throwable current = exception;

        for (int depth = 0; current.getCause() != null && depth < MAX_CAUSE_DEPTH; depth++) {
            current = current.getCause();
        }

        return current;
    }

    // 공통 오류 형식(code·msg), 로컬 API 형식(errorType·message), 비JSON·알 수 없는 구조는 코드 없이 제한 길이 메시지
    private static ProviderError providerError(String body) {

        if (body == null || body.isBlank()) {
            return new ProviderError(
                    null,
                    null
            );
        }

        try {
            JsonNode node = ERROR_MAPPER.readTree(body);

            if (node != null && node.isObject()) {
                JsonNode code = node.has("code")
                        ? node.get("code")
                        : node.get("errorType");
                JsonNode message = node.has("msg")
                        ? node.get("msg")
                        : node.get("message");

                return new ProviderError(
                        code == null || !code.isValueNode()
                                ? null
                                : code.asString(),
                        message == null || !message.isValueNode()
                                ? null
                                : safeMessage(message.asString())
                );
            }
        } catch (RuntimeException exception) {
            // 비JSON 본문의 제한 길이 메시지 보존
        }

        return new ProviderError(
                null,
                safeMessage(body)
        );
    }

    // 인증값 마스킹·공백 정리·길이 제한
    private static String safeMessage(String message) {

        String masked = message
                .replaceAll(
                        "(?i)(KakaoAK|Bearer)\\s+\\S+",
                        "$1 <REDACTED>"
                )
                .replaceAll(
                        "\\s+",
                        " "
                )
                .strip();

        return masked.length() <= MAX_PROVIDER_MESSAGE_LENGTH
                ? masked
                : masked.substring(
                        0,
                        MAX_PROVIDER_MESSAGE_LENGTH
                );
    }

    private record ProviderError(

            String code,
            String message
    ) {
    }

    // 단계별 실패 정보 전달용 내부 예외, stack trace 미생성
    private static final class RouteLookupException extends RuntimeException {

        private final transient KakaoRouteFailure failure;

        private RouteLookupException(KakaoRouteFailure failure) {

            super(
                    failure
                            .kind()
                            .name(),
                    null,
                    false,
                    false
            );

            this.failure = failure;
        }
    }

    private Mono<List<String>> routeCoordinates(
            String name,
            String x,
            String y
    ) {

        return Mono
                .defer(() -> {
                    if (x != null && !x.isBlank() && y != null && !y.isBlank()) {
                        return Mono.just(List.of(x, y));
                    }
                    if (name == null || name.isBlank()) {
                        return Mono.error(new IllegalArgumentException("경로 조회 지점의 이름과 좌표 누락"));
                    }
                    return searchPlace(name)
                            .map(response -> kakaoMapRouteHelper.getFirstPlace(response, name))
                            .map(place -> List.of(place.x(), place.y()))
                            .switchIfEmpty(Mono.error(new IllegalStateException("경로 조회 지점 검색 결과 없음: " + name)));
                })
                .onErrorMap(
                        exception -> !(exception instanceof RouteLookupException),
                        exception -> new RouteLookupException(routeFailure(
                                KakaoRouteFailure.Stage.POINT_SEARCH,
                                exception,
                                null
                        ))
                );
    }

    // 실제 장소(카페 또는 TourAPI에서 검색되지 않는 관광지) 존재 확인
    // + 이전 장소로부터의 이동시간 조회
    // excludeNames와 일치하는 장소는 이미 사용된 것으로 간주, found=false로 처리
    public Mono<PlaceWithRouteResult> findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames,
            String categoryCode,
            String previousLongitude,
            String previousLatitude
    ) {

        return findPlaceWithRoute(
                keyword,
                previousLocation,
                transportation,
                excludeNames,
                categoryCode,
                previousLongitude,
                previousLatitude,
                false
        );
    }

    public Mono<PlaceWithRouteResult> findPlaceWithRoute(
            String keyword,
            String previousLocation,
            Transportation transportation,
            List<String> excludeNames,
            String categoryCode,
            String previousLongitude,
            String previousLatitude,
            boolean parksOnly
    ) {

        return searchPlace(keyword)
                .map(response -> kakaoPlaceSearchHelper.filterByCategory(
                        response,
                        categoryCode
                ))
                .map(response -> parksOnly
                        ? kakaoPlaceSearchHelper.filterParks(response, excludeNames)
                        : response)
                .filter(kakaoPlaceSearchHelper::hasResult)
                .filter(response -> !kakaoPlaceSearchHelper.isExcluded(response, excludeNames))
                .flatMap(response ->
                        travelMinutesFrom(
                                previousLocation,
                                previousLongitude,
                                previousLatitude,
                                response,
                                transportation
                        )
                                .map(minutes -> kakaoPlaceSearchHelper.toResult(response, minutes))
                                .switchIfEmpty(Mono.fromSupplier(() ->
                                        kakaoPlaceSearchHelper.toResult(response, null)))
                )
                .defaultIfEmpty(kakaoPlaceSearchHelper.notFound());
    }

    /**
     * 확정 좌표에 따른 경로 이동시간 조회
     */
    private Mono<Integer> travelMinutesFrom(
            String previousLocation,
            String previousLongitude,
            String previousLatitude,
            KakaoPlaceSearchResponse response,
            Transportation transportation
    ) {

        return Mono
                .justOrEmpty(previousLocation)
                .filter(location -> !location.isBlank())
                .flatMap(location ->
                        getRoute(
                                location,
                                kakaoPlaceSearchHelper.firstPlaceName(response),
                                transportation,
                                previousLongitude,
                                previousLatitude,
                                kakaoPlaceSearchHelper.firstPlaceLongitude(response),
                                kakaoPlaceSearchHelper.firstPlaceLatitude(response)
                        ))
                .flatMap(route -> Mono.justOrEmpty(route.travelMinutes()))
                .onErrorResume(e -> Mono.empty());
    }
}
