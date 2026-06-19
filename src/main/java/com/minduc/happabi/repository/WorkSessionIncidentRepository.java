package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WorkSessionIncident;
import com.minduc.happabi.enums.WorkSessionIncidentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkSessionIncidentRepository extends JpaRepository<WorkSessionIncident, UUID> {

    List<WorkSessionIncident> findByWorkSession_IdOrderByCreatedAtDesc(UUID workSessionId);

    Page<WorkSessionIncident> findAllByOrderByCreatedAtDesc(Pageable pageable);

    Page<WorkSessionIncident> findByStatusOrderByCreatedAtDesc(WorkSessionIncidentStatus status, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select incident
            from WorkSessionIncident incident
            join fetch incident.workSession ws
            join fetch ws.booking booking
            join fetch ws.mother mother
            join fetch ws.nurseProfile nurseProfile
            join fetch nurseProfile.user nurseUser
            join fetch incident.reportedBy reporter
            where incident.id = :id
            """)
    Optional<WorkSessionIncident> findByIdForUpdate(@Param("id") UUID id);
}
