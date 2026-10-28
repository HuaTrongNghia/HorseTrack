-- Xóa 4 dòng Email_Logs do rollback test tạo ra (luồng gửi email async ghi log ở transaction riêng nên không bị rollback).
-- Chỉ xóa đúng log_id + purpose + status SKIPPED để không đụng log thật.
SET XACT_ABORT ON;
BEGIN TRANSACTION;

SELECT log_id, to_email, purpose, status, subject, sent_at
FROM Email_Logs
WHERE log_id IN (201, 206, 207, 208)
  AND status = 'SKIPPED'
  AND purpose IN ('CARE_TASK_DONE', 'CARE_TASK_OVERDUE');

DELETE FROM Email_Logs
WHERE log_id IN (201, 206, 207, 208)
  AND status = 'SKIPPED'
  AND purpose IN ('CARE_TASK_DONE', 'CARE_TASK_OVERDUE');

PRINT CONCAT('Đã xóa ', @@ROWCOUNT, ' dòng (mong đợi 4).');

-- Kiểm tra số dòng đã xóa rồi chọn một trong hai lệnh:
COMMIT TRANSACTION;
-- ROLLBACK TRANSACTION;
