package com.minduc.happabi.service.notification;

import com.minduc.happabi.entity.NurseProfile;
import com.minduc.happabi.enums.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NurseNotificationService {

    private static final String RESOURCE_TYPE = "NURSE_PROFILE";

    private final INotificationService notificationService;

    public void notifyRejected(NurseProfile profile, String reason) {
        notificationService.create(
                profile.getUser(),
                NotificationType.NURSE_PROFILE_REJECTED,
                "Ho so dieu duong can cap nhat",
                reason == null || reason.isBlank()
                        ? "Ho so cua ban chua duoc duyet. Vui long kiem tra va nop lai."
                        : reason,
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    public void notifyApprovedPendingContract(NurseProfile profile) {
        notificationService.create(
                profile.getUser(),
                NotificationType.NURSE_PROFILE_APPROVED_PENDING_CONTRACT,
                "Ho so dieu duong da duoc duyet",
                "Vui long xem va xac nhan hop dong de kich hoat tai khoan nurse.",
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    public void notifyActive(NurseProfile profile) {
        notificationService.create(
                profile.getUser(),
                NotificationType.NURSE_PROFILE_ACTIVE,
                "Tai khoan nurse da duoc kich hoat",
                "Ban da co the nhan lich va hoat dong tren Happabi.",
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    public void notifySuspended(NurseProfile profile, String reason) {
        notificationService.create(
                profile.getUser(),
                NotificationType.NURSE_SUSPENDED,
                "Tai khoan nurse dang bi tam khoa",
                reason == null || reason.isBlank()
                        ? "Tai khoan nurse cua ban dang bi tam khoa hoat dong."
                        : reason,
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }

    public void notifyReactivated(NurseProfile profile) {
        notificationService.create(
                profile.getUser(),
                NotificationType.NURSE_REACTIVATED,
                "Tai khoan nurse da duoc mo lai",
                "Ban co the tiep tuc nhan lich tren Happabi.",
                RESOURCE_TYPE,
                profile.getId().toString()
        );
    }
}
