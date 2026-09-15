package com.planb.query.health.repository;


import com.planb.domain.health.dto.response.HealthSummaryQueryResponse;
import com.planb.domain.health.entity.Health;
import com.planb.domain.health.entity.constant.DiseaseType;
import com.planb.domain.health.entity.QFoodInfo;
import com.planb.domain.health.entity.QHealth;
import com.planb.domain.health.entity.constant.FoodType;
import com.querydsl.core.Tuple;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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


    /**
     * 건강 요약을 조회한다.
     *
     * 관리 질환은 컬렉션이라 알레르기 집계 쿼리에 함께 넣을 수 없다.
     * 집계로 요약을 만든 뒤 질환만 한 번 더 읽어 붙인다. 질의는 집계 1회, 엔티티 1회,
     * 질환 컬렉션 배치 1회로 3회다. default_batch_fetch_size가 500이라
     * 구성원이 500명까지 늘어도 질의 횟수는 그대로다.
     */
    private List<HealthSummaryQueryResponse> findHealthSummaryList(BooleanExpression condition) {

        List<Tuple> rows = jpaQueryFactory
                .select(
                        health
                                .id,
                        health
                                .travelerName,
                        health
                                .hasMedication,
                        foodInfo
                                .id
                                .count()
                                .gt(0)
                )
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
                                .hasMedication
                )
                .fetch();

        Map<Long, List<DiseaseType>> diseaseTypesByHealthId =
                findDiseaseTypesByHealthId(rows
                        .stream()
                        .map(row -> row.get(health.id))
                        .toList());

        return rows
                .stream()
                .map(row -> new HealthSummaryQueryResponse(
                        row.get(health.id),
                        row.get(health.travelerName),
                        Boolean.TRUE.equals(row.get(health.hasMedication)),
                        diseaseTypesByHealthId.getOrDefault(
                                row.get(health.id),
                                List.of()),
                        Boolean.TRUE.equals(row.get(foodInfo.id.count().gt(0)))))
                .toList();
    }

    private Map<Long, List<DiseaseType>> findDiseaseTypesByHealthId(List<Long> healthIds) {

        if (healthIds.isEmpty()) {
            return Map.of();
        }

        return jpaQueryFactory
                .selectFrom(health)
                .where(health.id.in(healthIds))
                .fetch()
                .stream()
                .filter(found -> found.getHealthInfo() != null)
                .collect(Collectors.toMap(
                        Health::getId,
                        found -> found
                                .getHealthInfo()
                                .diseaseTypeList()));
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
