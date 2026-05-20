package com.minduc.happabi.repository;

import com.minduc.happabi.entity.OutboxEvent;
import com.minduc.happabi.observability.outbox.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT event FROM OutboxEvent event
        WHERE event.status = :status
          AND event.nextRetryAt <= :now
        ORDER BY event.createdAt ASC
    """)
    List<OutboxEvent> findPublishable(@Param("status") OutboxStatus status,
                                      @Param("now") OffsetDateTime now,
                                      Pageable pageable);
}
