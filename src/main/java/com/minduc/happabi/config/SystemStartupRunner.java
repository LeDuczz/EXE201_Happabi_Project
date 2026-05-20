package com.minduc.happabi.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Configuration;

import com.minduc.happabi.seed.DataSeeder;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class SystemStartupRunner implements ApplicationRunner {

    private final DataSeeder dataSeeder;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("System startup initialization");
        dataSeeder.seedRolesAndPermissions();
        dataSeeder.seedAdminAccount();

        log.info("System startup completed");
    }
}
