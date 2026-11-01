-- =====================================================================================
-- XÓA MỘT CON NGỰA VÀ MỌI DỮ LIỆU ĐI THEO NGỰA ĐÓ (VD: ngựa tạo nhầm / ngựa test)
--
-- Xóa : ngựa + hồ sơ đăng ký (giấy tờ, kết quả kiểm tra), giáo án / buổi tập / chỉ số buổi tập, đề xuất thi đấu,
--       ca khám, bệnh án, phác đồ (kèm hàng đang giữ trong kho), tiêm phòng, khẩu phần, việc chăm sóc,
--       hóa đơn / giao dịch thanh toán của ngựa, yêu cầu phân công HLV.
-- Giữ : chủ ngựa và mọi tài khoản, ngựa khác, chuồng (tự trống), giải đấu, tồn kho / nhật ký kho,
--       Audit_Logs, Email_Logs, thông báo chuông đã gửi.
--
-- Chọn ngựa theo tên (+ tên đăng nhập chủ ngựa nếu trùng tên) hoặc điền thẳng @HorseId.
-- @DryRun = 1 (mặc định): chạy toàn bộ rồi ROLLBACK, chỉ in số dòng sẽ bị xóa. @DryRun = 0: xóa thật và COMMIT.
-- CẢNH BÁO: KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT. Hóa đơn / giao dịch thanh toán của ngựa cũng bị xóa.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @HorseName     NVARCHAR(100) = N'O Long';   -- tên ngựa cần xóa
DECLARE @OwnerUsername NVARCHAR(100) = NULL;        -- tên đăng nhập chủ ngựa, điền khi có nhiều ngựa trùng tên
DECLARE @HorseId       INT           = NULL;        -- hoặc điền thẳng horse_id (bỏ qua 2 dòng trên)
DECLARE @DryRun        BIT           = 1;           -- 1 = chạy thử (rollback), 0 = xóa thật

IF @HorseId IS NULL
BEGIN
    DECLARE @Matches INT = (SELECT COUNT(*) FROM Horses h JOIN Users u ON u.user_id = h.owner_id
                            WHERE h.horse_name = @HorseName AND (@OwnerUsername IS NULL OR u.username = @OwnerUsername));
    IF @Matches <> 1
    BEGIN
        SELECT h.horse_id, h.horse_name, u.username AS chu_ngua FROM Horses h JOIN Users u ON u.user_id = h.owner_id
        WHERE h.horse_name = @HorseName;
        RAISERROR(N'Tìm thấy %d ngựa tên "%s". Điền @OwnerUsername hoặc @HorseId cho đúng 1 con rồi chạy lại.', 16, 1, @Matches, @HorseName);
        RETURN;
    END;
    SELECT @HorseId = h.horse_id FROM Horses h JOIN Users u ON u.user_id = h.owner_id
    WHERE h.horse_name = @HorseName AND (@OwnerUsername IS NULL OR u.username = @OwnerUsername);
END
ELSE IF NOT EXISTS (SELECT 1 FROM Horses WHERE horse_id = @HorseId)
BEGIN
    RAISERROR(N'Không có ngựa horse_id = %d.', 16, 1, @HorseId);
    RETURN;
END;

-- Các bản ghi con của ngựa
DECLARE @Plans     TABLE (id INT PRIMARY KEY);   -- Training_Plans
DECLARE @Sessions  TABLE (id INT PRIMARY KEY);   -- training_sessions
DECLARE @Days      TABLE (id INT PRIMARY KEY);   -- Training_Days
DECLARE @Protocols TABLE (id INT PRIMARY KEY);   -- Treatment_Protocols
DECLARE @Invoices  TABLE (id INT PRIMARY KEY);   -- Invoices
DECLARE @Regs      TABLE (id INT PRIMARY KEY);   -- Horse_Registrations

