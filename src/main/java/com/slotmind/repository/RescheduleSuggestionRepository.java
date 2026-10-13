package com.slotmind.repository;
import com.slotmind.domain.*;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
public interface RescheduleSuggestionRepository extends JpaRepository<RescheduleSuggestion, String> {
    List<RescheduleSuggestion> findByAppointmentIdAndStatus(String appointmentId, SuggestionStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from RescheduleSuggestion s where s.id = :id")
    Optional<RescheduleSuggestion> findByIdForUpdate(@Param("id") String id);
    // Scalar projection discovers lock IDs without putting stale entities in the persistence context.
    interface LockIds {
        String getAppointmentId(); String getOldSlotId(); String getOldProviderId();
        String getNewSlotId(); String getNewProviderId();
    }
    @Query("select s.appointment.id as appointmentId, s.appointment.slot.id as oldSlotId, " +
        "s.appointment.provider.id as oldProviderId, s.recommendedSlot.id as newSlotId, " +
        "s.recommendedSlot.provider.id as newProviderId from RescheduleSuggestion s where s.id = :id")
    Optional<LockIds> findLockIds(@Param("id") String id);
}
