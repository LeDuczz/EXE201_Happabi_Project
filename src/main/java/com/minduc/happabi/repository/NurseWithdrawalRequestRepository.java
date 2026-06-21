package com.minduc.happabi.repository;

import com.minduc.happabi.entity.NurseWithdrawalRequest;
import com.minduc.happabi.enums.NurseWithdrawalStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface NurseWithdrawalRequestRepository extends JpaRepository<NurseWithdrawalRequest, UUID> {

    @EntityGraph(attributePaths = {"nurseProfile", "nurseProfile.user", "processedByAdmin"})
    Page<NurseWithdrawalRequest> findByNurseProfile_IdOrderByCreatedAtDesc(UUID nurseProfileId, Pageable pageable);

    @EntityGraph(attributePaths = {"nurseProfile", "nurseProfile.user", "processedByAdmin"})
    Page<NurseWithdrawalRequest> findByStatusOrderByCreatedAtDesc(NurseWithdrawalStatus status, Pageable pageable);

    @EntityGraph(attributePaths = {"nurseProfile", "nurseProfile.user", "processedByAdmin"})
    Page<NurseWithdrawalRequest> findAllByOrderByCreatedAtDesc(Pageable pageable);

    long countByStatus(NurseWithdrawalStatus status);

    @Query("select coalesce(sum(request.amount), 0) from NurseWithdrawalRequest request where request.status = :status")
    BigDecimal sumAmountByStatus(@Param("status") NurseWithdrawalStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from NurseWithdrawalRequest request where request.id = :id")
    Optional<NurseWithdrawalRequest> findByIdForUpdate(@Param("id") UUID id);
}
