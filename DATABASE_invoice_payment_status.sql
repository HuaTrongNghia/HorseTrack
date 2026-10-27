-- Hủy phác đồ ghi Invoices.payment_status = CANCELLED (chưa thanh toán) hoặc REFUNDED (đã thanh toán).
-- Ràng buộc thật trên DB là CHK_Invoice_Status và chưa có REFUNDED, nên UPDATE bị từ chối.
-- Giữ DRAFT vì ràng buộc cũ đang cho phép giá trị đó.
SET NOCOUNT ON;
SET XACT_ABORT ON;

SELECT name, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.Invoices')
  AND name IN ('CHK_Invoice_Status', 'CK_Inv_payment_status', 'CK_Invoice_PaymentStatus');

BEGIN TRAN;

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Inv_payment_status' AND parent_object_id = OBJECT_ID('dbo.Invoices'))
    ALTER TABLE dbo.Invoices DROP CONSTRAINT CK_Inv_payment_status;

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Invoice_PaymentStatus' AND parent_object_id = OBJECT_ID('dbo.Invoices'))
    ALTER TABLE dbo.Invoices DROP CONSTRAINT CK_Invoice_PaymentStatus;

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CHK_Invoice_Status' AND parent_object_id = OBJECT_ID('dbo.Invoices'))
    ALTER TABLE dbo.Invoices DROP CONSTRAINT CHK_Invoice_Status;

ALTER TABLE dbo.Invoices ADD CONSTRAINT CHK_Invoice_Status
    CHECK ([payment_status] IN ('UNPAID', 'OVERDUE', 'PARTIALLY_PAID', 'PAID', 'CANCELLED', 'REFUNDED', 'DRAFT'));

COMMIT;

SELECT name, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.Invoices')
  AND name = 'CHK_Invoice_Status';
