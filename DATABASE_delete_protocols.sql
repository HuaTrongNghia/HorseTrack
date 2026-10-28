-- =====================================================================================
-- XÓA PHÁC ĐỒ ĐIỀU TRỊ (mặc định PD-1054, PD-1055) ĐỂ LẬP LẠI VÀ THANH TOÁN LẠI
--
-- Xóa : phác đồ + giai đoạn, thuốc / vật tư, ghi chú tiến triển, dòng giữ hàng;
--       hóa đơn giai đoạn của phác đồ (GD-00xxxx-..) + dòng hóa đơn + giao dịch thanh toán VNPay đang chờ.
-- Trả kho (@ReturnStock = 1): vật tư đã cấp cho phác đồ được nhập lại đúng lô, ghi giao dịch kho RETURN.
-- Giữ : ca khám (Vet_Cases, vẫn MONITORING để bác sĩ lập lại phác đồ), ngựa, Audit_Logs, lịch sử kho cũ.
-- Dừng (không xóa gì) nếu có hóa đơn đã thanh toán hoặc phác đồ đã có bản thay thế.
--
-- @DryRun = 1 (mặc định): chạy toàn bộ rồi ROLLBACK, chỉ in kết quả. Đổi thành 0 để xóa thật.
-- CẢNH BÁO: KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @DryRun BIT = 1;          -- đổi thành 0 để xóa thật
DECLARE @ReturnStock BIT = 1;     -- 1: nhập lại kho vật tư đã cấp cho phác đồ
DECLARE @Ids TABLE (protocol_id INT PRIMARY KEY);
INSERT INTO @Ids VALUES (1054), (1055);   -- protocol_id cần xóa

-- Hóa đơn giai đoạn của phác đồ: mã GD-<protocol_id 6 số>-<giai đoạn>
DECLARE @Inv TABLE (invoice_id INT PRIMARY KEY);
INSERT INTO @Inv
SELECT i.invoice_id FROM Invoices i JOIN @Ids p ON i.invoice_code LIKE 'GD-' + RIGHT('000000' + CAST(p.protocol_id AS VARCHAR(10)), 6) + '-%';

-- Xem trước
SELECT p.protocol_id, p.protocol_code, p.state, h.horse_name, p.case_id FROM Treatment_Protocols p JOIN @Ids x ON x.protocol_id = p.protocol_id LEFT JOIN Horses h ON h.horse_id = p.horse_id;
SELECT i.invoice_id, i.invoice_code, i.payment_status, i.final_payable_amount,
       (SELECT COUNT(*) FROM Payment_Transactions t WHERE t.invoice_id = i.invoice_id) AS giao_dich
FROM Invoices i JOIN @Inv v ON v.invoice_id = i.invoice_id;

IF EXISTS (SELECT 1 FROM Invoices i JOIN @Inv v ON v.invoice_id = i.invoice_id WHERE i.payment_status = 'PAID')
   OR EXISTS (SELECT 1 FROM Payment_Transactions t JOIN @Inv v ON v.invoice_id = t.invoice_id WHERE t.payment_status = 'VERIFIED')
BEGIN
    RAISERROR(N'Có hóa đơn đã thanh toán, không xóa. Kiểm tra lại danh sách phác đồ.', 16, 1);
    RETURN;
END;
IF EXISTS (SELECT 1 FROM Treatment_Protocols n JOIN @Ids x ON n.replaces_protocol_id = x.protocol_id)
BEGIN
    RAISERROR(N'Có phác đồ đã được thay thế bằng phiên bản mới, không xóa.', 16, 1);
    RETURN;
END;

BEGIN TRAN;

