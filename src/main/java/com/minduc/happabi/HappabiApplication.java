package com.minduc.happabi;

import com.minduc.happabi.config.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class HappabiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HappabiApplication.class, args);
    }

}
