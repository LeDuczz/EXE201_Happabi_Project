package com.minduc.happabi.repository;

import com.minduc.happabi.entity.AdminWalletTransaction;
import com.minduc.happabi.enums.AdminWalletTransactionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AdminWalletTransactionRepository extends JpaRepository<AdminWalletTransaction, UUID> {

    Optional<AdminWalletTransaction> findByBookingIdAndTransactionType(UUID bookingId,
                                                                       AdminWalletTransactionType transactionType);

    Page<AdminWalletTransaction> findByWalletIdOrderByCreatedAtDesc(String walletId, Pageable pageable);
}
