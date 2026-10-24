-- =====================================================================================
-- XÓA MỘT TÀI KHOẢN NGƯỜI DÙNG (ví dụ tài khoản tạo nhầm / tài khoản test)
--
-- Lỗi "The DELETE statement conflicted with the REFERENCE constraint FK1lih5y2npsf8u5o3vhdb9y0os" là do bảng
-- Refresh_Tokens (phiên đăng nhập) còn dòng của tài khoản. Script này xóa theo đúng thứ tự:
--   1. Kiểm tra tài khoản có dữ liệu nghiệp vụ bắt buộc (việc chăm sóc, giáo án, bệnh án, hóa đơn, phiếu kho...) không.
--      Có thì DỪNG, không xóa gì: tài khoản đã làm việc thì nên KHÓA (PUT /api/v1/users/{id}/lock) để giữ lịch sử.
--   2. Bỏ liên kết ở các cột cho phép trống (người duyệt, HLV phụ trách ngựa, Groom phụ trách chuồng...).
--   3. Xóa phiên đăng nhập, hồ sơ nhân viên / chủ ngựa, rồi xóa tài khoản.
--   Audit_Logs và Email_Logs được giữ lại (user_id tự chuyển NULL).
--
-- Mặc định @DryRun = 1: chạy thử, in số dòng bị ảnh hưởng rồi ROLLBACK (không đổi gì).
-- Kiểm tra kết quả xong, đổi @DryRun = 0 để xóa thật. KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @Username NVARCHAR(100) = N'HanTin';   -- tên đăng nhập cần xóa
DECLARE @DryRun BIT = 1;                         -- 1 = chạy thử (rollback), 0 = xóa thật

DECLARE @Id INT, @Role NVARCHAR(30);
SELECT @Id = user_id, @Role = role_name FROM Users WHERE username = @Username;
IF @Id IS NULL
BEGIN
    RAISERROR(N'Không tìm thấy tài khoản "%s".', 16, 1, @Username);
    RETURN;
END;
IF @Role = 'Club Manager' AND (SELECT COUNT(*) FROM Users WHERE role_name = 'Club Manager' AND user_id <> @Id) = 0
BEGIN
    RAISERROR(N'"%s" là Quản lý CLB duy nhất, không được xóa.', 16, 1, @Username);
    RETURN;
END;

-- ---------- 1. Dữ liệu nghiệp vụ bắt buộc (cột NOT NULL): có thì dừng ----------
DECLARE @Blocking TABLE (table_name NVARCHAR(100), rows_count INT);
INSERT INTO @Blocking
SELECT t, n FROM (VALUES
    (N'care_tasks (việc chăm sóc cũ)',            (SELECT COUNT(*) FROM care_tasks WHERE assigned_to = @Id)),
    (N'Daily_Tasks (việc chăm sóc)',              (SELECT COUNT(*) FROM Daily_Tasks WHERE groom_id = @Id)),
    (N'Diet_Change_Requests (đề xuất thức ăn)',   (SELECT COUNT(*) FROM Diet_Change_Requests WHERE requested_by = @Id)),
    (N'Horses (ngựa sở hữu)',                     (SELECT COUNT(*) FROM Horses WHERE owner_id = @Id)),
    (N'Horse_Registrations (hồ sơ ngựa)',         (SELECT COUNT(*) FROM Horse_Registrations WHERE owner_id = @Id)),
    (N'Invoices (hóa đơn)',                       (SELECT COUNT(*) FROM Invoices WHERE owner_id = @Id)),
    (N'Material_Requisitions (yêu cầu vật tư)',   (SELECT COUNT(*) FROM Material_Requisitions WHERE requested_by = @Id)),
    (N'Medical_Records (bệnh án)',                (SELECT COUNT(*) FROM Medical_Records WHERE vet_id = @Id)),
    (N'Nutrition_Plans (khẩu phần)',              (SELECT COUNT(*) FROM Nutrition_Plans WHERE created_by_id = @Id)),
    (N'Protocol_Progress_Notes (tiến trình)',     (SELECT COUNT(*) FROM Protocol_Progress_Notes WHERE written_by = @Id)),
    (N'Session_Alerts (cảnh báo buổi tập)',       (SELECT COUNT(*) FROM Session_Alerts WHERE recipient_id = @Id)),
    (N'Stock_Counts (kiểm kê)',                   (SELECT COUNT(*) FROM Stock_Counts WHERE counted_by = @Id)),
    (N'Stock_Holds (giữ hàng phác đồ)',           (SELECT COUNT(*) FROM Stock_Holds WHERE checked_by = @Id)),
    (N'Stock_Transactions (nhật ký kho)',         (SELECT COUNT(*) FROM Stock_Transactions WHERE performed_by = @Id)),
    (N'Training_Days (ngày tập)',                 (SELECT COUNT(*) FROM Training_Days WHERE trainer_id = @Id)),
    (N'Training_Plans (giáo án)',                 (SELECT COUNT(*) FROM Training_Plans WHERE head_trainer_id = @Id)),
    (N'Treatment_Protocols (phác đồ)',            (SELECT COUNT(*) FROM Treatment_Protocols WHERE vet_id = @Id)),
    (N'Vet_Cases (ca khám đã báo)',               (SELECT COUNT(*) FROM Vet_Cases WHERE reported_by = @Id))
) v(t, n) WHERE n > 0;

