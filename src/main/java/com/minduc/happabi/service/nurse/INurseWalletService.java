package com.minduc.happabi.service.nurse;

import com.minduc.happabi.dto.WalletDTO;

import java.math.BigDecimal;

public interface INurseWalletService {
    WalletDTO getMyWalletInfo();
    boolean canAcceptCashBooking(String nurseId, BigDecimal bookingAmount);

}
