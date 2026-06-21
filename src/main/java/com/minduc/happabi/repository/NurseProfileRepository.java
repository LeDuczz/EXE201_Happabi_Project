package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.User;
import com.minduc.happabi.enums.AvailabilityStatus;
import com.minduc.happabi.enums.NurseStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.data.domain.Pageable;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NurseProfileRepository extends JpaRepository<NurseProfile, UUID>, JpaSpecificationExecutor<NurseProfile> {

    @EntityGraph(attributePaths = {"user"})
    Optional<NurseProfile> findByUser(User user);

    @EntityGraph(attributePaths = {"user"})
    List<NurseProfile> findByNurseStatusOrderByUpdatedAtAsc(NurseStatus nurseStatus);

    long countByNurseStatus(NurseStatus nurseStatus);

    long countByAvailabilityStatus(AvailabilityStatus availabilityStatus);

    @Query("""
            select count(np)
            from NurseProfile np
            where np.noShowViolationCount > 0
               or np.permanentlySuspendedAt is not null
               or np.bookingSuspendedUntil > :now
            """)
    long countPenalizedProfiles(@Param("now") OffsetDateTime now);

    @Query("SELECT np.nurseStatus FROM NurseProfile np WHERE np.user.id = :userId")
    Optional<NurseStatus> findNurseStatusByUserId(@Param("userId") UUID userId);

    List<NurseProfile> findByNurseStatus(NurseStatus nurseStatus, Pageable pageable);

    @EntityGraph(attributePaths = {"user"})
    Optional<NurseProfile> findByIdAndNurseStatus(UUID id, NurseStatus nurseStatus);

    @EntityGraph(attributePaths = {"user"})
    List<NurseProfile> findByIdInAndNurseStatus(List<UUID> ids, NurseStatus nurseStatus);


    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select np from NurseProfile np join fetch np.user where np.id = :id")
    Optional<NurseProfile> findByIdForUpdate(@Param("id") UUID id);
}
