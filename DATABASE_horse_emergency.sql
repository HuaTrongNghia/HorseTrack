-- Trạng thái cấp cứu của ngựa. Ngựa EMERGENCY được cấp thuốc và vật tư y tế
-- mà không chờ chủ ngựa thanh toán.
-- Giữ các mã cũ (Healthy / Need_Check / Injured / Isolated) và các mã ứng dụng đang ghi
-- (HEALTHY / WATCH / INJURED / QUARANTINE).
IF EXISTS (
    SELECT 1 FROM sys.check_constraints
    WHERE name = 'CHK_Horse_Health' AND parent_object_id = OBJECT_ID('dbo.Horses')
)
    ALTER TABLE dbo.Horses DROP CONSTRAINT CHK_Horse_Health;
GO
ALTER TABLE dbo.Horses WITH CHECK ADD CONSTRAINT CHK_Horse_Health CHECK (
    health_status IN (
        'Healthy', 'Need_Check', 'Injured', 'Isolated',
        'HEALTHY', 'WATCH', 'INJURED', 'QUARANTINE', 'EMERGENCY'
    )
);
GO
ALTER TABLE dbo.Horses CHECK CONSTRAINT CHK_Horse_Health;
GO
-- Bỏ ảnh hiện trường trên ca khám do nhân viên chăm sóc báo
IF COL_LENGTH('dbo.Vet_Cases', 'image_url') IS NOT NULL
    ALTER TABLE dbo.Vet_Cases DROP COLUMN image_url;
GO
