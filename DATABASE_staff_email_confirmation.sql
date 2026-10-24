-- =====================================================================================
-- Nhân viên xác nhận tài khoản qua email - chỉ THÊM cột, không xóa dữ liệu. Chạy lại nhiều lần vẫn an toàn.
-- Phải chạy TRƯỚC khi deploy bản backend mới (entity User map các cột này).
--  Users.email_verified_at      : thời điểm xác nhận email (NULL = chưa xác nhận -> chưa đăng nhập / chưa được phân công)
--  Users.email_verify_token_hash: SHA-256 của mã xác nhận gửi trong email (không lưu mã gốc)
--  Users.email_verify_expires_at: hạn của mã xác nhận
-- Tài khoản đã có trước khi chạy script được coi là đã xác nhận (đang dùng bình thường).
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRAN;

IF COL_LENGTH('Users', 'email_verified_at') IS NULL
BEGIN
    ALTER TABLE Users ADD email_verified_at DATETIME2 NULL;
    EXEC('UPDATE Users SET email_verified_at = created_at WHERE email_verified_at IS NULL');
END
IF COL_LENGTH('Users', 'email_verify_token_hash') IS NULL
    ALTER TABLE Users ADD email_verify_token_hash VARCHAR(64) NULL;
IF COL_LENGTH('Users', 'email_verify_expires_at') IS NULL
    ALTER TABLE Users ADD email_verify_expires_at DATETIME2 NULL;

COMMIT;

SELECT COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'Users' AND COLUMN_NAME IN ('email_verified_at', 'email_verify_token_hash', 'email_verify_expires_at');
EXEC('SELECT COUNT(*) AS users_total, SUM(CASE WHEN email_verified_at IS NULL THEN 1 ELSE 0 END) AS chua_xac_nhan FROM Users');
