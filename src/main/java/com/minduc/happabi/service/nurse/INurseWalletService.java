package com.minduc.happabi.service.nurse;

import java.math.BigDecimal;

public interface INurseWalletService {
    boolean canAcceptCashBooking(String nurseId, BigDecimal bookingAmount);
}
