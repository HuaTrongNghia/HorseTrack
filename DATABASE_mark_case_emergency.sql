-- =====================================================================================
-- ĐÁNH DẤU CẤP CỨU CHO PHÁC ĐỒ ĐÃ LẬP TRƯỚC BẢN SỬA (mặc định PD-1057 / ca C-2039)
-- Trước bản sửa, ô "Trạng thái cấp cứu" trên FE không được backend ghi nhận. Script chuyển ca khám của phác đồ
-- sang severity = CRITICAL (BR_17). Job hằng ngày 00:20 (ProtocolPhaseBilling.runDaily) sẽ chuyển phác đồ đang
-- "Chờ thanh toán" sang "Đang điều trị". Muốn chuyển ngay: bác sĩ đặt tình trạng ngựa "Cấp cứu" (EMERGENCY).
-- =====================================================================================
SET XACT_ABORT ON;
DECLARE @ProtocolId INT = 1057;

BEGIN TRAN;
SELECT p.protocol_code, p.state, c.case_code, c.severity AS severity_cu
FROM Treatment_Protocols p JOIN Vet_Cases c ON c.case_id = p.case_id WHERE p.protocol_id = @ProtocolId;

UPDATE c SET severity = 'CRITICAL'
FROM Vet_Cases c JOIN Treatment_Protocols p ON p.case_id = c.case_id
WHERE p.protocol_id = @ProtocolId AND c.severity <> 'CRITICAL';
PRINT CONCAT(N'Đã cập nhật ', @@ROWCOUNT, N' ca khám (mong đợi 1).');

INSERT INTO Audit_Logs (user_id, action_type, target_table, old_value, new_value, timestamp, is_login)
SELECT NULL, 'MARK_EMERGENCY', 'Vet_Cases', NULL,
       CONCAT('severity=CRITICAL | ca khám #', p.case_id, ' | phác đồ ', p.protocol_code, ' | đánh dấu cấp cứu bằng script'), SYSDATETIME(), 0
FROM Treatment_Protocols p WHERE p.protocol_id = @ProtocolId;

COMMIT;
-- ROLLBACK;   -- dùng thay COMMIT nếu muốn chạy thử
