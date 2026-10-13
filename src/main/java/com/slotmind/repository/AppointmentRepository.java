package com.slotmind.repository;

import com.slotmind.domain.Appointment;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

import com.slotmind.domain.AppointmentStatus;

public interface AppointmentRepository extends JpaRepository<Appointment, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id = :id")
    Optional<Appointment> findByIdForUpdate(@Param("id") String id);

    List<Appointment> findByProviderIdAndStatusIn(String providerId, Collection<AppointmentStatus> statuses);

    Optional<Appointment> findBySlotId(String slotId);

    interface AffectedId {
        String getId();

        String getSlotId();
    }

    @Query("select a.id as id, a.slot.id as slotId from Appointment a where a.provider.id=:provider and a.status in :statuses order by a.id")
    List<AffectedId> findAffectedIds(@Param("provider") String provider, @Param("statuses") Collection<AppointmentStatus> statuses);
}


