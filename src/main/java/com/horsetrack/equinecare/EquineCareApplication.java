package com.horsetrack.equinecare;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.security.Security;

@SpringBootApplication
public class EquineCareApplication {

    static {
        // Cho phép kết nối TLS 1.0/1.1 đối với SQL Server máy local
        Security.setProperty("jdk.tls.disabledAlgorithms",
                "SSLv3, RC4, DES, MD5withRSA, DH keySize < 1024, EC keySize < 224, 3DES_EDE_CBC, anon, NULL");
        System.setProperty("jdk.tls.client.protocols", "TLSv1,TLSv1.1,TLSv1.2,TLSv1.3");
    }

    public static void main(String[] args) {
        SpringApplication.run(EquineCareApplication.class, args);
    }

}