-- Giáo án loại "Khác": HLV tự viết mô tả và các bước cho Mã phu.
-- Thêm 2 cột cho phép NULL vào Training_Plans. Chạy TRƯỚC khi deploy BE mới
-- (BE mới đọc 2 cột này; BE cũ không dùng nên chạy trước vẫn an toàn).
-- Chạy lại nhiều lần không lỗi.

IF COL_LENGTH('Training_Plans', 'custom_summary') IS NULL
    ALTER TABLE Training_Plans ADD custom_summary NVARCHAR(1000) NULL;
GO
IF COL_LENGTH('Training_Plans', 'custom_steps') IS NULL
    ALTER TABLE Training_Plans ADD custom_steps NVARCHAR(MAX) NULL;  -- mỗi dòng một bước
GO

-- Kiểm tra
SELECT name, max_length FROM sys.columns
WHERE object_id = OBJECT_ID('Training_Plans') AND name IN ('custom_summary', 'custom_steps');
