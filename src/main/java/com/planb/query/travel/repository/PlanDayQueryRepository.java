package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.QPlanDay;
import com.planb.query.travel.dto.response.PlanDayQueryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlanDayQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QPlanDay planDay = QPlanDay.planDay;

    // Plan기반으로 PlanDay 리스트 조회하기
    public List<PlanDayQueryResponse> findPlanDaysByPlanId(Long planId) {

        return jpaQueryFactory
                .select(
                        Projections.constructor(
                                PlanDayQueryResponse.class,
                                planDay
                                        .id,
                                planDay
                                        .dayNumber,
                                planDay
                                        .planDate
                        )
                )
                .from(planDay)
                .where(
                        planDay
                                .plan
                                .id
                                .eq(planId)
                )
                .orderBy(
                        planDay
                                .dayNumber
                                .asc()
                )
                .fetch();

    }
}
