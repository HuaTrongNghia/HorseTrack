package com.equinecare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class EquinecareApplication {

    public static void main(String[] args) {
        SpringApplication.run(EquinecareApplication.class, args);
        System.out.println("Hệ thống HorseTrack Backend đã khởi động thành công!");
    }

}