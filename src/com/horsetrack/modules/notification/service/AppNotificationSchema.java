package com.horsetrack.equinecare.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;

/** ddl-auto=none: tự tạo bảng chuông nếu chưa có. */
@Slf4j
@Component
public class AppNotificationSchema {

    public AppNotificationSchema(DataSource dataSource) {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("""
                    IF OBJECT_ID(N'dbo.App_Notifications', N'U') IS NULL
                    BEGIN
                        CREATE TABLE dbo.App_Notifications (
                            notification_id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_App_Notifications PRIMARY KEY,
                            user_id INT NOT NULL,
                            purpose VARCHAR(40) NOT NULL,
                            title NVARCHAR(255) NOT NULL,
                            message NVARCHAR(1000) NOT NULL,
                            link NVARCHAR(255) NULL,
                            is_read BIT NOT NULL CONSTRAINT DF_App_Notifications_read DEFAULT (0),
                            created_at DATETIME2 NOT NULL
                        );
                        CREATE INDEX IX_App_Notifications_user ON dbo.App_Notifications(user_id, is_read, created_at DESC);
                    END
                    """);
        } catch (Exception ex) {
            log.error("Chưa tạo được bảng App_Notifications. Chạy script src/main/resources/db/app_notifications.sql. {}", ex.getMessage());
        }
    }
}
