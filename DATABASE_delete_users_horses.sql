-- =====================================================================================
-- XÓA DỮ LIỆU NGỰA VÀ NGƯỜI DÙNG (giữ lại 1 tài khoản Club Manager để đăng nhập quản trị)
--
-- Xóa : toàn bộ Horses, Users (trừ @KeepUsername) và mọi dữ liệu đi theo:
--       hồ sơ đăng ký, giáo án / buổi tập, ca khám, bệnh án, phác đồ, khẩu phần, việc chăm sóc,
--       hóa đơn / giao dịch, yêu cầu vật tư, hồ sơ nhân viên / chủ ngựa, refresh token.
-- Giữ : danh mục & tồn kho (Inventory_Items, Inventory_Lots, Stock_Transactions), nhà cung cấp, đơn mua,
--       kiểm kê, 15 chuồng (Stalls), Audit_Logs (user_id tự chuyển NULL khi xóa user).
--       Người thực hiện của các bản ghi kho được chuyển sang tài khoản giữ lại.
--
-- Chạy trong 1 transaction: lỗi ở bất kỳ bước nào thì không có gì bị xóa.
-- CẢNH BÁO: KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT. Hãy sao lưu DB trước khi chạy.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @KeepUsername NVARCHAR(100) = N'admin';   -- tài khoản Club Manager được giữ lại
DECLARE @KeepId INT = (SELECT user_id FROM Users WHERE username = @KeepUsername AND role_name = 'Club Manager');
IF @KeepId IS NULL
BEGIN
    RAISERROR(N'Không tìm thấy tài khoản Club Manager "%s" để giữ lại. Sửa @KeepUsername rồi chạy lại.', 16, 1, @KeepUsername);
    RETURN;
END;

BEGIN TRAN;

-- ---------- 1. Việc chăm sóc, khẩu phần ----------
DELETE FROM Task_Material_Usage;
DELETE FROM Daily_Tasks;
DELETE FROM care_tasks;
DELETE FROM Diet_Change_Requests;
DELETE FROM Nutrition_Plan_Details;
DELETE FROM Nutrition_Plans;

-- ---------- 2. Hóa đơn, thanh toán ----------
DELETE FROM Payment_Transactions;
DELETE FROM Invoice_Line_Items;
DELETE FROM Invoices;

-- ---------- 3. Kho: bỏ liên kết với phác đồ / yêu cầu vật tư, chuyển người thực hiện sang tài khoản giữ lại ----------
DELETE FROM Stock_Holds;                                   -- hàng đang giữ cho phác đồ
UPDATE Inventory_Items SET held_stock = 0;
UPDATE Purchase_Orders SET purpose = 'RESTOCK', protocol_id = NULL, requisition_id = NULL
    WHERE purpose IN ('PROTOCOL', 'REQUISITION');
UPDATE Purchase_Orders SET created_by = @KeepId WHERE created_by IS NOT NULL AND created_by <> @KeepId;
UPDATE Stock_Transactions SET performed_by = @KeepId WHERE performed_by IS NOT NULL AND performed_by <> @KeepId;
UPDATE Stock_Counts SET counted_by = @KeepId WHERE counted_by IS NOT NULL AND counted_by <> @KeepId;
DELETE FROM Material_Requisitions;

-- ---------- 4. Y tế ----------
DELETE FROM prescriptions;
DELETE FROM treatment_stages;
DELETE FROM Protocol_Progress_Notes;
DELETE FROM Protocol_Items;
DELETE FROM Protocol_Phases;
DELETE FROM Treatment_Protocols;
DELETE FROM Prescription_Items;
DELETE FROM Injury_Marks;
DELETE FROM Medical_Records;
DELETE FROM Vet_Cases;
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

-- ---------- 7. Người dùng (trừ tài khoản giữ lại) ----------
UPDATE Stalls SET assigned_groom_id = NULL WHERE assigned_groom_id IS NOT NULL;
DELETE FROM Refresh_Tokens WHERE user_id <> @KeepId;
DELETE FROM Groom_Profiles WHERE user_id <> @KeepId;
DELETE FROM Trainer_Profiles WHERE user_id <> @KeepId;
DELETE FROM Veterinarian_Profiles WHERE user_id <> @KeepId;
DELETE FROM Horse_Owner_Profiles WHERE user_id <> @KeepId;
DELETE FROM Club_Manager_Profiles WHERE user_id <> @KeepId;
DELETE FROM Users WHERE user_id <> @KeepId;               -- Audit_Logs.user_id tự chuyển NULL

COMMIT;

-- Kiểm tra sau khi xóa
SELECT (SELECT COUNT(*) FROM Users) AS users, (SELECT COUNT(*) FROM Horses) AS horses,
       (SELECT COUNT(*) FROM Training_Plans) AS training_plans, (SELECT COUNT(*) FROM Invoices) AS invoices,
       (SELECT COUNT(*) FROM Inventory_Items) AS inventory_items, (SELECT COUNT(*) FROM Stalls) AS stalls;
SELECT user_id, username, role_name FROM Users;
