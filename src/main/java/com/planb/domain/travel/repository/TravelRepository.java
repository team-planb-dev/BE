package com.planb.domain.travel.repository;

import com.planb.domain.travel.entity.Travel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface TravelRepository extends JpaRepository<Travel, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select travel from Travel travel where travel.id = :travelId")
    Optional<Travel> findForEditUpdate(@Param("travelId") Long travelId);

}
