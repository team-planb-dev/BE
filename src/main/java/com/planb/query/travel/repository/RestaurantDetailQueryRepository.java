package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.QRestaurantDetail;
import com.planb.query.travel.dto.response.RestaurantDetailQueryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class RestaurantDetailQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QRestaurantDetail restaurantDetail = QRestaurantDetail.restaurantDetail;

    // PlanScheduleId 조합들로 RestaurantDetail 객체 조회하기
    public List<RestaurantDetailQueryResponse> findRestaurantDetailsByPlanScheduleIds
            (List<Long> planScheduleIds) {

        return jpaQueryFactory
                .select(
                        Projections.constructor(
                                RestaurantDetailQueryResponse.class,
                                restaurantDetail
                                        .planSchedule.id,
                                restaurantDetail
                                        .menuName,
                                restaurantDetail
                                        .carbohydrate,
                                restaurantDetail
                                        .sodium,
                                restaurantDetail
                                        .fat,
                                restaurantDetail
                                        .openTime,
                                restaurantDetail
                                        .address,
                                restaurantDetail
                                        .longitude,
                                restaurantDetail
                                        .latitude,
                                restaurantDetail
                                        .imageUrl
                        )
                )
                .from(restaurantDetail)
                .where(
                        restaurantDetail
                                .planSchedule
                                .id
                                .in(planScheduleIds)
                )
                .fetch();
    }
}
