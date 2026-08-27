package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.PlanSchedule;
import com.planb.domain.travel.entity.QPlanSchedule;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PlanScheduleQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QPlanSchedule planSchedule = QPlanSchedule.planSchedule;

    // PlanDayId를 기반으로 PlanSchedule 리스트 가져오기
    public List<PlanSchedule> findPlanSchedulesByPlanDayIds
            (List<Long> planDayIds) {

        return jpaQueryFactory
                .selectFrom(planSchedule)
                .distinct()
                .leftJoin(
                        planSchedule
                                .tags
                )
                .fetchJoin()
                .where(
                        planSchedule
                                .planDay
                                .id
                                .in(planDayIds)
                )
                .orderBy(
                        planSchedule
                                .planDay
                                .id
                                .asc(),
                        planSchedule
                                .startTime
                                .asc()
                )
                .fetch();
    }
}
