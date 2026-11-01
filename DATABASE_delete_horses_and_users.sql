-- =====================================================================================
-- XÓA TOÀN BỘ NGỰA, GIẢI ĐẤU VÀ TOÀN BỘ NGƯỜI DÙNG (TRỪ TÀI KHOẢN ADMIN)
--
-- Xóa : Horses và mọi dữ liệu đi theo ngựa (hồ sơ đăng ký, giáo án / buổi tập, đề xuất thi đấu, ca khám, bệnh án,
--       phác đồ, tiêm phòng, khẩu phần, việc chăm sóc, hóa đơn / giao dịch thanh toán, hàng giữ cho phác đồ,
--       yêu cầu phân công), giải đấu (External_Competitions, Competition_Prizes), yêu cầu vật tư,
--       thông báo chuông của người bị xóa;
--       Users (trừ admin) kèm hồ sơ nhân viên / chủ ngựa và phiên đăng nhập.
-- Giữ : tài khoản admin; chuồng (Stalls, tự trống);
--       danh mục & tồn kho (Inventory_Items, Inventory_Lots), nhà cung cấp, đơn mua, kiểm kê;
--       Stock_Transactions (nhật ký kho, người thực hiện chuyển sang admin để tồn kho không lệch);
--       Audit_Logs, Email_Logs (user_id tự chuyển NULL).
--
-- @DryRun = 1 (mặc định): chạy toàn bộ rồi ROLLBACK, chỉ in số dòng trước / sau. Không thay đổi gì.
-- @DryRun = 0: xóa thật và COMMIT.
-- Chạy trong 1 transaction: lỗi ở bất kỳ bước nào thì không có gì bị xóa.
-- CẢNH BÁO: KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT. Hãy sao lưu DB trước.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @DryRun BIT = 1;                          -- đổi thành 0 để xóa thật
DECLARE @AdminUsername NVARCHAR(100) = N'admin';  -- tài khoản được giữ lại

DECLARE @Admin INT = (SELECT user_id FROM Users WHERE username = @AdminUsername);
IF @Admin IS NULL
BEGIN
    RAISERROR(N'Không tìm thấy tài khoản "%s", dừng lại.', 16, 1, @AdminUsername);
    RETURN;
END;

-- Số dòng trước khi xóa
SELECT 'Users' AS bang, COUNT(*) AS so_dong FROM Users
UNION ALL SELECT 'Horses', COUNT(*) FROM Horses
UNION ALL SELECT 'Horse_Registrations', COUNT(*) FROM Horse_Registrations
UNION ALL SELECT 'Competition_Entries', COUNT(*) FROM Competition_Entries
UNION ALL SELECT 'External_Competitions', COUNT(*) FROM External_Competitions
UNION ALL SELECT 'Competition_Prizes', COUNT(*) FROM Competition_Prizes
UNION ALL SELECT 'Training_Plans', COUNT(*) FROM Training_Plans
UNION ALL SELECT 'Vet_Cases', COUNT(*) FROM Vet_Cases
UNION ALL SELECT 'Treatment_Protocols', COUNT(*) FROM Treatment_Protocols
UNION ALL SELECT 'Invoices', COUNT(*) FROM Invoices
UNION ALL SELECT 'Material_Requisitions', COUNT(*) FROM Material_Requisitions
UNION ALL SELECT 'Stock_Transactions (giữ)', COUNT(*) FROM Stock_Transactions
UNION ALL SELECT 'Refresh_Tokens', COUNT(*) FROM Refresh_Tokens
UNION ALL SELECT 'App_Notifications', COUNT(*) FROM App_Notifications;

BEGIN TRAN;

-- =================== PHẦN 1: NGỰA VÀ DỮ LIỆU ĐI THEO NGỰA ===================

-- 1.1 Việc chăm sóc, khẩu phần
DELETE FROM Task_Material_Usage;
DELETE FROM Daily_Tasks;
DELETE FROM care_tasks;
DELETE FROM Diet_Change_Requests;
DELETE FROM Nutrition_Plan_Details;
DELETE FROM Nutrition_Plans;

-- 1.2 Thi đấu (trước hóa đơn: Competition_Entries.invoice_id)
DELETE FROM Competition_Entries;

-- 1.3 Hóa đơn, thanh toán
DELETE FROM Payment_Transactions;
DELETE FROM Invoice_Line_Items;
DELETE FROM Invoices;

-- 1.4 Kho: bỏ hàng đang giữ và liên kết với phác đồ / yêu cầu vật tư (tồn kho giữ nguyên)
DELETE FROM Stock_Holds;
UPDATE Inventory_Items SET held_stock = 0 WHERE held_stock <> 0;
UPDATE Purchase_Orders SET protocol_id = NULL WHERE protocol_id IS NOT NULL;
UPDATE Purchase_Orders SET requisition_id = NULL WHERE requisition_id IS NOT NULL;
DELETE FROM Material_Requisitions;

