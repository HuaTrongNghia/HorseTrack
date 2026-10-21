-- =====================================================================
-- XOA TOAN BO DU LIEU cua EquineCareDB, GIU NGUYEN cau truc
-- (bang, cot, khoa chinh, khoa ngoai, CHECK, index, cot tinh toan).
--
-- Script lam gi:
--   1. Tat tam thoi khoa ngoai / CHECK de xoa khong phai theo thu tu.
--   2. DELETE du lieu trong MOI bang (tru sysdiagrams).
--   3. Dua bo dem IDENTITY ve 0 -> dong dau tien them vao se co id = 1
--      (cac ma tu sinh nhu HT-0001, VT-0001, PD-0001... cung bat dau lai tu 0001).
--   4. Bat lai khoa ngoai / CHECK (WITH CHECK).
--   5. Tao lai 1 tai khoan Club Manager de co the dang nhap tao nhan vien
--      (API khong co chuc nang tao Club Manager).
--
-- KHONG THE HOAN TAC sau khi COMMIT. Hay sao luu (backup) DB truoc neu can giu du lieu cu.
-- Nen dung server backend truoc khi chay de tranh request ghi du lieu xen vao.
-- Toan bo chay trong 1 transaction: loi o bat ky buoc nao thi tu dong hoan tac het.
-- =====================================================================
USE EquineCareDB;
GO

-- ---------- Tai khoan Club Manager se duoc tao lai (sua theo nhom) ----------
-- Mat khau mac dinh: Admin@123  (password_hash ben duoi la BCrypt cua chuoi nay).
-- DOI MAT KHAU ngay sau khi dang nhap lan dau (API doi mat khau / quen mat khau).
DECLARE @AdminUsername VARCHAR(50)    = 'admin';
DECLARE @AdminFullName NVARCHAR(200)  = N'Quản lý CLB';
DECLARE @AdminEmail    VARCHAR(100)   = 'admin@horsetrack.com';   -- nen doi thanh email that de nhan OTP
DECLARE @AdminPhone    VARCHAR(20)    = NULL;
DECLARE @AdminHash     VARCHAR(255)   = '$2a$10$3SHXmiZV6BIVa7.g6MZoGeEW3vZ.X57cm2ytQVmIYd2GW7EEttvK2';
DECLARE @AdminDept     NVARCHAR(100)  = N'Ban quản lý CLB';

-- Kiem tra dang chay dung server/DB
SELECT @@SERVERNAME AS server_name, DB_NAME() AS db_name, 'TRUOC KHI XOA' AS buoc;

SET XACT_ABORT ON;
SET NOCOUNT ON;

DECLARE @tables TABLE (id INT IDENTITY(1,1), full_name NVARCHAR(300), has_identity BIT, ident_used BIT);
INSERT INTO @tables (full_name, has_identity, ident_used)
SELECT QUOTENAME(s.name) + '.' + QUOTENAME(t.name),
       CASE WHEN ic.object_id IS NOT NULL THEN 1 ELSE 0 END,
       CASE WHEN ic.last_value IS NOT NULL THEN 1 ELSE 0 END
FROM sys.tables t
JOIN sys.schemas s ON s.schema_id = t.schema_id
LEFT JOIN sys.identity_columns ic ON ic.object_id = t.object_id
WHERE t.is_ms_shipped = 0
  AND t.name <> 'sysdiagrams';

DECLARE @i INT = 1, @n INT = (SELECT COUNT(*) FROM @tables), @t NVARCHAR(300), @sql NVARCHAR(MAX);

BEGIN TRANSACTION;

-- 1. Tat khoa ngoai va CHECK
SET @i = 1;
WHILE @i <= @n
BEGIN
    SELECT @t = full_name FROM @tables WHERE id = @i;
    SET @sql = N'ALTER TABLE ' + @t + N' NOCHECK CONSTRAINT ALL;';
    EXEC sp_executesql @sql;
    SET @i += 1;
END;

-- 2. Xoa du lieu
SET @i = 1;
WHILE @i <= @n
BEGIN
    SELECT @t = full_name FROM @tables WHERE id = @i;
    SET @sql = N'DELETE FROM ' + @t + N';';
    EXEC sp_executesql @sql;
    SET @i += 1;
END;

-- 3. Dua IDENTITY ve 0 (chi cac bang da tung co du lieu; bang chua tung dung thi giu nguyen)
SET @i = 1;
WHILE @i <= @n
BEGIN
    IF EXISTS (SELECT 1 FROM @tables WHERE id = @i AND has_identity = 1 AND ident_used = 1)
    BEGIN
        SELECT @t = full_name FROM @tables WHERE id = @i;
        SET @sql = N'DBCC CHECKIDENT (''' + REPLACE(@t, '''', '''''') + N''', RESEED, 0) WITH NO_INFOMSGS;';
        EXEC sp_executesql @sql;
    END;
    SET @i += 1;
END;

-- 4. Bat lai khoa ngoai va CHECK (kiem tra lai toan bo, bang rong nen luon hop le)
SET @i = 1;
WHILE @i <= @n
BEGIN
    SELECT @t = full_name FROM @tables WHERE id = @i;
    SET @sql = N'ALTER TABLE ' + @t + N' WITH CHECK CHECK CONSTRAINT ALL;';
    EXEC sp_executesql @sql;
    SET @i += 1;
END;

-- 5. Tao lai tai khoan Club Manager
INSERT INTO dbo.Users (username, password_hash, full_name, email, phone, role_name, is_active, created_at)
VALUES (@AdminUsername, @AdminHash, @AdminFullName, @AdminEmail, @AdminPhone, 'Club Manager', 1, GETDATE());

INSERT INTO dbo.Club_Manager_Profiles (user_id, department, approval_limit)
VALUES (SCOPE_IDENTITY(), @AdminDept, 0);

COMMIT TRANSACTION;
GO

-- ---------- Kiem tra sau khi xoa ----------
-- Moi bang phai = 0 dong, tru Users va Club_Manager_Profiles = 1 dong (tai khoan admin).
SELECT t.name AS bang, SUM(p.rows) AS so_dong
FROM sys.tables t JOIN sys.partitions p ON p.object_id = t.object_id AND p.index_id IN (0, 1)
WHERE t.is_ms_shipped = 0 AND t.name <> 'sysdiagrams'
GROUP BY t.name
HAVING SUM(p.rows) > 0
ORDER BY t.name;

SELECT user_id, username, full_name, email, role_name, is_active FROM dbo.Users;
GO
