package com.planb.query.health.repository;


import com.planb.domain.health.entity.QMedicationInfo;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

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


}
