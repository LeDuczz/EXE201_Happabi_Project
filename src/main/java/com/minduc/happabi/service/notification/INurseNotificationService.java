package com.minduc.happabi.service.notification;

import com.minduc.happabi.entity.NurseProfile;

public interface INurseNotificationService {
    void notifyRejected(NurseProfile profile, String reason);

    void notifyApprovedPendingContract(NurseProfile profile);

    void notifyDepositRequired(NurseProfile profile);

    void notifyDepositConfirmed(NurseProfile profile);

    void notifyActive(NurseProfile profile);

    void notifySuspended(NurseProfile profile, String reason);

    void notifyReactivated(NurseProfile profile);
}
