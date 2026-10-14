package com.planb.domain.travel.service;

import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.KakaoRouteResult;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.entity.constant.Transportation;
import com.planb.domain.travel.policy.NearbyWalkEstimate;
import com.planb.global.client.kakaoMapService.dto.response.KakaoRouteFailure;
import com.planb.global.client.kakaoMapService.handler.KakaoMapServiceHandler;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 일정 이동시간 확정
 *
 * 누락 구간 수집 → 같은 구간 1회 조회(요청 범위 메모이즈) → 상한 내 병렬 조회
 * → 조회 실패이면서 값이 비어 있는 구간만 500m 이하 도보 추정 → 결과 반영
 * 원거리 조회 실패는 값 누락을 유지해 후속 검증의 명시적 실패 계약 보존
 */
@Slf4j
@Component
public class TravelMinutesResolver {

    private final KakaoMapServiceHandler kakaoMapServiceHandler;

    private final int lookupConcurrency;

    private final MeterRegistry meterRegistry;

    // 지표 저장소 없는 수동 생성
    public TravelMinutesResolver(
            KakaoMapServiceHandler kakaoMapServiceHandler,
            int lookupConcurrency
    ) {

        this(
                kakaoMapServiceHandler,
                lookupConcurrency,
                new SimpleMeterRegistry()
        );
    }

    @Autowired
    public TravelMinutesResolver(
            KakaoMapServiceHandler kakaoMapServiceHandler,
            @Value("${planb.travel.route.lookup-concurrency:4}") int lookupConcurrency,
            MeterRegistry meterRegistry
    ) {

        this.kakaoMapServiceHandler = kakaoMapServiceHandler;
        this.lookupConcurrency = Math.max(
                1,
                lookupConcurrency
        );
        this.meterRegistry = meterRegistry;
    }

    /**
     * 이동시간 보정 결과와 조회 후에도 누락된 구간
     */
    public record Filled(

            CreatePlanAiResponse response,
            List<MissingSegment> missing
    ) {
    }

    /**
     * 조회 후 누락 구간과 공급자 실패, 실패 정보 없는 결과는 null
     */
    public record MissingSegment(

            int dayNumber,
            String origin,
            String destination,
            KakaoRouteFailure failure
    ) {
    }

    /**
     * 단일 구간 이동시간과 실패 시 공급자 실패
     */
    public record SegmentMinutes(

            Optional<Integer> minutes,
            KakaoRouteFailure failure
    ) {
    }

    // 직전 확정 장소를 이어가며 누락(null·0)된 이동시간 채움
    public CreatePlanAiResponse fill(
            CreatePlanAiResponse response,
            Transportation transportation,
            RouteAnchor anchor
    ) {

        return fillReporting(
                response,
                transportation,
                anchor
        )
                .response();
    }

