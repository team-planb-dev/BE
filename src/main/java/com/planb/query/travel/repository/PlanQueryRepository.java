package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.QPlan;
import com.planb.query.travel.dto.response.PlanBasicQueryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlanQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QPlan plan = QPlan.plan;

    // TravelId로 Plan(id, name) 조회하기
    // tags는 @ElementCollection이라 이 쿼리에서 제외, PlanQueryService에서 별도 조회
    public PlanBasicQueryResponse findPlanBasicByTravelId(Long travelId) {

        return jpaQueryFactory
                .select(
                        Projections.constructor(
                                PlanBasicQueryResponse.class,
                                plan.id,
                                plan.planName
                        )
                )
                .from(plan)
                .where(
                        plan.travel.id.eq(travelId)
                )
                .fetchOne();

    }
}