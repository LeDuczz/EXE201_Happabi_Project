package com.minduc.happabi.repository;

import com.minduc.happabi.entity.WorkSessionChecklistItem;
import com.minduc.happabi.enums.WorkSessionChecklistStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkSessionChecklistItemRepository extends JpaRepository<WorkSessionChecklistItem, UUID> {

    List<WorkSessionChecklistItem> findByWorkSession_IdOrderBySortOrderAsc(UUID workSessionId);

    List<WorkSessionChecklistItem> findByWorkSession_IdInOrderByWorkSession_IdAscSortOrderAsc(List<UUID> workSessionIds);

    long countByWorkSession_IdAndStatus(UUID workSessionId, WorkSessionChecklistStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select item
            from WorkSessionChecklistItem item
            join fetch item.workSession ws
            where item.id = :itemId
              and ws.id = :workSessionId
            """)
    Optional<WorkSessionChecklistItem> findByIdAndWorkSessionIdForUpdate(@Param("itemId") UUID itemId,
                                                                         @Param("workSessionId") UUID workSessionId);
}
