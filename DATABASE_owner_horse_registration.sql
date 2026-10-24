-- =====================================================================================
-- Đăng ký chủ ngựa / đăng ký ngựa - chỉ THÊM cột / chỉ mục, không xóa dữ liệu. Chạy lại nhiều lần vẫn an toàn.
-- Phải chạy TRƯỚC khi deploy bản backend mới (entity map các cột này).
--  1. Registration_Attachments.file_content: lưu nội dung tệp giấy tờ chủ ngựa đính kèm hồ sơ
--  2. Horses.height_cm: thống nhất chiều cao theo cm (điền từ height_hands cũ: 1 hand = 10.16 cm)
--  3. UX_Users_phone: số điện thoại không trùng (đăng nhập được bằng số điện thoại)
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRAN;

-- ---------- 1. Nội dung tệp đính kèm ----------
IF COL_LENGTH('Registration_Attachments', 'file_content') IS NULL
    ALTER TABLE Registration_Attachments ADD file_content VARBINARY(MAX) NULL;

-- ---------- 2. Chiều cao theo cm ----------
IF COL_LENGTH('Horses', 'height_cm') IS NULL
    ALTER TABLE Horses ADD height_cm DECIMAL(5, 1) NULL;
EXEC('UPDATE Horses SET height_cm = ROUND(height_hands * 10.16, 1) WHERE height_cm IS NULL AND height_hands IS NOT NULL');

-- ---------- 3. Số điện thoại duy nhất ----------
IF EXISTS (SELECT phone FROM Users WHERE phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1)
BEGIN
    SELECT user_id, username, phone FROM Users
    WHERE phone IN (SELECT phone FROM Users WHERE phone IS NOT NULL GROUP BY phone HAVING COUNT(*) > 1);
    RAISERROR(N'Có số điện thoại trùng giữa các tài khoản (danh sách ở trên). Hãy sửa rồi chạy lại script.', 16, 1);
    ROLLBACK;
    RETURN;
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_Users_phone' AND object_id = OBJECT_ID('Users'))
    CREATE UNIQUE NONCLUSTERED INDEX UX_Users_phone ON Users (phone) WHERE phone IS NOT NULL;

COMMIT;

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS
WHERE (TABLE_NAME = 'Registration_Attachments' AND COLUMN_NAME = 'file_content') OR (TABLE_NAME = 'Horses' AND COLUMN_NAME = 'height_cm');
SELECT name, filter_definition FROM sys.indexes WHERE name = 'UX_Users_phone';
