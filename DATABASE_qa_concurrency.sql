-- =====================================================================================
-- QA H3 - Chống ghi đè khi thao tác đồng thời. Chỉ THÊM cột / chỉ mục, không xóa dữ liệu. Chạy lại nhiều lần vẫn an toàn.
-- Phải chạy TRƯỚC khi deploy bản backend có @Version (entity map cột version).
--  1. Cột version (khóa lạc quan) cho Training_Sessions, Training_Plans, Horses, Invoices
--  2. Chỉ mục duy nhất có lọc UX_plan_open_per_horse: mỗi ngựa tối đa 1 giáo án PENDING / ACTIVE / PAUSED
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRAN;

-- ---------- 1. Cột version ----------
IF COL_LENGTH('Training_Sessions', 'version') IS NULL
    ALTER TABLE Training_Sessions ADD version BIGINT NOT NULL CONSTRAINT DF_Training_Sessions_version DEFAULT 0;
IF COL_LENGTH('Training_Plans', 'version') IS NULL
    ALTER TABLE Training_Plans ADD version BIGINT NOT NULL CONSTRAINT DF_Training_Plans_version DEFAULT 0;
IF COL_LENGTH('Horses', 'version') IS NULL
    ALTER TABLE Horses ADD version BIGINT NOT NULL CONSTRAINT DF_Horses_version DEFAULT 0;
IF COL_LENGTH('Invoices', 'version') IS NULL
    ALTER TABLE Invoices ADD version BIGINT NOT NULL CONSTRAINT DF_Invoices_version DEFAULT 0;

-- ---------- 2. Mỗi ngựa một giáo án chưa kết thúc ----------
IF EXISTS (SELECT horse_id FROM Training_Plans WHERE status IN ('PENDING', 'ACTIVE', 'PAUSED') GROUP BY horse_id HAVING COUNT(*) > 1)
BEGIN
    SELECT horse_id, plan_id, status FROM Training_Plans
    WHERE status IN ('PENDING', 'ACTIVE', 'PAUSED')
      AND horse_id IN (SELECT horse_id FROM Training_Plans WHERE status IN ('PENDING', 'ACTIVE', 'PAUSED') GROUP BY horse_id HAVING COUNT(*) > 1);
    RAISERROR(N'Có ngựa đang có nhiều hơn 1 giáo án chưa kết thúc (danh sách ở trên). Hãy hủy bớt rồi chạy lại script.', 16, 1);
    ROLLBACK;
    RETURN;
END
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_plan_open_per_horse' AND object_id = OBJECT_ID('Training_Plans'))
    CREATE UNIQUE NONCLUSTERED INDEX UX_plan_open_per_horse ON Training_Plans (horse_id)
        WHERE status IN ('PENDING', 'ACTIVE', 'PAUSED');

COMMIT;

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
WHERE COLUMN_NAME = 'version' AND TABLE_NAME IN ('Training_Sessions', 'Training_Plans', 'Horses', 'Invoices')
ORDER BY TABLE_NAME;
SELECT name, filter_definition FROM sys.indexes WHERE name = 'UX_plan_open_per_horse';
