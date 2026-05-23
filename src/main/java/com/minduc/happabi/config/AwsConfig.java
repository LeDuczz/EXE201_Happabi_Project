package com.minduc.happabi.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.retry.RetryMode;
import software.amazon.awssdk.core.retry.RetryPolicy;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.time.Duration;

@Configuration
public class AwsConfig {
    @Value("${aws.iam.access-key}")
    private String accessKey;

    @Value("${aws.iam.secret-key}")
    private String secretKey;

    @Value("${aws.region}")
    private String region;

    private ClientOverrideConfiguration overrideConfiguration() {
        return ClientOverrideConfiguration.builder()
                .apiCallTimeout(Duration.ofSeconds(20))
                .apiCallAttemptTimeout(Duration.ofSeconds(12))
                .retryPolicy(RetryPolicy.forRetryMode(RetryMode.STANDARD))
                .build();
    }

    private ApacheHttpClient.Builder httpClientBuilder() {
        return ApacheHttpClient.builder()
                .connectionTimeout(Duration.ofSeconds(3))
                .socketTimeout(Duration.ofSeconds(10))
                .connectionAcquisitionTimeout(Duration.ofSeconds(3));
    }

    private StaticCredentialsProvider credentialsProvider() {
        return StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKey, secretKey));
    }

    @Bean
    public CognitoIdentityProviderClient cognitoClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .httpClientBuilder(httpClientBuilder())
                .overrideConfiguration(overrideConfiguration())
                .build();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .httpClientBuilder(httpClientBuilder())
                .overrideConfiguration(overrideConfiguration())
                .build();
    }

    @Bean
    public S3Presigner s3Presigner() {
        return S3Presigner.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .build();
    }

    @Bean
    public SqsClient sqsClient() {
        return SqsClient.builder()
                .region(Region.of(region))
                .credentialsProvider(credentialsProvider())
                .httpClientBuilder(httpClientBuilder())
                .overrideConfiguration(overrideConfiguration())
                .build();
    }
}