IF EXISTS (SELECT 1 FROM @Blocking)
BEGIN
    SELECT * FROM @Blocking;
    RAISERROR(N'Tài khoản "%s" đã có dữ liệu nghiệp vụ (danh sách ở trên). Không xóa để giữ lịch sử - hãy khóa tài khoản (PUT /api/v1/users/{id}/lock).', 16, 1, @Username);
    RETURN;
END;

BEGIN TRAN;

-- ---------- 2. Bỏ liên kết ở các cột cho phép trống ----------
UPDATE Competition_Entries    SET proposed_by = NULL         WHERE proposed_by = @Id;
UPDATE Competition_Entries    SET vet_id = NULL              WHERE vet_id = @Id;
UPDATE Diet_Change_Requests   SET decided_by = NULL          WHERE decided_by = @Id;
UPDATE Horse_Registrations    SET decided_by = NULL          WHERE decided_by = @Id;
UPDATE Horses                 SET assigned_trainer_id = NULL WHERE assigned_trainer_id = @Id;
UPDATE Horses                 SET locked_by = NULL           WHERE locked_by = @Id;
UPDATE Injury_Marks           SET created_by = NULL          WHERE created_by = @Id;
UPDATE Material_Requisitions  SET issued_by = NULL           WHERE issued_by = @Id;
UPDATE Material_Requisitions  SET issued_to = NULL           WHERE issued_to = @Id;
UPDATE Payment_Transactions   SET verified_by = NULL         WHERE verified_by = @Id;
UPDATE Purchase_Orders        SET created_by = NULL          WHERE created_by = @Id;
UPDATE Registration_Checks    SET checked_by = NULL          WHERE checked_by = @Id;
UPDATE Stalls                 SET assigned_groom_id = NULL   WHERE assigned_groom_id = @Id;
UPDATE Training_Days          SET decided_by = NULL          WHERE decided_by = @Id;
UPDATE Training_Days          SET evaluated_by = NULL        WHERE evaluated_by = @Id;
UPDATE Training_Days          SET groom_id = NULL            WHERE groom_id = @Id;
UPDATE training_sessions      SET assigned_to = NULL         WHERE assigned_to = @Id;
UPDATE Vaccinations           SET done_by = NULL             WHERE done_by = @Id;
UPDATE Vet_Cases              SET assigned_vet_id = NULL     WHERE assigned_vet_id = @Id;

-- ---------- 3. Phiên đăng nhập, hồ sơ, tài khoản ----------
DELETE FROM Refresh_Tokens         WHERE user_id = @Id;
DELETE FROM Trainer_Profiles       WHERE user_id = @Id;
DELETE FROM Groom_Profiles         WHERE user_id = @Id;
DELETE FROM Veterinarian_Profiles  WHERE user_id = @Id;
DELETE FROM Club_Manager_Profiles  WHERE user_id = @Id;
DELETE FROM Horse_Owner_Profiles   WHERE user_id = @Id;
DELETE FROM Users                  WHERE user_id = @Id;

SELECT @Username AS username, @Id AS user_id, @Role AS role_name,
       CASE WHEN EXISTS (SELECT 1 FROM Users WHERE user_id = @Id) THEN N'CÒN' ELSE N'ĐÃ XÓA' END AS ket_qua,
       CASE WHEN @DryRun = 1 THEN N'CHẠY THỬ - đã ROLLBACK, không đổi gì' ELSE N'ĐÃ COMMIT' END AS che_do;

IF @DryRun = 1
    ROLLBACK;
ELSE
    COMMIT;
