package com.slotmind.repository;
import com.slotmind.domain.Provider;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
public interface ProviderRepository extends JpaRepository<Provider, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Provider e where e.id = :id")
    Optional<Provider> findByIdForUpdate(@Param("id") String id);
}
