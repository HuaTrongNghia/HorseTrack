-- Bảng thông báo in-app (chuông). ddl-auto=none nên chạy tay nếu bootstrap Java thất bại.
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
