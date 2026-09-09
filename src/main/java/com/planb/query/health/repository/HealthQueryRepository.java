package com.planb.query.health.repository;


import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.QFoodInfo;
import com.planb.domain.health.entity.QHealth;
import com.planb.domain.health.entity.constant.FoodType;
import com.querydsl.core.types.Projections;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class HealthQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QHealth health = QHealth.health;
    private final QFoodInfo foodInfo = QFoodInfo.foodInfo;


    public List<HealthSummaryQueryResponse> findHealthSummaryList(Long userId) {

        return findHealthSummaryList(health.user.id.eq(userId));
    }


    /**
     * 여행에 선택된 구성원만 건강 요약으로 조회한다.
     *
     * @param healthIds 조회할 구성원 id
     * @return 구성원 건강 요약 목록
     */
    public List<HealthSummaryQueryResponse> findHealthSummaryListByHealthIds(List<Long> healthIds) {

        if (healthIds.isEmpty()) {
            return List.of();
        }

        return findHealthSummaryList(health.id.in(healthIds));
    }


    private List<HealthSummaryQueryResponse> findHealthSummaryList(BooleanExpression condition) {

        return jpaQueryFactory
                .select(Projections.constructor(HealthSummaryQueryResponse.class,
                        health
                                .id,
                        health
                                .travelerName,
                        health
                                .hasMedication,
                        health
                                .healthInfo
                                .diseaseType,
                        foodInfo
                                .id
                                .count()
                                .gt(0)
                ))
                .from(health)
                .leftJoin(foodInfo)
                .on(
                        foodInfo
                                .health
                                .id.eq(health.id),
                        foodInfo
                                .foodType
                                .eq(FoodType.ALLERGY)
                )
                .where(condition)
                .groupBy(
                        health
                                .id,
                        health
                                .travelerName,
                        health
                                .hasMedication,
                        health
                                .healthInfo
                                .diseaseType
                )
                .fetch();
    }

    public boolean existsByHealthIdAndUserId
            (Long healthId,
             Long userId) {

        return jpaQueryFactory
                .selectOne()
                .from(health)
                .where(
                        health
                                .id
                                .eq(healthId),
                        health
                                .user
                                .id
                                .eq(userId)
                )
                .fetchFirst() != null;
    }


}
