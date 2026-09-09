package com.planb.query.health.repository;


import com.planb.domain.health.entity.QMedicationInfo;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.LocalTime;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class MedicationInfoQueryRepository {

    private final JPAQueryFactory jpaQueryFactory;
    private final QMedicationInfo medicationInfo = QMedicationInfo.medicationInfo;

    public long deleteAllByHealthId(Long healthId) {

        return jpaQueryFactory
                .delete(medicationInfo)
                .where(medicationInfo.health.id.eq(healthId))
                .execute();
    }

    /**
     * 여행에 선택된 구성원의 복약 시간만 조회한다.
     *
     * @param healthIds 조회할 구성원 id
     * @return 복약 시간 목록
     */
    public List<LocalTime> findMedicationTimesByHealthIds(List<Long> healthIds) {

        if (healthIds.isEmpty()) {
            return List.of();
        }

        return jpaQueryFactory
                .select(
                        medicationInfo.medicationTime
                )
                .from(medicationInfo)
                .where(
                        medicationInfo
                                .health
                                .id
                                .in(healthIds),
                        medicationInfo
                                .medicationTime
                                .isNotNull()
                )
                .fetch();
    }

    // userId로 관련 동행인의 의료정보(복약 시간) 리스트를 조회
    public List<LocalTime> findMedicationTimesByUserId(Long userId) {

        return jpaQueryFactory
                .select(
                        medicationInfo.medicationTime
                )
                .from(medicationInfo)
                .where(
                        medicationInfo
                                .health
                                .user
                                .id
                                .eq(userId),
                        medicationInfo
                                .medicationTime
                                .isNotNull()
                )
                .fetch();
    }


}
