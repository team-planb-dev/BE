package com.planb.query.travel.repository;

import com.planb.domain.travel.entity.QPlan;
import com.planb.domain.travel.entity.QPlanDay;
import com.planb.domain.travel.entity.QPlanSchedule;
import com.planb.domain.travel.entity.QTravel;
import com.planb.domain.travel.entity.constant.TravelListFilter;
import com.planb.domain.user.entity.QUser;
import com.planb.query.travel.dto.response.TravelConditionQueryResponse;
import com.planb.query.travel.dto.response.TravelListItemQueryResponse;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.OrderSpecifier;
import com.querydsl.core.types.Projections;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class TravelQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QTravel travel = QTravel.travel;
    private final QUser user = QUser.user;
    private final QPlan plan = QPlan.plan;
    private final QPlanDay planDay = QPlanDay.planDay;
    private final QPlanSchedule planSchedule = QPlanSchedule.planSchedule;

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

    /**
     * 사용자의 여행 목록을 탭 기준으로 조회한다.
     *
     * 다가오는 여행은 시작일이 가까운 순, 지난 여행은 최근에 끝난 순으로 정렬한다.
     *
     * @param userId 조회할 사용자 id
     * @param filter 목록 탭 구분
     * @param today  진행 상태 판정 기준일
     * @return 여행 목록
     */
    public List<TravelListItemQueryResponse> findAllByUserId(
            Long userId,
            TravelListFilter filter,
            LocalDate today
    ) {

        boolean past = filter == TravelListFilter.PAST;

        OrderSpecifier<LocalDate> order = past
                ? travel.endDate.desc()
                : travel.startDate.asc();

        return jpaQueryFactory
                .select(
                        Projections.constructor(
                                TravelListItemQueryResponse.class,
                                travel
                                        .id,
                                travel
                                        .travelName,
                                travel
                                        .locationDo,
                                travel
                                        .locationSigungu,
                                travel
                                        .startDate,
                                travel
                                        .endDate
                        )
                )
                .from(travel)
                .where(
                        travel
                                .user
                                .id
                                .eq(userId),
                        past
                                ? travel.endDate.before(today)
                                : travel.endDate.goe(today)
                )
                .orderBy(order)
                .fetch();
    }


    /**
     * 여행별 목록 썸네일로 사용할 첫 번째 일정 이미지를 조회한다.
     *
     * 여행마다 한 번씩 조회하면 N+1이 되므로 한 번에 가져와 Java에서 여행별 첫 값만 남긴다.
     *
     * @param travelIds 썸네일이 필요한 여행 id 목록
     * @return 여행 id별 이미지 URL
     */
    public Map<Long, String> findThumbnailUrlsByTravelIds(List<Long> travelIds) {

        if (travelIds.isEmpty()) {
            return Map.of();
        }

        List<Tuple> rows = jpaQueryFactory
                .select(
                        travel
                                .id,
                        planSchedule
                                .imageUrl
                )
                .from(planSchedule)
                .join(planSchedule.planDay, planDay)
                .join(planDay.plan, plan)
                .join(plan.travel, travel)
                .where(
                        travel
                                .id
                                .in(travelIds),
                        planSchedule
                                .imageUrl
                                .isNotNull(),
                        planSchedule
                                .imageUrl
                                .ne("")
                )
                .orderBy(travel.id.asc(),
                        planDay.dayNumber.asc(),
                        planSchedule.startTime.asc())
                .fetch();

        Map<Long, String> thumbnailUrls = new LinkedHashMap<>();

        rows
                .forEach(row -> thumbnailUrls
                        .putIfAbsent(
                                row.get(travel.id),
                                row.get(planSchedule.imageUrl)
                        ));

        return thumbnailUrls;
    }


    /**
     * 공유 토큰으로 여행 id를 조회한다.
     *
     * @param shareToken 공유 링크 토큰
     * @return 토큰에 해당하는 여행 id
     */
    public Optional<Long> findTravelIdByShareToken(String shareToken) {

        return Optional.ofNullable(
                jpaQueryFactory
                        .select(travel.id)
                        .from(travel)
                        .where(travel
                                .shareToken
                                .eq(shareToken))
                        .fetchOne()
        );
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
