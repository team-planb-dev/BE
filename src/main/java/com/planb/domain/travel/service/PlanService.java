package com.planb.domain.travel.service;

import com.planb.ai.context.TravelPlanContext;
import com.planb.ai.dto.response.CreatePlanAiResponse;
import com.planb.ai.dto.response.PlaceWithRouteResult;
import com.planb.ai.handler.TravelRecommendHandler;
import com.planb.ai.prompt.CafeRecommendPrompt;
import com.planb.domain.travel.dto.request.CreatePlanRequest;
import com.planb.domain.travel.dto.request.CreateTravelRequest;
import com.planb.domain.travel.entity.Plan;
import com.planb.domain.travel.entity.constant.CourseType;
import com.planb.domain.travel.repository.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlanService {

    /*
    Repository
     */
    private final PlanRepository planRepository;

    /*
    Handler
     */
    private final TravelRecommendHandler travelRecommendHandler;

    public Plan createPlan(CreatePlanRequest createPlanRequest){

        return Plan
                .builder()
                .planName(createPlanRequest
                        .planName())
                .travel(createPlanRequest
                        .travel())
                .build();
    }

    // AI로 사용자 입력 및 정보기반 일정 생성하기
    public CreatePlanAiResponse makePlanByAi(TravelPlanContext travelPlanContext){

        CreatePlanAiResponse response =
                travelRecommendHandler.createPlanByAi(travelPlanContext);

        return ensureUniqueCafes(
                response,
                travelPlanContext.createTravelRequest()
        );
    }

    // 여행 전체 기간 CAFE_REST 중복 배치 보정
    // 메인 플랜 생성 프롬프트(STEP 9) 자체검증 대신 Java 레벨 검증
    // 중복 슬롯은 recommendCafe로 재확정, 대체 후보 없으면 슬롯 제거
    private CreatePlanAiResponse ensureUniqueCafes(
            CreatePlanAiResponse response,
            CreateTravelRequest createTravelRequest
    ) {

        Set<String> usedNames = new HashSet<>();

        List<CreatePlanAiResponse.PlanDayDetail> fixedPlanDays =
                response.planDays()
                        .stream()
                        .map(planDay ->
                                fixPlanDay(
                                        planDay,
                                        usedNames,
                                        createTravelRequest
                                )
                        )
                        .toList();

        return new CreatePlanAiResponse(
                response.planName(),
                response.description(),
                fixedPlanDays
        );
    }

    // 하루치 일정 순회, CAFE_REST 중복 보정
    private CreatePlanAiResponse.PlanDayDetail fixPlanDay(
            CreatePlanAiResponse.PlanDayDetail planDay,
            Set<String> usedNames,
            CreateTravelRequest createTravelRequest
    ) {

        List<CreatePlanAiResponse.PlanScheduleDetail> fixedSchedules = new ArrayList<>();
        String previousLocation = "";

        for (CreatePlanAiResponse.PlanScheduleDetail schedule : planDay.schedules()) {

            boolean isDuplicateCafe =
                    schedule.courseType() == CourseType.CAFE_REST
                            && (isBlank(schedule.locationName())
                            || usedNames.contains(schedule.locationName()));

            if (isDuplicateCafe) {

                PlaceWithRouteResult replacement =
                        travelRecommendHandler.recommendCafe(
                                new CafeRecommendPrompt(
                                        createTravelRequest.locationDo(),
                                        createTravelRequest.locationSigungu(),
                                        createTravelRequest.decidedLocation(),
                                        previousLocation,
                                        createTravelRequest.transportation(),
                                        List.copyOf(usedNames)
                                )
                        );

                if (!replacement.found()) {
                    // 대체 카페 없음, 슬롯 미생성
                    // (기존 프롬프트 정책과 동일: 대체 후보 없으면 슬롯 미생성)
                    continue;
                }

                CreatePlanAiResponse.PlanScheduleDetail replaced =
                        new CreatePlanAiResponse.PlanScheduleDetail(
                                schedule.scheduleType(),
                                schedule.courseType(),
                                schedule.startTime(),
                                schedule.endTime(),
                                replacement.placeName(),
                                replacement.address(),
                                replacement.longitude(),
                                replacement.latitude(),
                                null,
                                null,
                                schedule.stayMinutes(),
                                replacement.travelMinutes(),
                                schedule.tags(),
                                schedule.medication(),
                                null
                        );

                usedNames.add(replacement.placeName());
                fixedSchedules.add(replaced);
                previousLocation = firstNonBlank(replacement.placeName(), previousLocation);
                continue;
            }

            if (schedule.courseType() == CourseType.ATTRACTION
                    || schedule.courseType() == CourseType.CAFE_REST) {
                addIfPresent(usedNames, schedule.locationName());
            }

            fixedSchedules.add(schedule);

            // MEDICATION은 실제 이동 지점 아님, previousLocation 갱신 제외
            if (schedule.courseType() != CourseType.MEDICATION) {
                previousLocation = firstNonBlank(schedule.locationName(), previousLocation);
            }
        }

        return new CreatePlanAiResponse.PlanDayDetail(
                planDay.dayNumber(),
                planDay.date(),
                fixedSchedules
        );
    }

    private void addIfPresent(Set<String> usedNames, String name) {
        if (!isBlank(name)) {
            usedNames.add(name);
        }
    }

    private String firstNonBlank(String candidate, String fallback) {
        return isBlank(candidate) ? fallback : candidate;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /*
    기본 CRUD 모음
     */
    public void savePlan(Plan plan){
        planRepository.save(plan);
    }
}