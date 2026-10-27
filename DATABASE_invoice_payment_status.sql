-- Hủy phác đồ ghi Invoices.payment_status = CANCELLED (chưa thanh toán) hoặc REFUNDED (đã thanh toán).
-- CK_Inv_payment_status hiện từ chối UPDATE đó. Script bổ sung đúng hai trạng thái trong Invoice.java.
SET NOCOUNT ON;
SET XACT_ABORT ON;

SELECT name, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.Invoices')
  AND name = 'CK_Inv_payment_status';

BEGIN TRAN;

IF EXISTS (SELECT 1 FROM sys.check_constraints WHERE name = 'CK_Inv_payment_status' AND parent_object_id = OBJECT_ID('dbo.Invoices'))
    ALTER TABLE dbo.Invoices DROP CONSTRAINT CK_Inv_payment_status;

ALTER TABLE dbo.Invoices ADD CONSTRAINT CK_Inv_payment_status
    CHECK ([payment_status] IN ('UNPAID', 'OVERDUE', 'PARTIALLY_PAID', 'PAID', 'CANCELLED', 'REFUNDED'));

COMMIT;

SELECT name, definition
FROM sys.check_constraints
WHERE parent_object_id = OBJECT_ID('dbo.Invoices')
  AND name = 'CK_Inv_payment_status';