-- ---------- 1. Trả kho vật tư đã cấp cho phác đồ ----------
IF @ReturnStock = 1
BEGIN
    DECLARE @tx INT, @item INT, @lot INT, @qty DECIMAL(18, 2), @by INT, @ref INT, @bal DECIMAL(18, 2);
    DECLARE c CURSOR LOCAL FAST_FORWARD FOR
        SELECT s.transaction_id, s.item_id, s.lot_id, s.quantity, s.performed_by, s.ref_id
        FROM Stock_Transactions s JOIN @Ids x ON s.ref_type = 'PROTOCOL' AND s.ref_id = x.protocol_id
        WHERE s.tx_type = 'ISSUE';
    OPEN c;
    FETCH NEXT FROM c INTO @tx, @item, @lot, @qty, @by, @ref;
    WHILE @@FETCH_STATUS = 0
    BEGIN
        IF @lot IS NOT NULL UPDATE Inventory_Lots SET quantity = quantity + @qty WHERE lot_id = @lot;
        UPDATE Inventory_Items SET current_stock = current_stock + @qty WHERE item_id = @item;
        SELECT @bal = current_stock FROM Inventory_Items WHERE item_id = @item;
        INSERT INTO Stock_Transactions (tx_type, item_id, lot_id, quantity, balance_after, ref_type, ref_id, performed_by, note)
        VALUES ('RETURN', @item, @lot, @qty, @bal, 'PROTOCOL', @ref, @by,
                N'Nhập lại kho: xóa phác đồ PD-' + CAST(@ref AS NVARCHAR(10)) + N' (hoàn TX-' + RIGHT('0000' + CAST(@tx AS VARCHAR(10)), 4) + N')');
        FETCH NEXT FROM c INTO @tx, @item, @lot, @qty, @by, @ref;
    END;
    CLOSE c;
    DEALLOCATE c;
END;

-- Hàng còn đang giữ (chưa cấp) cho phác đồ: trả lại số lượng giữ
UPDATE it SET held_stock = CASE WHEN it.held_stock - h.qty < 0 THEN 0 ELSE it.held_stock - h.qty END
FROM Inventory_Items it
JOIN (SELECT sh.item_id, SUM(sh.qty_held) AS qty FROM Stock_Holds sh JOIN @Ids x ON x.protocol_id = sh.protocol_id
      WHERE sh.status NOT IN ('ISSUED', 'RELEASED') GROUP BY sh.item_id) h ON h.item_id = it.item_id;

-- ---------- 2. Hóa đơn giai đoạn và giao dịch thanh toán ----------
DELETE t FROM Payment_Transactions t JOIN @Inv v ON v.invoice_id = t.invoice_id;
DELETE l FROM Invoice_Line_Items l JOIN @Inv v ON v.invoice_id = l.invoice_id;
DELETE i FROM Invoices i JOIN @Inv v ON v.invoice_id = i.invoice_id;

-- ---------- 3. Phác đồ ----------
UPDATE po SET protocol_id = NULL FROM Purchase_Orders po JOIN @Ids x ON x.protocol_id = po.protocol_id;
DELETE s FROM Stock_Holds s JOIN @Ids x ON x.protocol_id = s.protocol_id;
DELETE pr FROM prescriptions pr JOIN treatment_stages ts ON ts.id = pr.stage_id JOIN @Ids x ON x.protocol_id = ts.protocol_id;
DELETE ts FROM treatment_stages ts JOIN @Ids x ON x.protocol_id = ts.protocol_id;
DELETE n FROM Protocol_Progress_Notes n JOIN @Ids x ON x.protocol_id = n.protocol_id;
DELETE i FROM Protocol_Items i JOIN Protocol_Phases ph ON ph.phase_id = i.phase_id JOIN @Ids x ON x.protocol_id = ph.protocol_id;
DELETE ph FROM Protocol_Phases ph JOIN @Ids x ON x.protocol_id = ph.protocol_id;
DELETE p FROM Treatment_Protocols p JOIN @Ids x ON x.protocol_id = p.protocol_id;

-- Kiểm tra trong transaction
SELECT (SELECT COUNT(*) FROM Treatment_Protocols p JOIN @Ids x ON x.protocol_id = p.protocol_id) AS phac_do_con_lai,
       (SELECT COUNT(*) FROM Invoices i JOIN @Inv v ON v.invoice_id = i.invoice_id) AS hoa_don_con_lai;
SELECT s.item_id, i.item_name, i.current_stock, s.tx_type, s.quantity, s.note
FROM Stock_Transactions s JOIN Inventory_Items i ON i.item_id = s.item_id
WHERE s.tx_type = 'RETURN' AND s.ref_type = 'PROTOCOL' AND s.ref_id IN (SELECT protocol_id FROM @Ids);

IF @DryRun = 1
BEGIN
    ROLLBACK;
    PRINT N'DRY RUN: đã ROLLBACK, không có gì bị xóa. Đổi @DryRun = 0 để xóa thật.';
END
ELSE
BEGIN
    COMMIT;
    PRINT N'Đã xóa phác đồ, hóa đơn giai đoạn và giao dịch thanh toán liên quan.';
END;
