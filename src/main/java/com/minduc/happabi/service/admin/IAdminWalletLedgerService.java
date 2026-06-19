package com.minduc.happabi.service.admin;

import com.minduc.happabi.dto.response.admin.AdminWalletResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.UUID;

public interface IAdminWalletLedgerService {

    void recordBookingPaymentReceived(UUID bookingId, BigDecimal amount);

    void recordNursePayout(UUID bookingId, BigDecimal amount);

    void recordBookingRefund(UUID bookingId, BigDecimal amount);

    void recordWithdrawalPayout(UUID withdrawalRequestId, BigDecimal amount);

    AdminWalletResponse getPlatformWallet(Pageable pageable);
}
