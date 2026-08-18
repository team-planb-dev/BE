package com.planb.query.health.repository;

import com.planb.domain.health.entity.QFoodInfo;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;


@Repository
@RequiredArgsConstructor
public class FoodInfoQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QFoodInfo foodInfo = QFoodInfo.foodInfo;

    public long deleteAllByHealthId(Long healthId) {

        return jpaQueryFactory
                .delete(foodInfo)
                .where(foodInfo
                        .health
                        .id
                        .eq(healthId))
                .execute();
    }
}
