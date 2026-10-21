-- BR_16: tới ngày bắt đầu một giai đoạn điều trị mà phí của giai đoạn đó chưa được thanh toán thì phác đồ bị tạm dừng.
-- Script cho phép trạng thái ON_HOLD_PAYMENT trong Treatment_Protocols. Không đổi dữ liệu hiện có.
SET NOCOUNT ON;
BEGIN TRAN;

ALTER TABLE Treatment_Protocols DROP CONSTRAINT CHK_TPR_state;
ALTER TABLE Treatment_Protocols ADD CONSTRAINT CHK_TPR_state CHECK ([state] IN (
    'DRAFT', 'WAITING_STOCK_CHECK', 'STOCK_AVAILABLE', 'STOCK_SHORTAGE', 'WAITING_RESTOCK', 'REQUIRES_VET_REVIEW',
    'VET_CONFIRMED', 'READY_FOR_DISPENSING', 'IN_TREATMENT', 'ON_HOLD_PAYMENT', 'COMPLETED', 'SUPERSEDED', 'CANCELLED'));

COMMIT;
SELECT name, definition FROM sys.check_constraints WHERE name = 'CHK_TPR_state';
