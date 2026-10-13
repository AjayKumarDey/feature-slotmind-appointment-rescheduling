package com.slotmind.repository;
import com.slotmind.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RescheduleJobRepository extends JpaRepository<RescheduleJob,String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from RescheduleJob j where j.id = :id")
    Optional<RescheduleJob> findByIdForUpdate(@Param("id") String id);
    boolean existsByAppointmentIdAndTriggerReasonAndStatusIn(String id, TriggerReason trigger, Collection<JobStatus> statuses);
}