    // 이동시간 보정과 조회 후 누락 구간·실패 사유 보고
    public Filled fillReporting(
            CreatePlanAiResponse response,
            Transportation transportation,
            RouteAnchor anchor
    ) {

        Map<Segment, KakaoRouteResult> resolved = resolve(
                segments(
                        response,
                        anchor
                ),
                transportation
        );

        List<CreatePlanAiResponse.PlanDayDetail> days = new ArrayList<>();
        List<MissingSegment> missing = new ArrayList<>();
        String previousLocation = anchor.previousLocation();
        CreatePlanAiResponse.PlanScheduleDetail previousPlace = anchor.previousPlace();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            List<CreatePlanAiResponse.PlanScheduleDetail> schedules = new ArrayList<>();

            for (CreatePlanAiResponse.PlanScheduleDetail schedule : day.schedules()) {
                Segment segment = Segment.of(
                        previousLocation,
                        previousPlace,
                        schedule
                );

                boolean lookedUp = needsLookup(
                        schedule,
                        previousLocation
                );

                KakaoRouteResult route = lookedUp
                        ? resolved.get(segment)
                        : null;

                Optional<Integer> routeMinutes = validMinutes(route);

                // 조회 실패 시 원래 값이 0이면 유지(기존 계약), 비어 있을 때만 근거리 도보 추정
                Optional<Integer> minutes = lookedUp
                        ? routeMinutes.or(() -> schedule.travelMinutes() == null
                                ? walkEstimate(segment)
                                : Optional.empty())
                        : Optional.empty();

                // 장소 일정만 논리 구간으로 계수, 누락 보고와 같은 분모
                if (lookedUp && isPlace(schedule)) {
                    recordSegment(
                            transportation,
                            routeMinutes.isPresent()
                                    ? "resolved"
                                    : minutes.isPresent()
                                            ? "walk_estimate"
                                            : schedule.travelMinutes() == null
                                                    ? "missing"
                                                    : "kept_original"
                    );

                    // 최종 누락만 보고, 이동시간 검증 범위와 동일
                    if (minutes.isEmpty() && schedule.travelMinutes() == null) {
                        missing.add(new MissingSegment(
                                day.dayNumber(),
                                segment.origin(),
                                segment.destination(),
                                route == null ? null : route.failure()
                        ));
                    }
                }

                schedules.add(minutes
                        .map(value -> withTravelMinutes(
                                schedule,
                                value
                        ))
                        .orElse(schedule));

                if (isPlace(schedule)) {
                    previousLocation = schedule.locationName();
                    previousPlace = schedule;
                }
            }

            days.add(new CreatePlanAiResponse.PlanDayDetail(
                    day.dayNumber(),
                    day.date(),
                    schedules
            ));
        }