INSERT INTO @Plans     SELECT plan_id FROM Training_Plans WHERE horse_id = @HorseId;
INSERT INTO @Sessions  SELECT session_id FROM training_sessions WHERE plan_id IN (SELECT id FROM @Plans);
INSERT INTO @Days      SELECT training_day_id FROM Training_Days WHERE plan_id IN (SELECT id FROM @Plans);
INSERT INTO @Protocols SELECT protocol_id FROM Treatment_Protocols WHERE horse_id = @HorseId;
INSERT INTO @Invoices  SELECT invoice_id FROM Invoices WHERE horse_id = @HorseId
                       UNION SELECT invoice_id FROM Competition_Entries WHERE horse_id = @HorseId AND invoice_id IS NOT NULL;
INSERT INTO @Regs      SELECT registration_id FROM Horse_Registrations WHERE horse_id = @HorseId;

-- Số dòng sẽ bị xóa
SELECT h.horse_id, h.horse_name, u.username AS chu_ngua,
       (SELECT COUNT(*) FROM @Regs) AS ho_so_dang_ky,
       (SELECT COUNT(*) FROM @Plans) AS giao_an,
       (SELECT COUNT(*) FROM @Sessions) AS buoi_tap,
       (SELECT COUNT(*) FROM Competition_Entries WHERE horse_id = @HorseId) AS de_xuat_thi_dau,
       (SELECT COUNT(*) FROM Vet_Cases WHERE horse_id = @HorseId) AS ca_kham,
       (SELECT COUNT(*) FROM @Protocols) AS phac_do,
       (SELECT COUNT(*) FROM Vaccinations WHERE horse_id = @HorseId) AS tiem_phong,
       (SELECT COUNT(*) FROM Nutrition_Plans WHERE horse_id = @HorseId) AS khau_phan,
       (SELECT COUNT(*) FROM Daily_Tasks WHERE horse_id = @HorseId) AS viec_cham_soc,
       (SELECT COUNT(*) FROM @Invoices) AS hoa_don
FROM Horses h JOIN Users u ON u.user_id = h.owner_id WHERE h.horse_id = @HorseId;

BEGIN TRAN;

-- ---------- 1. Việc chăm sóc, khẩu phần ----------
DELETE FROM Task_Material_Usage WHERE task_id IN (SELECT task_id FROM Daily_Tasks WHERE horse_id = @HorseId);
DELETE FROM Daily_Tasks WHERE horse_id = @HorseId;
DELETE FROM care_tasks WHERE horse_id = @HorseId;
DELETE FROM Diet_Change_Requests WHERE horse_id = @HorseId;
DELETE FROM Nutrition_Plan_Details WHERE plan_id IN (SELECT plan_id FROM Nutrition_Plans WHERE horse_id = @HorseId);
DELETE FROM Nutrition_Plans WHERE horse_id = @HorseId;

-- ---------- 2. Thi đấu (trước hóa đơn: Competition_Entries.invoice_id) ----------
DELETE FROM Competition_Entries WHERE horse_id = @HorseId;

-- ---------- 3. Hóa đơn, thanh toán của ngựa ----------
DELETE FROM Payment_Transactions WHERE invoice_id IN (SELECT id FROM @Invoices);
DELETE FROM Invoice_Line_Items WHERE invoice_id IN (SELECT id FROM @Invoices);
DELETE FROM Invoices WHERE invoice_id IN (SELECT id FROM @Invoices);

-- ---------- 4. Kho: trả lại hàng đang giữ cho phác đồ của ngựa ----------
UPDATE i SET held_stock = CASE WHEN i.held_stock - x.qty < 0 THEN 0 ELSE i.held_stock - x.qty END
FROM Inventory_Items i
JOIN (SELECT item_id, SUM(ISNULL(qty_held, 0)) AS qty FROM Stock_Holds
      WHERE protocol_id IN (SELECT id FROM @Protocols) AND status IN ('HELD', 'SHORT') GROUP BY item_id) x ON x.item_id = i.item_id;
