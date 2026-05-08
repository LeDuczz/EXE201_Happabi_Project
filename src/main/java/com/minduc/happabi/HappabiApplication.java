package com.minduc.happabi;

import com.minduc.happabi.config.ratelimit.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(RateLimitProperties.class)
public class HappabiApplication {

    public static void main(String[] args) {
        SpringApplication.run(HappabiApplication.class, args);
    }

}
