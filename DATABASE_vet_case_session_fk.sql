-- HLV dừng buổi tập và gửi bác sĩ: Vet_Cases.training_day_id lưu session_id (Training_Sessions).
-- FK_VC_session trước đây trỏ Training_Days. Bảng đó không còn dòng nào, nên INSERT ca khám bị từ chối.
SET NOCOUNT ON;
SET XACT_ABORT ON;

SELECT fk.name, OBJECT_NAME(fk.referenced_object_id) AS referenced_table
FROM sys.foreign_keys fk
WHERE fk.name = 'FK_VC_session' AND fk.parent_object_id = OBJECT_ID('dbo.Vet_Cases');

BEGIN TRAN;

IF EXISTS (
    SELECT 1 FROM sys.foreign_keys
    WHERE name = 'FK_VC_session' AND parent_object_id = OBJECT_ID('dbo.Vet_Cases')
)
    ALTER TABLE dbo.Vet_Cases DROP CONSTRAINT FK_VC_session;

ALTER TABLE dbo.Vet_Cases WITH CHECK ADD CONSTRAINT FK_VC_session
    FOREIGN KEY (training_day_id) REFERENCES dbo.Training_Sessions (session_id);

COMMIT;

SELECT fk.name, OBJECT_NAME(fk.parent_object_id) AS child_table, pc.name AS child_col,
       OBJECT_NAME(fk.referenced_object_id) AS referenced_table, rc.name AS referenced_col
FROM sys.foreign_keys fk
JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
JOIN sys.columns pc ON pc.object_id = fkc.parent_object_id AND pc.column_id = fkc.parent_column_id
JOIN sys.columns rc ON rc.object_id = fkc.referenced_object_id AND rc.column_id = fkc.referenced_column_id
WHERE fk.name = 'FK_VC_session';
