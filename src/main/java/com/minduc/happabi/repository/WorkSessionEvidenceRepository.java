package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WorkSessionEvidence;
import com.minduc.happabi.enums.WorkSessionEvidenceStatus;
import com.minduc.happabi.enums.WorkSessionEvidenceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface WorkSessionEvidenceRepository extends JpaRepository<WorkSessionEvidence, UUID> {

    List<WorkSessionEvidence> findByWorkSession_IdAndStatusOrderByCreatedAtAsc(UUID workSessionId,
                                                                               WorkSessionEvidenceStatus status);

    List<WorkSessionEvidence> findByChecklistItem_IdAndStatusOrderByCreatedAtAsc(UUID checklistItemId,
                                                                                 WorkSessionEvidenceStatus status);

    long countByChecklistItem_IdAndStatus(UUID checklistItemId, WorkSessionEvidenceStatus status);

    List<WorkSessionEvidence> findByChecklistItem_IdAndStatusIn(UUID checklistItemId,
                                                                Collection<WorkSessionEvidenceStatus> statuses);

    List<WorkSessionEvidence> findTop100ByStatusAndRetentionUntilBeforeOrderByRetentionUntilAsc(
            WorkSessionEvidenceStatus status,
            OffsetDateTime retentionUntil);

    List<WorkSessionEvidence> findByWorkSession_IdAndEvidenceTypeAndStatus(UUID workSessionId,
                                                                           WorkSessionEvidenceType evidenceType,
                                                                           WorkSessionEvidenceStatus status);
}
