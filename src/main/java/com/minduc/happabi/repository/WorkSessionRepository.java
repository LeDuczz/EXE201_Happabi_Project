package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.WorkSessionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkSessionRepository extends JpaRepository<WorkSession, UUID> {

    boolean existsByBooking_Id(UUID bookingId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select ws
            from WorkSession ws
            join fetch ws.booking b
            join fetch ws.mother m
            join fetch ws.nurseProfile np
            join fetch np.user nu
            join fetch ws.serviceOffering so
            where ws.id = :id
            """)
    Optional<WorkSession> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select ws
            from WorkSession ws
            join fetch ws.booking b
            join fetch ws.mother m
            join fetch ws.nurseProfile np
            join fetch np.user nu
            join fetch ws.serviceOffering so
            where np.id = :nurseProfileId
            order by ws.scheduledStartAt desc
            """)
    List<WorkSession> findByNurseProfileId(@Param("nurseProfileId") UUID nurseProfileId);

    @Query("""
            select ws
            from WorkSession ws
            join fetch ws.booking b
            join fetch ws.mother m
            join fetch ws.nurseProfile np
            join fetch np.user nu
            join fetch ws.serviceOffering so
            where ws.mother.id = :motherId
            order by ws.scheduledStartAt desc
            """)
    List<WorkSession> findByMotherId(@Param("motherId") UUID motherId);

    @Query("""
            select ws.id
            from WorkSession ws
            where ws.status = :status
              and ws.autoConfirmAt <= :now
            order by ws.autoConfirmAt asc
            """)
    List<UUID> findIdsReadyForAutoConfirm(@Param("status") WorkSessionStatus status,
                                          @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update WorkSession ws
            set ws.status = :nextStatus,
                ws.confirmedAt = :confirmedAt,
                ws.updatedAt = :confirmedAt
            where ws.id = :id
              and ws.status = :currentStatus
              and ws.reportedAt is null
            """)
    int autoConfirm(@Param("id") UUID id,
                    @Param("currentStatus") WorkSessionStatus currentStatus,
                    @Param("nextStatus") WorkSessionStatus nextStatus,
                    @Param("confirmedAt") OffsetDateTime confirmedAt);

    long countByStatusInAndNurseProfile_Id(Collection<WorkSessionStatus> statuses, UUID nurseProfileId);
}
