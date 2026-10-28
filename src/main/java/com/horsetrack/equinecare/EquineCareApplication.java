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

        // Server (Docker, SQL Server) chạy giờ UTC trong khi giờ nhập trên FE là giờ Việt Nam: đặt múi giờ mặc định của JVM
        // để LocalDateTime.now() / LocalDate.now() (thời điểm hoàn thành, "hôm nay", việc trễ hạn...) khớp giờ người dùng
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(System.getenv().getOrDefault("APP_TIMEZONE", "Asia/Ho_Chi_Minh")));
    }

    public static void main(String[] args) {
        SpringApplication.run(EquineCareApplication.class, args);
    }

}