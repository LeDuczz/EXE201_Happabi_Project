package com.minduc.happabi.integration.s3.impl;

import com.minduc.happabi.exception.AppException;
import com.minduc.happabi.exception.code.S3ErrorCode;
import com.minduc.happabi.integration.s3.IS3Service;
import com.minduc.happabi.integration.s3.S3ObjectDownload;
import com.minduc.happabi.observability.annotation.AuditAction;
import com.minduc.happabi.observability.annotation.LogExecution;
import com.minduc.happabi.observability.annotation.TimedAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
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
public class S3ServiceImpl implements IS3Service {

    private static final Map<String, Set<String>> ALLOWED_TYPES = Map.of(
            "avatars", Set.of("image/jpeg", "image/png", "image/webp"),
            "certifications", Set.of("image/jpeg", "image/png", "application/pdf"),
            "kyc", Set.of("image/jpeg", "image/png"),
            "checklist", Set.of("image/jpeg", "image/png"),
            "work-sessions", Set.of("image/jpeg", "image/png"),
            "withdrawals", Set.of("image/jpeg", "image/png", "image/webp", "application/pdf"),
            "gallery", Set.of("image/jpeg", "image/png", "image/webp")
    );

    private static final Map<String, Long> MAX_SIZES = Map.of(
            "avatars", 5L * 1024 * 1024,
            "certifications", 10L * 1024 * 1024,
            "kyc", 10L * 1024 * 1024,
            "checklist", 5L * 1024 * 1024,
            "work-sessions", 5L * 1024 * 1024,
            "withdrawals", 10L * 1024 * 1024,
            "gallery", 20L * 1024 * 1024
    );

    private static final Duration PRESIGN_TTL = Duration.ofHours(1);

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Override
    @LogExecution
    @AuditAction(action = "UPLOAD_FILE_TO_S3", resourceType = "S3_OBJECT")
    @TimedAction("UPLOAD_FILE_TO_S3")
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
        } catch (SdkClientException e) {
            log.warn("[S3] Storage connectivity issue: bucket={} key={} error={}",
                    bucket, key, e.getMessage());
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, "Storage service temporarily unavailable");
        } catch (Exception e) {
            log.error("[S3] Unexpected upload error: key={}", key, e);
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, e);
        }
    }

    @Override
    @LogExecution
    @AuditAction(action = "DELETE_FILE_FROM_S3", resourceType = "S3_OBJECT")
    @TimedAction("DELETE_FILE_FROM_S3")
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
        } catch (SdkClientException e) {
            log.warn("[S3] Storage connectivity issue while deleting: key={} error={}",
                    key, e.getMessage());
            throw new AppException(S3ErrorCode.DELETE_FAILED, "Storage service temporarily unavailable");
        } catch (Exception e) {
            log.error("[S3] Unexpected delete error: key={}", key, e);
            throw new AppException(S3ErrorCode.DELETE_FAILED, e);
        }
    }

    @Override
    @LogExecution
    @TimedAction("GENERATE_PRESIGNED_URL_FOR_S3_OBJECT")
    public String presign(String key) {
        return presignWithTtl(key, PRESIGN_TTL);
    }

    @Override
    @LogExecution
    @TimedAction("GENERATE_PRESIGNED_URL_FOR_S3_OBJECT_WITH_TTL")
    public String presign(String key, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            ttl = PRESIGN_TTL;
        }
        return presignWithTtl(key, ttl);
    }

    @Override
    @LogExecution
    @TimedAction("DOWNLOAD_S3_OBJECT")
    public S3ObjectDownload download(String key) {
        if (key == null || key.isBlank()) {
            throw new AppException(S3ErrorCode.FILE_NOT_FOUND, "File key is missing");
        }
        try {
            GetObjectRequest request = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build();
            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(request);
            GetObjectResponse response = objectBytes.response();
            return new S3ObjectDownload(
                    objectBytes.asByteArray(),
                    response.contentType(),
                    response.contentLength()
            );
        } catch (S3Exception e) {
            if (e.statusCode() == 403) {
                throw new AppException(S3ErrorCode.ACCESS_DENIED,
                        "IAM user is missing s3:GetObject permission for bucket " + bucket);
            }
            throw new AppException(S3ErrorCode.FILE_NOT_FOUND, key);
        } catch (SdkClientException e) {
            log.warn("[S3] Storage connectivity issue while downloading: key={} error={}",
                    key, e.getMessage());
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, "Storage service temporarily unavailable");
        } catch (Exception e) {
            log.error("[S3] Unexpected download error: key={}", key, e);
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, e);
        }
    }

    private String presignWithTtl(String key, Duration ttl) {
        if (key == null || key.isBlank()) return null;
        try {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                    .signatureDuration(ttl)
                    .getObjectRequest(r -> r.bucket(bucket).key(key))
                    .build();

            String url = s3Presigner.presignGetObject(req).url().toString();
            log.debug("[S3] Pre-signed URL generated: key={} ttl={}s", key, ttl.getSeconds());
            return url;
        } catch (S3Exception e) {
            throw new AppException(S3ErrorCode.FILE_NOT_FOUND, key);
        } catch (SdkClientException e) {
            log.warn("[S3] Presign connectivity issue: key={} error={}",
                    key, e.getMessage());
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, "Storage service temporarily unavailable");
        } catch (Exception e) {
            log.error("[S3] Unexpected presign error: key={}", key, e);
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, e);
        }
    }

    private void validate(String folder, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(S3ErrorCode.UPLOAD_FAILED, "File must not be empty");
        }

        Set<String> allowed = ALLOWED_TYPES.getOrDefault(folder, Set.of());
        String contentType = file.getContentType();
        if (contentType == null || !allowed.contains(contentType.toLowerCase())) {
            throw new AppException(S3ErrorCode.UNSUPPORTED_FILE_TYPE,
                    "Folder '" + folder + "' accepts: " + allowed);
        }

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
