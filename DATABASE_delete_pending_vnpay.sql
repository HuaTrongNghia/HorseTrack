-- =====================================================================================
-- XÓA GIAO DỊCH VNPAY ĐANG CHỜ XÁC MINH (mặc định #35 / 2057-20261029015751, #36 / 2058-20261029023722)
-- Chỉ xóa giao dịch PENDING_VERIFICATION; hóa đơn giữ nguyên (vẫn chưa thanh toán) để tạo giao dịch mới.
-- Giao dịch đã VERIFIED / FAILED không bị đụng tới.
-- @DryRun = 1 (mặc định): chạy rồi ROLLBACK. Đổi thành 0 để xóa thật. KHÔNG HOÀN TÁC ĐƯỢC SAU KHI COMMIT.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @DryRun BIT = 0;   -- đổi thành 0 để xóa thật
DECLARE @Refs TABLE (transaction_ref VARCHAR(100) PRIMARY KEY);
INSERT INTO @Refs VALUES ('2057-20261029015751'), ('2058-20261029023722');

SELECT t.transaction_id, t.transaction_ref, t.payment_status, t.amount_paid, i.invoice_code, i.payment_status AS invoice_status
FROM Payment_Transactions t JOIN @Refs r ON r.transaction_ref = t.transaction_ref JOIN Invoices i ON i.invoice_id = t.invoice_id;

BEGIN TRAN;
DELETE t FROM Payment_Transactions t JOIN @Refs r ON r.transaction_ref = t.transaction_ref
WHERE t.payment_status = 'PENDING_VERIFICATION';
PRINT CONCAT(N'Đã xóa ', @@ROWCOUNT, N' giao dịch (mong đợi 2).');

IF @DryRun = 1
BEGIN
    ROLLBACK;
    PRINT N'DRY RUN: đã ROLLBACK, không có gì bị xóa. Đổi @DryRun = 0 để xóa thật.';
END
ELSE
BEGIN
    COMMIT;
    PRINT N'Đã xóa giao dịch VNPay đang chờ. Hóa đơn vẫn chưa thanh toán, có thể tạo giao dịch mới.';
END;
