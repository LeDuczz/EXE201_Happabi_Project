package com.minduc.happabi.controller.admin;

import com.minduc.happabi.dto.response.admin.AdminWalletResponse;
import com.minduc.happabi.service.admin.IAdminWalletLedgerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminWalletControllerTest {

    @Mock
    private IAdminWalletLedgerService adminWalletLedgerService;

    @Test
    void getPlatformWalletPassesFiltersWithVietnamBusinessDayBounds() {
        AdminWalletController controller = new AdminWalletController(adminWalletLedgerService);
        Pageable pageable = Pageable.unpaged();
        AdminWalletResponse walletResponse = AdminWalletResponse.builder()
                .walletId("PLATFORM_ADMIN")
                .balance(BigDecimal.valueOf(123000))
                .build();
        when(adminWalletLedgerService.getPlatformWallet(
                eq(pageable),
                eq("NURSE_PAYOUT"),
                eq("OUT"),
                org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any()))
                .thenReturn(walletResponse);

        var response = controller.getPlatformWallet(
                pageable,
                "NURSE_PAYOUT",
                "OUT",
                LocalDate.of(2026, 7, 14),
                LocalDate.of(2026, 7, 20));

        ArgumentCaptor<Instant> startCaptor = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> endCaptor = ArgumentCaptor.forClass(Instant.class);
        verify(adminWalletLedgerService).getPlatformWallet(
                eq(pageable),
                eq("NURSE_PAYOUT"),
                eq("OUT"),
                startCaptor.capture(),
                endCaptor.capture());
        assertThat(startCaptor.getValue()).isEqualTo(Instant.parse("2026-07-13T17:00:00Z"));
        assertThat(endCaptor.getValue()).isEqualTo(Instant.parse("2026-07-20T17:00:00Z"));
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getData()).isSameAs(walletResponse);
    }
}
