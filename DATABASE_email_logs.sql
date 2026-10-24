-- =====================================================================================
-- F1: Nhật ký gửi email - mọi email gửi cho người dùng ghi nhận thành công / thất bại. Chỉ THÊM bảng. Chạy lại nhiều lần vẫn an toàn.
-- Phải chạy TRƯỚC khi deploy bản backend có ghi nhật ký email (thiếu bảng thì email vẫn gửi, chỉ không ghi được nhật ký).
--  status: SENT (gửi thành công), FAILED (máy chủ thư báo lỗi), SKIPPED (chế độ dev chưa cấu hình MAIL_USERNAME, không gửi thật)
--  user_id: tài khoản có email nhận (NULL nếu email chưa có tài khoản, ví dụ OTP đăng ký); xóa tài khoản thì giữ nhật ký, user_id = NULL
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRAN;

IF OBJECT_ID('Email_Logs', 'U') IS NULL
BEGIN
    CREATE TABLE Email_Logs (
        log_id     BIGINT IDENTITY(1, 1) NOT NULL CONSTRAINT PK_Email_Logs PRIMARY KEY,
        to_email   NVARCHAR(255) NOT NULL,
        user_id    INT NULL CONSTRAINT FK_EL_user REFERENCES Users(user_id) ON DELETE SET NULL,
        purpose    VARCHAR(40) NOT NULL,
        subject    NVARCHAR(255) NULL,
        status     VARCHAR(10) NOT NULL CONSTRAINT CK_EL_status CHECK (status IN ('SENT', 'FAILED', 'SKIPPED')),
        error      NVARCHAR(1000) NULL,
        sent_at    DATETIME2 NOT NULL CONSTRAINT DF_EL_sent_at DEFAULT SYSDATETIME()
    );
    CREATE INDEX IX_EL_user_sent ON Email_Logs (user_id, sent_at DESC);
    CREATE INDEX IX_EL_purpose_sent ON Email_Logs (purpose, sent_at DESC);
END

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'Email_Logs' ORDER BY ORDINAL_POSITION;
