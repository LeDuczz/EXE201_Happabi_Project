package com.minduc.happabi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import com.minduc.happabi.seed.DataSeeder;
import software.amazon.awssdk.core.exception.SdkClientException;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SystemStartupRunner implements ApplicationRunner {

    private final DataSeeder dataSeeder;

    @Override
    public void run(ApplicationArguments args) {
        log.info("System startup initialization");

        dataSeeder.seedRolesAndPermissions();
        dataSeeder.seedServiceOfferings();
        dataSeeder.seedDemoNurses();

        try {
            dataSeeder.seedAdminAccount();
        } catch (SdkClientException e) {
            if (isNetworkAwsError(e)) {
                log.warn("[Startup] AWS unavailable. Skip admin seed.", e);
            } else {
                throw e;
            }
        }

        log.info("System startup completed");
    }

    private boolean isNetworkAwsError(Throwable ex) {
        Throwable current = ex;

        while (current != null) {
            if (current instanceof UnknownHostException
                    || current instanceof SocketTimeoutException
                    || current instanceof ConnectException) {
                return true;
            }
            current = current.getCause();
        }

        return false;
    }
}
