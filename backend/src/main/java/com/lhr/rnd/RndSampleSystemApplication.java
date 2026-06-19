package com.lhr.rnd;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RndSampleSystemApplication {
    public static void main(String[] args) {
        SpringApplication.run(RndSampleSystemApplication.class, args);
    }
}
