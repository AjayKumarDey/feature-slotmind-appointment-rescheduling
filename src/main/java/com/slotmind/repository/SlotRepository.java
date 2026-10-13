package com.slotmind.repository;
import com.slotmind.domain.Slot;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface SlotRepository extends JpaRepository<Slot, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Slot e where e.id = :id")
    Optional<Slot> findByIdForUpdate(@Param("id") String id);
    @Query("select s.provider.id from Slot s where s.id = :id")
    Optional<String> findProviderId(@Param("id") String id);
}

