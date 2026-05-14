package com.minduc.happabi.service.s3;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.S3ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class S3ServiceImpl implements S3Service {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "avatars",         Set.of("image/jpeg", "image/png", "image/webp"),
            "certifications",  Set.of("image/jpeg", "image/png", "application/pdf"),
            "kyc",             Set.of("image/jpeg", "image/png"),
            "checklist",       Set.of("image/jpeg", "image/png"),
            "gallery",         Set.of("image/jpeg", "image/png", "image/webp")
    );

    private static final Map<String, Long> MAX_SIZES = Map.of(
            "avatars",         5L  * 1024 * 1024,   // 5 MB
            "certifications",  10L * 1024 * 1024,   // 10 MB
            "kyc",             10L * 1024 * 1024,   // 10 MB
            "checklist",       5L  * 1024 * 1024,   // 5 MB
            "gallery",         20L * 1024 * 1024    // 20 MB
    );

    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    @Override
    public String upload(String folder, String ownerId, MultipartFile file) {
        validate(folder, file);

        String key = buildKey(folder, ownerId, file.getOriginalFilename());
        try {
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(file.getContentType())
                    .contentLength(file.getSize())
                    .metadata(Map.of(
                            "owner-id", ownerId,
                            "original-name", sanitize(file.getOriginalFilename())
                    ))
                    .build();

            s3Client.putObject(request, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("[S3] Uploaded: bucket={} key={} size={}", bucket, key, file.getSize());
            return key;

        } catch (IOException e) {
            log.error("[S3] Upload failed: key={}", key, e);
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, e);
        } catch (S3Exception e) {
            log.error("[S3] Upload denied: bucket={} key={} status={} code={} message={}",
                    bucket, key, e.statusCode(), e.awsErrorDetails().errorCode(),
                    e.awsErrorDetails().errorMessage());
            if (e.statusCode() == 403) {
                throw new AppException(S3ErrorCode.ACCESS_DENIED,
                        "IAM user is missing s3:PutObject permission for bucket " + bucket);
            }
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("[S3] Upload error: key={}", key, e);
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null || key.isBlank()) return;
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            log.info("[S3] Deleted: bucket={} key={}", bucket, key);
        } catch (S3Exception e) {
            log.error("[S3] Delete denied: bucket={} key={} status={} code={} message={}",
                    bucket, key, e.statusCode(), e.awsErrorDetails().errorCode(),
                    e.awsErrorDetails().errorMessage());
            if (e.statusCode() == 403) {
                throw new AppException(S3ErrorCode.ACCESS_DENIED,
                        "IAM user is missing s3:DeleteObject permission for bucket " + bucket);
            }
            throw new AppException(S3ErrorCode.DELETE_FAILED, e.awsErrorDetails().errorMessage());
        } catch (Exception e) {
            log.error("[S3] Delete failed: key={}", key, e);
            throw new AppException(S3ErrorCode.DELETE_FAILED, e);
        }
    }

    @Override
    public String presign(String key) {
        return presignWithTtl(key, PRESIGN_TTL);
    }

    @Override
    public String presign(String key, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = PRESIGN_TTL;
        }
        return presignWithTtl(key, ttl);
    }

    private String presignWithTtl(String key, Duration ttl) {
        if (key == null || key.isBlank()) return null;
        try {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(r -> r.bucket(bucket).key(key))
                    .build();

            String url = s3Presigner.presignGetObject(req).url().toString();
            log.debug("[S3] Pre-signed URL generated: key={} ttl={}s", key, PRESIGN_TTL.getSeconds());
            return url;
        } catch (Exception e) {
            log.error("[S3] Presign failed: key={}", key, e);
            throw new AppException(S3ErrorCode.FILE_NOT_FOUND, key);
        }
    }

    private void validate(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, "File must not be empty");
        }

        // Content-type check
        Set<String> allowed = ALLOWED_TYPES.getOrDefault(folder, Set.of());
        String contentType = file.getContentType();
        if (contentType == null || !allowed.contains(contentType.toLowerCase())) {
            throw new AppException(S3ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Folder '" + folder + "' accepts: " + allowed);
        }

        // Size check
        long maxBytes = MAX_SIZES.getOrDefault(folder, 5L * 1024 * 1024);
        if (file.getSize() > maxBytes) {
            throw new AppException(S3ErrorCode.FILE_TOO_LARGE,
                    "Max allowed: " + (maxBytes / 1024 / 1024) + " MB");
        }
    }

    private String buildKey(String folder, String ownerId, String originalFilename) {
        String ext = extractExtension(originalFilename);
        return folder + "/" + ownerId + "/" + UUID.randomUUID() + ext;
    }

    private String extractExtension(String filename) {
        if (filename == null) return "";
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    private String sanitize(String filename) {
        return filename == null ? "" : filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
