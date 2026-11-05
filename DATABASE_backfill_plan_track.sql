-- =====================================================================================
-- GÁN LOẠI ĐƯỜNG TẬP CHO GIÁO ÁN CŨ ĐANG MỞ (track_type còn NULL)
--
-- Giáo án lập trước khi có loại đường tập không được tính trong luật "mỗi loại đường chỉ một ngựa trong khoảng ngày trùng nhau".
-- Script gán lần lượt theo plan_id: chọn đường đầu tiên (Đường đất, Đường cỏ, Cỏ nhân tạo) chưa có ngựa khác trong khoảng ngày giao nhau.
-- Hết đường trống cho một giáo án thì báo lỗi và không đổi gì. Giáo án đã kết thúc / đã hủy giữ nguyên.
-- HLV không sửa được giáo án đang chạy, nên nếu cần đường khác thì đổi trực tiếp kết quả script này.
--
-- @DryRun = 1 (mặc định): chạy rồi ROLLBACK, chỉ in kết quả gán. @DryRun = 0: gán thật và COMMIT.
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;

DECLARE @DryRun BIT = 1;   -- đổi thành 0 để gán thật

BEGIN TRAN;

DECLARE @PhaseId INT, @PlanId INT, @HorseId INT, @Start DATE, @End DATE, @Track VARCHAR(20);

DECLARE phase_cursor CURSOR LOCAL FAST_FORWARD FOR
    SELECT ph.phase_id, p.plan_id, p.horse_id, p.start_date, p.end_date
    FROM Training_Plan_Phases ph JOIN Training_Plans p ON p.plan_id = ph.plan_id
    WHERE ph.track_type IS NULL AND p.status IN ('PENDING', 'ACTIVE', 'PAUSED')
    ORDER BY p.plan_id, ph.phase_id;

OPEN phase_cursor;
FETCH NEXT FROM phase_cursor INTO @PhaseId, @PlanId, @HorseId, @Start, @End;
WHILE @@FETCH_STATUS = 0
BEGIN
    SET @Track = NULL;
    SELECT TOP 1 @Track = t.code
    FROM (VALUES (1, 'DIRT'), (2, 'TURF'), (3, 'SYNTHETIC')) t(ord, code)
    WHERE NOT EXISTS (
        SELECT 1 FROM Training_Plan_Phases oph JOIN Training_Plans o ON o.plan_id = oph.plan_id
        WHERE oph.track_type = t.code AND o.status IN ('PENDING', 'ACTIVE', 'PAUSED')
          AND o.horse_id <> @HorseId AND o.start_date <= @End AND o.end_date >= @Start)
    ORDER BY t.ord;

    IF @Track IS NULL
        THROW 50001, N'Không còn loại đường trống cho một giáo án trong khoảng ngày của nó. Không đổi gì; hãy kết thúc hoặc hủy bớt giáo án.', 1;

    UPDATE Training_Plan_Phases SET track_type = @Track WHERE phase_id = @PhaseId;
    FETCH NEXT FROM phase_cursor INTO @PhaseId, @PlanId, @HorseId, @Start, @End;
END;
CLOSE phase_cursor;
DEALLOCATE phase_cursor;

-- Kết quả trong transaction
SELECT p.plan_id, h.horse_name, p.status, p.start_date, p.end_date, ph.track_type
FROM Training_Plans p JOIN Horses h ON h.horse_id = p.horse_id LEFT JOIN Training_Plan_Phases ph ON ph.plan_id = p.plan_id
WHERE p.status IN ('PENDING', 'ACTIVE', 'PAUSED') ORDER BY p.plan_id;

IF @DryRun = 1
BEGIN
    ROLLBACK;
    PRINT N'DRY RUN: đã ROLLBACK, không đổi gì. Đổi @DryRun = 0 để gán thật.';
END
ELSE
BEGIN
    COMMIT;
    PRINT N'Đã gán loại đường cho các giáo án cũ đang mở.';
END;
