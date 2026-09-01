package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.QTravel;
import com.planb.domain.user.entity.QUser;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TravelQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QTravel travel = QTravel.travel;
    private final QUser user = QUser.user;

    // travelId로 여행조건 리스트를 조회
    public TravelConditionQueryResponse findTravelConditionById(Long travelId) {

        return jpaQueryFactory
                .select(
                        Projections.constructor(
                                TravelConditionQueryResponse.class,
                                travel
                                        .travelStyle,
                                travel
                                        .travelTheme
                        )
                )
                .from(travel)
                .where(
                        travel
                                .id
                                .eq(travelId)
                )
                .fetchOne();
    }

    // travelId가 해당 userId 소유인지 확인
    public boolean existsByIdAndUserId(Long travelId, Long userId) {

        Integer result = jpaQueryFactory
                .selectOne()
                .from(travel)
                .where(
                        travel
                                .id
                                .eq(travelId),
                        travel
                                .user
                                .id
                                .eq(userId)
                )
                .fetchFirst();

        return result != null;
    }
}
