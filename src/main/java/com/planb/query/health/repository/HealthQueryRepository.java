package com.planb.query.health.repository;


import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.QFoodInfo;
import com.planb.domain.health.entity.QHealth;
import com.planb.domain.health.entity.constant.FoodType;
import com.querydsl.core.types.Projections;
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
                .where(
                        health
                                .user
                                .id
                                .eq(userId)
                )
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
