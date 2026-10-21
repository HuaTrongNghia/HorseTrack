-- Vòng đời giáo án mới: HLV lập giáo án -> PENDING (đang chờ); chỉ chuyển ACTIVE (đang chạy) khi đã có buổi tập
-- được Groom xác nhận và đã tới ngày bắt đầu. Script cho phép trạng thái PENDING trong Training_Plans.
-- Buổi tập (training_sessions.status) dùng thêm giá trị CONFIRMED, bảng này không có CHECK nên không cần sửa.
SET NOCOUNT ON;
BEGIN TRAN;

-- 1. Cho phép trạng thái PENDING
ALTER TABLE Training_Plans DROP CONSTRAINT CHK_Plan_Status;
ALTER TABLE Training_Plans ADD CONSTRAINT CHK_Plan_Status
    CHECK ([status] IN ('PENDING', 'ACTIVE', 'PAUSED', 'COMPLETED', 'CANCELLED'));

-- 2. Giá trị mặc định của status: ACTIVE -> PENDING
DECLARE @df sysname = (SELECT dc.name FROM sys.default_constraints dc
                       JOIN sys.columns c ON c.object_id = dc.parent_object_id AND c.column_id = dc.parent_column_id
                       WHERE dc.parent_object_id = OBJECT_ID('Training_Plans') AND c.name = 'status');
IF @df IS NOT NULL EXEC('ALTER TABLE Training_Plans DROP CONSTRAINT ' + @df);
ALTER TABLE Training_Plans ADD CONSTRAINT DF_Training_Plans_status DEFAULT ('PENDING') FOR [status];

-- 3. Dữ liệu cũ: giáo án ACTIVE chưa có buổi tập nào được xác nhận / hoàn thành thì về PENDING
UPDATE tp SET tp.[status] = 'PENDING'
FROM Training_Plans tp
WHERE tp.[status] = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM training_sessions s WHERE s.plan_id = tp.plan_id AND s.[status] IN ('CONFIRMED', 'COMPLETED'));
SELECT @@ROWCOUNT AS plans_moved_to_pending;

COMMIT;
SELECT plan_id, horse_id, [status], start_date, end_date FROM Training_Plans;
