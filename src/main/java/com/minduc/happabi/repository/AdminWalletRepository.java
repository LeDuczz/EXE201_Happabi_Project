package com.minduc.happabi.repository;

import com.minduc.happabi.entity.AdminWallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AdminWalletRepository extends JpaRepository<AdminWallet, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from AdminWallet wallet where wallet.id = :id")
    Optional<AdminWallet> findByIdForUpdate(@Param("id") String id);
}
