package com.example.henry;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HenryApplication {

    public static void main(String[] args) {
        SpringApplication.run(HenryApplication.class, args);
    }
}