DELETE FROM Stock_Holds WHERE protocol_id IN (SELECT id FROM @Protocols);
UPDATE Purchase_Orders SET protocol_id = NULL WHERE protocol_id IN (SELECT id FROM @Protocols);

-- ---------- 5. Y tế ----------
DELETE FROM prescriptions WHERE stage_id IN (SELECT ts.id FROM treatment_stages ts WHERE ts.protocol_id IN (SELECT id FROM @Protocols));
DELETE FROM treatment_stages WHERE protocol_id IN (SELECT id FROM @Protocols);
DELETE FROM Protocol_Progress_Notes WHERE protocol_id IN (SELECT id FROM @Protocols);
DELETE FROM Protocol_Items WHERE phase_id IN (SELECT phase_id FROM Protocol_Phases WHERE protocol_id IN (SELECT id FROM @Protocols));
DELETE FROM Protocol_Phases WHERE protocol_id IN (SELECT id FROM @Protocols);
-- Phác đồ có phiên bản (replaces_protocol_id): xóa bản mới nhất trước, lặp tới hết
WHILE EXISTS (SELECT 1 FROM Treatment_Protocols WHERE horse_id = @HorseId)
BEGIN
    DELETE p FROM Treatment_Protocols p
    WHERE p.horse_id = @HorseId AND NOT EXISTS (SELECT 1 FROM Treatment_Protocols n WHERE n.replaces_protocol_id = p.protocol_id);
END;
DELETE FROM Prescription_Items WHERE record_id IN (SELECT record_id FROM Medical_Records WHERE horse_id = @HorseId);
DELETE FROM Injury_Marks WHERE horse_id = @HorseId OR case_id IN (SELECT case_id FROM Vet_Cases WHERE horse_id = @HorseId);
DELETE FROM Medical_Records WHERE horse_id = @HorseId;
DELETE FROM Vet_Cases WHERE horse_id = @HorseId;      -- trước training_sessions (Vet_Cases.training_day_id)
DELETE FROM Vaccinations WHERE horse_id = @HorseId;

-- ---------- 6. Huấn luyện ----------
DELETE FROM Session_Readings WHERE session_id IN (SELECT id FROM @Sessions)
                                OR training_day_id IN (SELECT id FROM @Days) OR training_day_id IN (SELECT id FROM @Sessions);
DELETE FROM Session_Alerts WHERE training_day_id IN (SELECT id FROM @Days) OR training_day_id IN (SELECT id FROM @Sessions);
DELETE FROM training_sessions WHERE session_id IN (SELECT id FROM @Sessions);
DELETE FROM Training_Days WHERE training_day_id IN (SELECT id FROM @Days);
DELETE FROM Training_Plan_Phases WHERE plan_id IN (SELECT id FROM @Plans);
DELETE FROM Training_Plans WHERE plan_id IN (SELECT id FROM @Plans);

-- ---------- 7. Ngựa ----------
DELETE FROM Assignment_Requests WHERE horse_id = @HorseId;   -- yêu cầu phân công (không có khóa ngoại)
DELETE FROM Registration_Checks WHERE registration_id IN (SELECT id FROM @Regs);
DELETE FROM Registration_Attachments WHERE registration_id IN (SELECT id FROM @Regs);
DELETE FROM Horse_Registrations WHERE registration_id IN (SELECT id FROM @Regs);
DELETE FROM Horses WHERE horse_id = @HorseId;

SELECT @HorseId AS horse_id,
       CASE WHEN EXISTS (SELECT 1 FROM Horses WHERE horse_id = @HorseId) THEN N'CÒN' ELSE N'ĐÃ XÓA' END AS ket_qua,
       (SELECT COUNT(*) FROM Horses) AS so_ngua_con_lai,
       CASE WHEN @DryRun = 1 THEN N'CHẠY THỬ - đã ROLLBACK, không đổi gì' ELSE N'ĐÃ COMMIT' END AS che_do;

IF @DryRun = 1
    ROLLBACK;
ELSE
    COMMIT;
