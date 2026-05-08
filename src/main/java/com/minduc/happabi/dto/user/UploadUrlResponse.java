package com.minduc.happabi.dto.user;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UploadUrlResponse {

    /** The S3 key stored in DB (use this to reference the object) */
    private String s3Key;

    /** Pre-signed GET URL (expires in 1 hour) — for immediate display */
    private String url;

    /** URL expiry in seconds */
    private Long expiresInSeconds;
}
