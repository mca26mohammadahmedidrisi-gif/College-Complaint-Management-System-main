package com.college.complaint;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class CollegeComplaintApplication {

    public static void main(String[] args) {
        SpringApplication.run(CollegeComplaintApplication.class, args);
    }
}
