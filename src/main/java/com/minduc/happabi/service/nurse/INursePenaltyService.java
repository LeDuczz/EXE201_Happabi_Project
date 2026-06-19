package com.minduc.happabi.service.nurse;

import com.minduc.happabi.entity.WorkSession;

public interface INursePenaltyService {
    void applyNoShowPenalty(WorkSession session, String reason);
}