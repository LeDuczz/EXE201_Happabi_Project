package com.minduc.happabi.observability.audit;

public interface AuditRecorder {

    void record(AuditEvent event);
}
