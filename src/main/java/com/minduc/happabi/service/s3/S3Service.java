package com.minduc.happabi.service.s3;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

public interface S3Service {

    String upload(String folder, String ownerId, MultipartFile file);

    void delete(String key);

    String presign(String key);

    String presign(String key, Duration ttlSeconds);

}
