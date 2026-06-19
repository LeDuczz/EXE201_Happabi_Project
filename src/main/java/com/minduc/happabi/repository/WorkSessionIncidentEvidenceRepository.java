package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WorkSessionIncidentEvidence;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WorkSessionIncidentEvidenceRepository extends JpaRepository<WorkSessionIncidentEvidence, UUID> {
    List<WorkSessionIncidentEvidence> findByIncident_IdOrderByCreatedAtAsc(UUID incidentId);
}
