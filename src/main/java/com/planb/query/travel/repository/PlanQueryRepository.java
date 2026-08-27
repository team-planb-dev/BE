package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.QPlan;
import com.planb.query.travel.dto.response.PlanQueryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PlanQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QPlan plan = QPlan.plan;

    // TravelId로 Plan 객체 조회하기
    public PlanQueryResponse findPlanByTravelId(Long travelId) {

        return jpaQueryFactory
                .select(
                        Projections.constructor(
                                PlanQueryResponse.class,
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
