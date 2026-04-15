package com.example.trainbooking;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
@EnableTransactionManagement
public class TrainBookingApplication {

    public static void main(String[] args) {
        SpringApplication.run(TrainBookingApplication.class, args);
    }
}
