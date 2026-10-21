-- =====================================================================
-- Sua loi mat dau tieng Viet o loai hoat dong cua buoi tap.
-- Cot training_sessions.activity_type do Hibernate tao la VARCHAR(100) nen
-- "Thả lỏng · dưỡng cát" bi luu thanh "Th? l?ng · du?ng cát".
-- Doi sang NVARCHAR(100) (giu NOT NULL). Du lieu cu da mat dau thi khong khoi phuc duoc,
-- can nhap lai (DB vua reset nen hien khong con dong nao).
-- Cot khong co index / rang buoc nao nen doi truc tiep duoc. Chay lai van an toan.
-- =====================================================================
USE EquineCareDB;
GO

IF EXISTS (SELECT 1 FROM sys.columns c JOIN sys.types t ON c.user_type_id = t.user_type_id
           WHERE c.object_id = OBJECT_ID('dbo.training_sessions') AND c.name = 'activity_type' AND t.name = 'varchar')
    ALTER TABLE dbo.training_sessions ALTER COLUMN activity_type NVARCHAR(100) NOT NULL;
GO

-- Kiem tra: type phai la nvarchar
SELECT c.name, t.name AS type, c.max_length / 2 AS so_ky_tu, c.is_nullable
FROM sys.columns c JOIN sys.types t ON c.user_type_id = t.user_type_id
WHERE c.object_id = OBJECT_ID('dbo.training_sessions') AND c.name = 'activity_type';
GO
