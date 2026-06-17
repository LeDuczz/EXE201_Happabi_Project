package com.minduc.happabi.repository;

import com.minduc.happabi.entity.AdminWalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AdminWalletTransactionRepository extends JpaRepository<AdminWalletTransaction, UUID> {
}
