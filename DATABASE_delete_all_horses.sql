-- =====================================================================================
-- XÓA TOÀN BỘ NGỰA (bảng Horses) VÀ MỌI DỮ LIỆU ĐI THEO NGỰA, ĐỂ TẠO LẠI NGỰA MỚI
--
-- Xóa : Horses + hồ sơ đăng ký ngựa (kèm giấy tờ, kết quả kiểm tra), giáo án / buổi tập, thi đấu,
--       ca khám, bệnh án, phác đồ, tiêm phòng, khẩu phần, việc chăm sóc, hóa đơn / giao dịch thanh toán,
--       hàng đang giữ cho phác đồ.
-- Giữ : toàn bộ Users và hồ sơ nhân viên / chủ ngựa, chuồng (Stalls, tự trống khi ngựa bị xóa),
--       danh mục & tồn kho (Inventory_Items, Inventory_Lots, Stock_Transactions), nhà cung cấp, đơn mua,
--       kiểm kê, Audit_Logs, Email_Logs.
--
-- @DryRun = 1 (mặc định): chạy toàn bộ rồi ROLLBACK, chỉ in số dòng sẽ bị xóa. Không thay đổi gì.
-- @DryRun = 0: xóa thật và COMMIT.
-- Chạy trong 1 transaction: lỗi ở bất kỳ bước nào thì không có gì bị xóa.
-- CẢNH BÁO: KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT. Hóa đơn / giao dịch thanh toán cũng bị xóa. Hãy sao lưu DB trước.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @DryRun BIT = 1;   -- đổi thành 0 để xóa thật

-- Số dòng trước khi xóa
SELECT 'Horses' AS bang, COUNT(*) AS so_dong FROM Horses
UNION ALL SELECT 'Horse_Registrations', COUNT(*) FROM Horse_Registrations
UNION ALL SELECT 'Training_Plans', COUNT(*) FROM Training_Plans
UNION ALL SELECT 'training_sessions', COUNT(*) FROM training_sessions
UNION ALL SELECT 'Training_Days', COUNT(*) FROM Training_Days
UNION ALL SELECT 'Competition_Entries', COUNT(*) FROM Competition_Entries
UNION ALL SELECT 'Vet_Cases', COUNT(*) FROM Vet_Cases
UNION ALL SELECT 'Medical_Records', COUNT(*) FROM Medical_Records
UNION ALL SELECT 'Treatment_Protocols', COUNT(*) FROM Treatment_Protocols
UNION ALL SELECT 'Vaccinations', COUNT(*) FROM Vaccinations
UNION ALL SELECT 'Nutrition_Plans', COUNT(*) FROM Nutrition_Plans
UNION ALL SELECT 'Daily_Tasks', COUNT(*) FROM Daily_Tasks
UNION ALL SELECT 'Invoices', COUNT(*) FROM Invoices
UNION ALL SELECT 'Payment_Transactions', COUNT(*) FROM Payment_Transactions
UNION ALL SELECT 'Stock_Holds', COUNT(*) FROM Stock_Holds;

BEGIN TRAN;

-- ---------- 1. Việc chăm sóc, khẩu phần ----------
DELETE FROM Task_Material_Usage;
DELETE FROM Daily_Tasks;
DELETE FROM care_tasks;
DELETE FROM Diet_Change_Requests;
DELETE FROM Nutrition_Plan_Details;
DELETE FROM Nutrition_Plans;

-- ---------- 2. Hóa đơn, thanh toán (hóa đơn nào cũng gắn với một ngựa) ----------
DELETE FROM Payment_Transactions;
DELETE FROM Invoice_Line_Items;
DELETE FROM Invoices;

-- ---------- 3. Kho: bỏ hàng đang giữ và liên kết với phác đồ (tồn kho, lịch sử nhập / xuất giữ nguyên) ----------
DELETE FROM Stock_Holds;
UPDATE Inventory_Items SET held_stock = 0 WHERE held_stock <> 0;
UPDATE Purchase_Orders SET protocol_id = NULL WHERE protocol_id IS NOT NULL;

-- ---------- 4. Y tế ----------
DELETE FROM prescriptions;
DELETE FROM treatment_stages;
DELETE FROM Protocol_Progress_Notes;
DELETE FROM Protocol_Items;
DELETE FROM Protocol_Phases;
-- Phác đồ có phiên bản (replaces_protocol_id, không được để NULL ở bản sau): xóa bản mới nhất trước, lặp tới hết
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

-- ---------- 5. Huấn luyện, thi đấu ----------
DELETE FROM training_sessions;
DELETE FROM Session_Alerts;
DELETE FROM Session_Readings;
DELETE FROM Training_Days;
DELETE FROM Training_Plan_Phases;
DELETE FROM Training_Plans;
DELETE FROM Competition_Entries;

-- ---------- 6. Ngựa ----------
DELETE FROM Registration_Checks;
DELETE FROM Registration_Attachments;
DELETE FROM Horse_Registrations;
DELETE FROM Horses;

-- Kiểm tra trong transaction
SELECT (SELECT COUNT(*) FROM Horses) AS horses, (SELECT COUNT(*) FROM Horse_Registrations) AS registrations,
       (SELECT COUNT(*) FROM Training_Plans) AS training_plans, (SELECT COUNT(*) FROM Vet_Cases) AS vet_cases,
       (SELECT COUNT(*) FROM Invoices) AS invoices, (SELECT COUNT(*) FROM Users) AS users_giu_nguyen,
       (SELECT COUNT(*) FROM Inventory_Items) AS inventory_items_giu_nguyen, (SELECT COUNT(*) FROM Stalls) AS stalls_giu_nguyen;

IF @DryRun = 1
BEGIN
    ROLLBACK;
    PRINT N'DRY RUN: đã ROLLBACK, không có gì bị xóa. Đổi @DryRun = 0 để xóa thật.';
END
ELSE
BEGIN
    COMMIT;
    PRINT N'Đã xóa toàn bộ ngựa và dữ liệu liên quan.';
END;
