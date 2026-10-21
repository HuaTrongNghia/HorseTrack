-- Gán HLV phụ trách (Horses.assigned_trainer_id) cho những ngựa đang có giáo án ACTIVE/PAUSED
-- nhưng chưa được Club Manager phân công, lấy theo HLV lập giáo án (Training_Plans.head_trainer_id).
-- Chạy một lần sau khi cập nhật code "Trainer chỉ xem ngựa được phân công", để HLV không mất quyền
-- với giáo án đang làm. Ngựa đã có HLV thì giữ nguyên.
SET NOCOUNT ON;
BEGIN TRAN;
UPDATE h
SET h.assigned_trainer_id = p.head_trainer_id
FROM Horses h
CROSS APPLY (SELECT TOP 1 tp.head_trainer_id FROM Training_Plans tp
             WHERE tp.horse_id = h.horse_id AND tp.status IN ('ACTIVE', 'PAUSED') AND tp.head_trainer_id IS NOT NULL
             ORDER BY tp.created_at DESC) p
WHERE h.assigned_trainer_id IS NULL;
SELECT @@ROWCOUNT AS horses_updated;
COMMIT;
SELECT h.horse_id, h.horse_name, h.assigned_trainer_id, u.username AS trainer FROM Horses h LEFT JOIN Users u ON u.user_id = h.assigned_trainer_id;
