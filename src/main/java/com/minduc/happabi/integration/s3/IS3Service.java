package com.minduc.happabi.integration.s3;

import org.springframework.web.multipart.MultipartFile;

import java.time.Duration;

public interface IS3Service {

    String upload(String folder, String ownerId, MultipartFile file);

    void delete(String key);

    String presign(String key);

    String presign(String key, Duration ttlSeconds);

    S3ObjectDownload download(String key);

}
