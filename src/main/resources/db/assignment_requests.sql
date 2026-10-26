-- Phân công ngựa / khu chờ nhân viên xác nhận.
-- ddl-auto=none: Hibernate không tự tạo bảng. Ứng dụng cũng tự chạy lệnh này lúc khởi động nếu bảng chưa có.
IF OBJECT_ID(N'dbo.Assignment_Requests', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Assignment_Requests (
        request_id INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Assignment_Requests PRIMARY KEY,
        request_type VARCHAR(30) NOT NULL,
        status VARCHAR(20) NOT NULL,
        target_user_id INT NOT NULL,
        horse_id INT NULL,
        previous_user_id INT NULL,
        barn_zone NVARCHAR(80) NULL,
        previous_zone NVARCHAR(80) NULL,
        reject_reason NVARCHAR(500) NULL,
        created_by INT NOT NULL,
        created_at DATETIME2 NOT NULL,
        decided_at DATETIME2 NULL
    );
    CREATE INDEX IX_Assignment_Requests_target ON dbo.Assignment_Requests(status, target_user_id);
END
