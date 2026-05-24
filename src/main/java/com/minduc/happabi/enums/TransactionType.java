package com.minduc.happabi.enums;

public enum TransactionType {
    TOPUP_WALLET,    // Nạp tiền vào ví có thể rút
    TOPUP_DEPOSIT,   // Nạp tiền vào quỹ ký quỹ (không rút được)
    FEE_DEDUCTION,   // Bị trừ tiền phí hoa hồng
    PAYOUT           // Rút tiền về tài khoản ngân hàng
}
