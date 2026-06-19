package com.minduc.happabi.service.booking;

import com.minduc.happabi.entity.WorkSession;

public interface IBookingSettlementService {

    void settleCompletedWorkSession(WorkSession workSession);
}
