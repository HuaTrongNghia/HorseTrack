-- =====================================================================================
-- HỦY GIÁO ÁN 3053 (ngựa Chiều Dạ) ĐỂ TRỐNG MỘT LOẠI ĐƯỜNG TẬP, RỒI MỚI CHẠY DATABASE_backfill_plan_track.sql
--
-- Luồng hủy của ứng dụng không cho hủy giáo án ACTIVE có buổi tập đang chạy, nên script này cập nhật trực tiếp.
-- Buổi 8075 (IN_PROGRESS từ 02/11) chưa có chỉ số, ca khám hay cảnh báo. Trạng thái khác đi thì script dừng, không đổi gì.
-- Không gửi thông báo cho HLV và nhân viên chuồng. Có ghi Audit_Logs.
--
-- @DryRun = 1 (mặc định): chạy rồi ROLLBACK. @DryRun = 0: hủy thật và COMMIT.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @DryRun BIT = 1;   -- đổi thành 0 để hủy thật
DECLARE @Why NVARCHAR(200) = N'Giải phóng đường tập khi gán loại đường cho giáo án cũ';

BEGIN TRAN;

IF NOT EXISTS (SELECT 1 FROM Training_Plans WHERE plan_id = 3053 AND status = 'ACTIVE')
   OR EXISTS (SELECT 1 FROM training_sessions WHERE plan_id = 3053 AND session_id <> 8075)
   OR NOT EXISTS (SELECT 1 FROM training_sessions WHERE session_id = 8075 AND status = 'IN_PROGRESS')
   OR EXISTS (SELECT 1 FROM Session_Readings WHERE session_id = 8075)
   OR EXISTS (SELECT 1 FROM Vet_Cases WHERE training_day_id = 8075)
    THROW 50002, N'Trạng thái giáo án 3053 đã thay đổi, không hủy.', 1;

UPDATE training_sessions
SET status = 'CANCELLED',
    notes = LEFT(CASE WHEN notes IS NULL OR notes = '' THEN N'' ELSE notes + N' | ' END + N'Hủy do giáo án bị hủy: ' + @Why, 4000)
WHERE session_id = 8075;

UPDATE Training_Plans SET status = 'CANCELLED', paused_reason = NULL WHERE plan_id = 3053;

-- Audit_Logs lưu giờ Việt Nam, GETDATE() của DB là UTC
INSERT INTO Audit_Logs (user_id, action_type, target_table, old_value, new_value, ip_address, [timestamp])
VALUES (NULL, 'CANCEL_TRAINING_PLAN', 'Training_Plans', 'status=ACTIVE | buổi 8075 IN_PROGRESS',
        N'status=CANCELLED | giáo án #3053 | ngựa Chiều Dạ | hủy 1 buổi tập | ' + @Why, 'db-script', DATEADD(hour, 7, GETDATE()));

SELECT p.plan_id, p.status AS plan_status, s.session_id, s.status AS session_status
FROM Training_Plans p JOIN training_sessions s ON s.plan_id = p.plan_id WHERE p.plan_id = 3053;

IF @DryRun = 1
BEGIN
    ROLLBACK;
    PRINT N'DRY RUN: đã ROLLBACK, không đổi gì. Đổi @DryRun = 0 để hủy thật.';
END
ELSE
BEGIN
    COMMIT;
    PRINT N'Đã hủy giáo án 3053. Chạy tiếp DATABASE_backfill_plan_track.sql.';
END;
