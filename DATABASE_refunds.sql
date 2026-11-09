-- =====================================================================================
-- LUỒNG HOÀN TIỀN: CHỜ HOÀN → ĐÃ HOÀN
--
-- 1. Thêm trạng thái hóa đơn REFUND_PENDING (đã thanh toán, đang chờ CLB chuyển tiền hoàn).
-- 2. Tạo bảng Invoice_Refunds: mỗi hóa đơn tối đa một khoản hoàn; khi xác nhận "đã hoàn" bắt buộc có mã giao dịch,
--    ngày chuyển và người chuyển.
-- Chạy TRƯỚC khi deploy code BE có RefundService, nếu không mọi thao tác hoàn tiền sẽ lỗi.
-- Chạy lại nhiều lần được (bỏ qua phần đã có). Một transaction: lỗi thì không đổi gì.
--
-- Hóa đơn REFUNDED có từ trước (đánh dấu hoàn ngay, chưa có thông tin chuyển tiền) giữ nguyên, không tạo khoản hoàn.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;
SET ANSI_NULLS ON;

BEGIN TRAN;

IF OBJECT_ID('dbo.Invoice_Refunds', 'U') IS NULL
BEGIN
    CREATE TABLE dbo.Invoice_Refunds (
        refund_id       INT IDENTITY(1,1) NOT NULL CONSTRAINT PK_Invoice_Refunds PRIMARY KEY,
        invoice_id      INT            NOT NULL CONSTRAINT FK_Refund_Invoice REFERENCES dbo.Invoices(invoice_id),
        amount          DECIMAL(12,2)  NOT NULL CONSTRAINT CHK_Refund_Amount CHECK (amount >= 0),
        reason          NVARCHAR(500)  NOT NULL,
        status          VARCHAR(20)    NOT NULL CONSTRAINT CHK_Refund_Status CHECK (status IN ('PENDING', 'DONE')),
        requested_at    DATETIME2      NOT NULL,
        transaction_ref NVARCHAR(100)  NULL,
        transferred_on  DATE           NULL,
        transferred_by  NVARCHAR(150)  NULL,
        confirmed_by    INT            NULL CONSTRAINT FK_Refund_ConfirmedBy REFERENCES dbo.Users(user_id),
        confirmed_at    DATETIME2      NULL,
        note            NVARCHAR(500)  NULL,
        CONSTRAINT CHK_Refund_Done CHECK (status = 'PENDING'
            OR (transaction_ref IS NOT NULL AND transferred_on IS NOT NULL AND transferred_by IS NOT NULL AND confirmed_at IS NOT NULL))
    );
    EXEC('CREATE UNIQUE INDEX UX_Refund_Invoice ON dbo.Invoice_Refunds(invoice_id)');
    EXEC('CREATE INDEX IX_Refund_Status ON dbo.Invoice_Refunds(status, requested_at)');
END;

-- Trạng thái hóa đơn mới REFUND_PENDING
IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CHK_Invoice_Status'
           AND definition NOT LIKE '%REFUND_PENDING%')
BEGIN
    ALTER TABLE dbo.Invoices DROP CONSTRAINT CHK_Invoice_Status;
    ALTER TABLE dbo.Invoices ADD CONSTRAINT CHK_Invoice_Status CHECK (payment_status IN
        ('DRAFT', 'UNPAID', 'OVERDUE', 'PARTIALLY_PAID', 'PAID', 'CANCELLED', 'REFUNDED', 'REFUND_PENDING'));
END;

COMMIT;

SELECT (SELECT COUNT(*) FROM sys.tables WHERE name = 'Invoice_Refunds') AS bang_invoice_refunds,
       (SELECT definition FROM sys.check_constraints WHERE name = 'CHK_Invoice_Status') AS check_trang_thai_hoa_don;
