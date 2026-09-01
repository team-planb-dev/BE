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