        return new Filled(
                new CreatePlanAiResponse(days),
                List.copyOf(missing)
        );
    }

    // 단일 구간 이동시간, 조회 실패 시 근거리 도보 추정
    public Optional<Integer> minutes(
            String previousLocation,
            CreatePlanAiResponse.PlanScheduleDetail previousPlace,
            CreatePlanAiResponse.PlanScheduleDetail destination,
            Transportation transportation
    ) {

        return lookup(
                previousLocation,
                previousPlace,
                destination,
                transportation
        )
                .minutes();
    }

    // 단일 구간 이동시간과 실패 시 공급자 실패
    public SegmentMinutes lookup(
            String previousLocation,
            CreatePlanAiResponse.PlanScheduleDetail previousPlace,
            CreatePlanAiResponse.PlanScheduleDetail destination,
            Transportation transportation
    ) {

        Segment segment = Segment.of(
                previousLocation,
                previousPlace,
                destination
        );

        KakaoRouteResult route = resolve(
                List.of(segment),
                transportation
        )
                .get(segment);

        Optional<Integer> routeMinutes = validMinutes(route);

        Optional<Integer> minutes = routeMinutes.or(() -> walkEstimate(segment));

        recordSegment(
                transportation,
                routeMinutes.isPresent()
                        ? "resolved"
                        : minutes.isPresent()
                                ? "walk_estimate"
                                : "missing"
        );

        return new SegmentMinutes(
                minutes,
                minutes.isPresent() || route == null
                        ? null
                        : route.failure()
        );
    }

    private static Optional<Integer> validMinutes(KakaoRouteResult route) {

        return route == null
                ? Optional.empty()
                : Optional
                        .ofNullable(route.travelMinutes())
                        .filter(value -> value >= 0);
    }

    // 논리 구간별 결과, 공급자 호출 지표와 별도 분모
    private void recordSegment(
            Transportation transportation,
            String result
    ) {

        Counter
                .builder("planb.travel.route.segment")
                .tag(
                        "transportation",
                        transportation.name()
                )
                .tag(
                        "result",
                        result
                )
                .register(meterRegistry)
                .increment();
    }

    private List<Segment> segments(
            CreatePlanAiResponse response,
            RouteAnchor anchor
    ) {

        List<Segment> segments = new ArrayList<>();
        String previousLocation = anchor.previousLocation();
        CreatePlanAiResponse.PlanScheduleDetail previousPlace = anchor.previousPlace();

        for (CreatePlanAiResponse.PlanDayDetail day : response.planDays()) {
            for (CreatePlanAiResponse.PlanScheduleDetail schedule : day.schedules()) {
                if (needsLookup(
                        schedule,
                        previousLocation
                )) {
                    segments.add(Segment.of(
                            previousLocation,
                            previousPlace,
                            schedule
                    ));
                }

                if (isPlace(schedule)) {
                    previousLocation = schedule.locationName();
                    previousPlace = schedule;
                }
            }
        }

        return segments;
    }

    // 중복 구간 제거 후 상한 내 병렬 조회
    private Map<Segment, KakaoRouteResult> resolve(
            List<Segment> segments,
            Transportation transportation
    ) {

        List<Segment> distinct = segments
                .stream()
                .distinct()
                .toList();

        if (distinct.isEmpty()) {
            return Map.of();
        }

        Map<Segment, KakaoRouteResult> resolved = Flux
                .fromIterable(distinct)
                .flatMap(
                        segment -> request(
                                segment,
                                transportation
                        ),
                        lookupConcurrency
                )
                .collectMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        LinkedHashMap::new
                )
                .block();

        return resolved == null
                ? Map.of()
                : resolved;
    }

    private Mono<Map.Entry<Segment, KakaoRouteResult>> request(
            Segment segment,
            Transportation transportation
    ) {

        return kakaoMapServiceHandler
                .getRoute(
                        segment.origin(),
                        segment.destination(),
                        transportation,
                        segment.originLongitude(),
                        segment.originLatitude(),
                        segment.destinationLongitude(),
                        segment.destinationLatitude()
                )
                .defaultIfEmpty(new KakaoRouteResult(
                        segment.origin(),
                        segment.destination(),
                        null,
                        null
                ))
                .map(route -> Map.entry(
                        segment,
                        route
                ));
    }

    private Optional<Integer> walkEstimate(Segment segment) {

        Optional<Integer> estimate = NearbyWalkEstimate.minutes(
                segment.originLongitude(),
                segment.originLatitude(),
                segment.destinationLongitude(),
                segment.destinationLatitude()
        );

        estimate.ifPresent(minutes -> log.info(
                "[ROUTE WALK ESTIMATE] origin={}, destination={}, minutes={}",
                segment.origin(),
                segment.destination(),
                minutes
        ));

        return estimate;
    }

    private static boolean needsLookup(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            String previousLocation
    ) {

        return (schedule.travelMinutes() == null || schedule.travelMinutes() == 0)
                && !isBlank(schedule.locationName())
                && !isBlank(previousLocation);
    }

    private static boolean isPlace(CreatePlanAiResponse.PlanScheduleDetail schedule) {

        return schedule.courseType() != CourseType.MEDICATION
                && schedule.courseType() != CourseType.TRANSPORTATION
                && !isBlank(schedule.locationName());
    }

    private static boolean isBlank(String value) {

        return value == null || value.isBlank();
    }

    private static CreatePlanAiResponse.PlanScheduleDetail withTravelMinutes(
            CreatePlanAiResponse.PlanScheduleDetail schedule,
            int travelMinutes
    ) {

        return new CreatePlanAiResponse.PlanScheduleDetail(
                schedule.scheduleType(),
                schedule.courseType(),
                schedule.startTime(),
                schedule.endTime(),
                schedule.locationName(),
                schedule.location(),
                schedule.longitude(),
                schedule.latitude(),
                schedule.imageUrl(),
                schedule.thumbNailImageUrl(),
                schedule.stayMinutes(),
                travelMinutes,
                schedule.tags(),
                schedule.medication(),
                schedule.restaurantDetail(),
                schedule.candidateId()
        );
    }

    // 출발·도착 이름과 좌표로 식별하는 경로 구간
    private record Segment(
            String origin,
            String originLongitude,
            String originLatitude,
            String destination,
            String destinationLongitude,
            String destinationLatitude
    ) {

        static Segment of(
                String previousLocation,
                CreatePlanAiResponse.PlanScheduleDetail previousPlace,
                CreatePlanAiResponse.PlanScheduleDetail destination
        ) {

            return new Segment(
                    previousLocation,
                    previousPlace == null ? null : previousPlace.longitude(),
                    previousPlace == null ? null : previousPlace.latitude(),
                    destination.locationName(),
                    destination.longitude(),
                    destination.latitude()
            );
        }
    }
}
