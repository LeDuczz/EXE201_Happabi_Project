package com.minduc.happabi.integration.sqs;

public interface IFileCleanupPublisher {
    void publishDeleteObject(String key, String reason);
}