-- 1.5 Y tế
DELETE FROM prescriptions;
DELETE FROM treatment_stages;
DELETE FROM Protocol_Progress_Notes;
DELETE FROM Protocol_Items;
DELETE FROM Protocol_Phases;
-- Phác đồ có phiên bản (replaces_protocol_id): xóa bản mới nhất trước, lặp tới hết
WHILE EXISTS (SELECT 1 FROM Treatment_Protocols)
BEGIN
    DELETE p FROM Treatment_Protocols p
    WHERE NOT EXISTS (SELECT 1 FROM Treatment_Protocols n WHERE n.replaces_protocol_id = p.protocol_id);
END;
DELETE FROM Prescription_Items;
DELETE FROM Injury_Marks;
DELETE FROM Medical_Records;
DELETE FROM Vet_Cases;          -- trước training_sessions (Vet_Cases.training_day_id)
DELETE FROM Vaccinations;

-- 1.6 Huấn luyện
DELETE FROM training_sessions;
DELETE FROM Session_Alerts;
DELETE FROM Session_Readings;
DELETE FROM Training_Days;
DELETE FROM Training_Plan_Phases;
DELETE FROM Training_Plans;

-- 1.7 Ngựa
DELETE FROM Assignment_Requests;   -- yêu cầu phân công HLV / Groom (không có khóa ngoại)
DELETE FROM Registration_Checks;
DELETE FROM Registration_Attachments;
DELETE FROM Horse_Registrations;
DELETE FROM Horses;

-- 1.8 Giải đấu (sau Competition_Entries và Training_Plans, hai bảng tham chiếu tới giải)
DELETE FROM Competition_Prizes;
DELETE FROM External_Competitions;

-- =================== PHẦN 2: NGƯỜI DÙNG (TRỪ ADMIN) ===================

-- 2.1 Dữ liệu kho bắt buộc có người thực hiện: chuyển sang admin để giữ lịch sử và tồn kho
UPDATE Stock_Transactions SET performed_by = @Admin WHERE performed_by <> @Admin;
UPDATE Stock_Counts       SET counted_by   = @Admin WHERE counted_by   <> @Admin;

-- 2.2 Các liên kết cho phép trống
UPDATE Purchase_Orders SET created_by = NULL        WHERE created_by <> @Admin;
UPDATE Stalls          SET assigned_groom_id = NULL WHERE assigned_groom_id IS NOT NULL AND assigned_groom_id <> @Admin;

-- 2.3 Thông báo chuông, phiên đăng nhập, hồ sơ, tài khoản
DELETE FROM App_Notifications     WHERE user_id <> @Admin;
DELETE FROM Refresh_Tokens        WHERE user_id <> @Admin;
DELETE FROM Trainer_Profiles      WHERE user_id <> @Admin;
DELETE FROM Groom_Profiles        WHERE user_id <> @Admin;
DELETE FROM Veterinarian_Profiles WHERE user_id <> @Admin;
DELETE FROM Club_Manager_Profiles WHERE user_id <> @Admin;
DELETE FROM Horse_Owner_Profiles  WHERE user_id <> @Admin;
DELETE FROM Users                 WHERE user_id <> @Admin;   -- Audit_Logs / Email_Logs tự chuyển user_id = NULL

-- Kiểm tra trong transaction
SELECT (SELECT COUNT(*) FROM Users) AS users_con_lai, (SELECT STRING_AGG(username, ', ') FROM Users) AS tai_khoan_con_lai,
       (SELECT COUNT(*) FROM Horses) AS horses, (SELECT COUNT(*) FROM Invoices) AS invoices,
       (SELECT COUNT(*) FROM Competition_Entries) AS competition_entries,
       (SELECT COUNT(*) FROM External_Competitions) AS giai_dau, (SELECT COUNT(*) FROM Competition_Prizes) AS bang_thuong,
       (SELECT COUNT(*) FROM Stock_Transactions) AS stock_transactions_giu_nguyen,
       (SELECT COUNT(*) FROM Inventory_Items) AS inventory_items_giu_nguyen,
       (SELECT COUNT(*) FROM Stalls) AS stalls_giu_nguyen;

IF @DryRun = 1
BEGIN
    ROLLBACK;
    PRINT N'DRY RUN: đã ROLLBACK, không có gì bị xóa. Đổi @DryRun = 0 để xóa thật.';
END
ELSE
BEGIN
    COMMIT;
    PRINT N'Đã xóa toàn bộ ngựa, giải đấu và người dùng (trừ admin).';
END;
