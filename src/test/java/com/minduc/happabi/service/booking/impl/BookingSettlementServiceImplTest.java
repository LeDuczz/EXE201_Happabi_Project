package com.minduc.happabi.service.booking.impl;

import com.minduc.happabi.entity.AdminWallet;
import com.minduc.happabi.entity.Booking;
import com.minduc.happabi.entity.BookingSettlement;
import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.entity.NurseWallet;
import com.minduc.happabi.entity.PlatformRevenue;
import com.minduc.happabi.entity.WalletTransaction;
import com.minduc.happabi.entity.WorkSession;
import com.minduc.happabi.enums.BookingPaymentOption;
import com.minduc.happabi.enums.TransactionType;
import com.minduc.happabi.enums.WorkSessionStatus;
import com.minduc.happabi.repository.AdminWalletRepository;
import com.minduc.happabi.repository.BookingSettlementRepository;
import com.minduc.happabi.repository.NurseWalletRepository;
import com.minduc.happabi.repository.PlatformRevenueRepository;
import com.minduc.happabi.repository.WalletTransactionRepository;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingSettlementServiceImplTest {

    @Mock
    private BookingSettlementRepository bookingSettlementRepository;

    @Mock
    private NurseWalletRepository nurseWalletRepository;

    @Mock
    private WalletTransactionRepository walletTransactionRepository;

    @Mock
    private AdminWalletRepository adminWalletRepository;

    @Mock
    private PlatformRevenueRepository platformRevenueRepository;

    @Mock
    private IAdminWalletLedgerService adminWalletLedgerService;

    private BookingSettlementServiceImpl service;
    private UUID bookingId;
    private UUID workSessionId;
    private UUID nurseId;
    private NurseWallet nurseWallet;
    private AdminWallet adminWallet;

    @BeforeEach
    void setUp() {
        service = new BookingSettlementServiceImpl(
                bookingSettlementRepository,
                nurseWalletRepository,
                walletTransactionRepository,
                adminWalletRepository,
                platformRevenueRepository,
                adminWalletLedgerService
        );
        bookingId = UUID.randomUUID();
        workSessionId = UUID.randomUUID();
        nurseId = UUID.randomUUID();
        nurseWallet = NurseWallet.builder()
                .nurseId(nurseId)
                .balance(BigDecimal.ZERO)
                .depositBalance(BigDecimal.ZERO)
                .build();
        adminWallet = AdminWallet.builder()
                .id(AdminWallet.PLATFORM_ADMIN_WALLET_ID)
                .balance(BigDecimal.ZERO)
                .build();

        when(bookingSettlementRepository.findByBookingIdForUpdate(bookingId)).thenReturn(Optional.empty());
    }

    @Test
    void settleCompletedWorkSessionCreditsNurseAndAdminForFullAppPayment() {
        WorkSession session = workSession(
                BookingPaymentOption.FULL_APP_PAYMENT,
                450000L,
                67500L,
                382500L,
                450000L,
                0L
        );
        adminWallet.setBalance(BigDecimal.valueOf(450000));
        stubWalletLocks();

        service.settleCompletedWorkSession(session);

        assertThat(nurseWallet.getBalance()).isEqualByComparingTo("382500");
        assertThat(adminWallet.getBalance()).isEqualByComparingTo("450000");
        assertNurseWalletTransaction("382500");
        verify(adminWalletLedgerService).recordNursePayout(bookingId, BigDecimal.valueOf(382500));
        assertSettlement("450000", "450000", "0", "382500", "382500", "67500");
        verify(platformRevenueRepository).save(any(PlatformRevenue.class));
    }

    @Test
    void settleCompletedWorkSessionOnlyCreditsRemainingNurseShareWhenMotherPaysCashToNurse() {
        WorkSession session = workSession(
                BookingPaymentOption.DEPOSIT_30_PERCENT,
                450000L,
                67500L,
                382500L,
                135000L,
                315000L
        );
        adminWallet.setBalance(BigDecimal.valueOf(135000));
        stubWalletLocks();

        service.settleCompletedWorkSession(session);

        assertThat(nurseWallet.getBalance()).isEqualByComparingTo("67500");
        assertThat(adminWallet.getBalance()).isEqualByComparingTo("135000");
        assertNurseWalletTransaction("67500");
        verify(adminWalletLedgerService).recordNursePayout(bookingId, BigDecimal.valueOf(67500));
        assertSettlement("450000", "135000", "315000", "382500", "67500", "67500");
    }

    @Test
    void settleCompletedWorkSessionDoesNothingWhenAlreadySettled() {
        WorkSession session = workSession(
                BookingPaymentOption.FULL_APP_PAYMENT,
                450000L,
                67500L,
                382500L,
                450000L,
                0L
        );
        when(bookingSettlementRepository.findByBookingIdForUpdate(bookingId))
                .thenReturn(Optional.of(BookingSettlement.builder().bookingId(bookingId).build()));

        service.settleCompletedWorkSession(session);

        verify(nurseWalletRepository, never()).findByNurseIdForUpdate(nurseId);
        verify(walletTransactionRepository, never()).save(any());
        verify(adminWalletLedgerService, never()).recordNursePayout(any(), any());
    }

    private WorkSession workSession(BookingPaymentOption paymentOption,
                                    Long grossAmount,
                                    Long platformFeeAmount,
                                    Long nurseEarningAmount,
                                    Long appPaymentAmount,
                                    Long remainingCashAmount) {
        NurseProfile nurseProfile = NurseProfile.builder()
                .id(nurseId)
                .build();
        Booking booking = Booking.builder()
                .id(bookingId)
                .nurseProfile(nurseProfile)
                .paymentOption(paymentOption)
                .grossAmount(grossAmount)
                .platformFeeAmount(platformFeeAmount)
                .nurseEarningAmount(nurseEarningAmount)
                .appPaymentAmount(appPaymentAmount)
                .remainingCashAmount(remainingCashAmount)
                .build();
        return WorkSession.builder()
                .id(workSessionId)
                .booking(booking)
                .nurseProfile(nurseProfile)
                .status(WorkSessionStatus.COMPLETED)
                .build();
    }

    private void stubWalletLocks() {
        when(nurseWalletRepository.findByNurseIdForUpdate(nurseId)).thenReturn(Optional.of(nurseWallet));
        when(adminWalletRepository.findByIdForUpdate(AdminWallet.PLATFORM_ADMIN_WALLET_ID))
                .thenReturn(Optional.of(adminWallet));
    }

    private void assertNurseWalletTransaction(String expectedAmount) {
        ArgumentCaptor<WalletTransaction> captor = ArgumentCaptor.forClass(WalletTransaction.class);
        verify(walletTransactionRepository).save(captor.capture());
        WalletTransaction transaction = captor.getValue();
        assertThat(transaction.getTransactionType()).isEqualTo(TransactionType.BOOKING_EARNING);
        assertThat(transaction.getAmount()).isEqualByComparingTo(expectedAmount);
        assertThat(transaction.getWalletImpact()).isEqualByComparingTo(expectedAmount);
    }

    private void assertSettlement(String grossAmount,
                                  String appCollectedAmount,
                                  String cashCollectedByNurseAmount,
                                  String nurseEarningAmount,
                                  String nurseWalletCreditAmount,
                                  String platformFeeAmount) {
        ArgumentCaptor<BookingSettlement> captor = ArgumentCaptor.forClass(BookingSettlement.class);
        verify(bookingSettlementRepository).save(captor.capture());
        BookingSettlement settlement = captor.getValue();
        assertThat(settlement.getBookingId()).isEqualTo(bookingId);
        assertThat(settlement.getWorkSessionId()).isEqualTo(workSessionId);
        assertThat(settlement.getGrossAmount()).isEqualByComparingTo(grossAmount);
        assertThat(settlement.getAppCollectedAmount()).isEqualByComparingTo(appCollectedAmount);
        assertThat(settlement.getCashCollectedByNurseAmount()).isEqualByComparingTo(cashCollectedByNurseAmount);
        assertThat(settlement.getNurseEarningAmount()).isEqualByComparingTo(nurseEarningAmount);
        assertThat(settlement.getNurseWalletCreditAmount()).isEqualByComparingTo(nurseWalletCreditAmount);
        assertThat(settlement.getPlatformFeeAmount()).isEqualByComparingTo(platformFeeAmount);
    }
}
