-- Tạo 15 chuồng ngựa: 5 chuồng mỗi khu (Khu A, Khu B, Khu C), trạng thái CLEAN.
-- Bỏ qua chuồng đã tồn tại mã, nên chạy lại nhiều lần không bị trùng.
SET NOCOUNT ON;
BEGIN TRAN;
INSERT INTO Stalls (stall_code, barn_zone, clean_status, updated_at)
SELECT v.code, v.zone, 'CLEAN', GETDATE()
FROM (VALUES
  (N'A-01', N'Khu A'), (N'A-02', N'Khu A'), (N'A-03', N'Khu A'), (N'A-04', N'Khu A'), (N'A-05', N'Khu A'),
  (N'B-01', N'Khu B'), (N'B-02', N'Khu B'), (N'B-03', N'Khu B'), (N'B-04', N'Khu B'), (N'B-05', N'Khu B'),
  (N'C-01', N'Khu C'), (N'C-02', N'Khu C'), (N'C-03', N'Khu C'), (N'C-04', N'Khu C'), (N'C-05', N'Khu C')
) AS v(code, zone)
WHERE NOT EXISTS (SELECT 1 FROM Stalls s WHERE s.stall_code = v.code);
SELECT @@ROWCOUNT AS inserted;
COMMIT;
SELECT stall_id, stall_code, barn_zone, clean_status, monthly_base_fee FROM Stalls ORDER BY stall_code;
