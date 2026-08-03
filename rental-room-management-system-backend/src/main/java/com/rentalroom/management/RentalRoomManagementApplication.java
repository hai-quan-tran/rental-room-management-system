package com.rentalroom.management;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class RentalRoomManagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(RentalRoomManagementApplication.class, args);
    }
}
