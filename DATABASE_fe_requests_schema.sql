-- =====================================================================================
-- Schema cho các yêu cầu của FE (NotesAPI mục 23) - chỉ THÊM cột / NỚI ràng buộc, không xóa dữ liệu.
-- Chạy lại nhiều lần vẫn an toàn. Phải chạy TRƯỚC khi deploy bản backend mới (entity đã map các cột này).
--  1. training_sessions : cột status dài 30, ngưỡng theo phiên, thời điểm bắt đầu / tạm dừng, lý do tạm dừng (F22-02)
--  2. Session_Readings  : lưu chỉ số / nhịp hồi phục theo phiên tập (session_id), training_day_id thành tùy chọn
--  3. Injury_Marks      : chấn thương gắn trực tiếp với ngựa (horse_id, case_id), record_id thành tùy chọn
--  4. Users             : ảnh đại diện (avatar_url)
--  5. Competition_Entries : ghi chú của HLV khi đề xuất thi đấu (proposal_note)
-- =====================================================================================
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRAN;

-- ---------- 1. training_sessions ----------
-- PAUSED_PENDING_TRAINER dài 22 ký tự, cột cũ chỉ varchar(20)
IF (SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_NAME = 'training_sessions' AND COLUMN_NAME = 'status') < 30
    ALTER TABLE training_sessions ALTER COLUMN status VARCHAR(30) NOT NULL;
IF COL_LENGTH('training_sessions', 'max_heart_rate') IS NULL ALTER TABLE training_sessions ADD max_heart_rate INT NULL;
IF COL_LENGTH('training_sessions', 'max_speed_kmh') IS NULL ALTER TABLE training_sessions ADD max_speed_kmh DECIMAL(5, 2) NULL;
IF COL_LENGTH('training_sessions', 'started_at') IS NULL ALTER TABLE training_sessions ADD started_at DATETIME2 NULL;
IF COL_LENGTH('training_sessions', 'paused_at') IS NULL ALTER TABLE training_sessions ADD paused_at DATETIME2 NULL;
IF COL_LENGTH('training_sessions', 'paused_reason') IS NULL ALTER TABLE training_sessions ADD paused_reason NVARCHAR(500) NULL;

-- ---------- 2. Session_Readings ----------
ALTER TABLE Session_Readings ALTER COLUMN training_day_id INT NULL;
IF COL_LENGTH('Session_Readings', 'session_id') IS NULL ALTER TABLE Session_Readings ADD session_id INT NULL;
IF OBJECT_ID('FK_SR_session', 'F') IS NULL
    EXEC('ALTER TABLE Session_Readings ADD CONSTRAINT FK_SR_session FOREIGN KEY (session_id) REFERENCES training_sessions(session_id) ON DELETE CASCADE');
IF OBJECT_ID('CK_SR_owner', 'C') IS NULL
    EXEC('ALTER TABLE Session_Readings ADD CONSTRAINT CK_SR_owner CHECK (training_day_id IS NOT NULL OR session_id IS NOT NULL)');

-- ---------- 3. Injury_Marks ----------
ALTER TABLE Injury_Marks ALTER COLUMN record_id INT NULL;
IF COL_LENGTH('Injury_Marks', 'horse_id') IS NULL ALTER TABLE Injury_Marks ADD horse_id INT NULL;
IF COL_LENGTH('Injury_Marks', 'case_id') IS NULL ALTER TABLE Injury_Marks ADD case_id INT NULL;
IF COL_LENGTH('Injury_Marks', 'created_by') IS NULL ALTER TABLE Injury_Marks ADD created_by INT NULL;
IF COL_LENGTH('Injury_Marks', 'created_at') IS NULL ALTER TABLE Injury_Marks ADD created_at DATETIME2 NULL;
IF OBJECT_ID('FK_IM_horse', 'F') IS NULL
    EXEC('ALTER TABLE Injury_Marks ADD CONSTRAINT FK_IM_horse FOREIGN KEY (horse_id) REFERENCES Horses(horse_id)');
IF OBJECT_ID('FK_IM_case', 'F') IS NULL
    EXEC('ALTER TABLE Injury_Marks ADD CONSTRAINT FK_IM_case FOREIGN KEY (case_id) REFERENCES Vet_Cases(case_id)');
IF OBJECT_ID('FK_IM_created_by', 'F') IS NULL
    EXEC('ALTER TABLE Injury_Marks ADD CONSTRAINT FK_IM_created_by FOREIGN KEY (created_by) REFERENCES Users(user_id)');
-- Chấn thương cũ gắn bệnh án: điền horse_id theo bệnh án
EXEC('UPDATE im SET im.horse_id = mr.horse_id FROM Injury_Marks im JOIN Medical_Records mr ON mr.record_id = im.record_id WHERE im.horse_id IS NULL');
IF OBJECT_ID('CK_IM_owner', 'C') IS NULL
    EXEC('ALTER TABLE Injury_Marks ADD CONSTRAINT CK_IM_owner CHECK (record_id IS NOT NULL OR horse_id IS NOT NULL)');

-- ---------- 4. Users ----------
IF COL_LENGTH('Users', 'avatar_url') IS NULL ALTER TABLE Users ADD avatar_url NVARCHAR(MAX) NULL;

-- ---------- 5. Competition_Entries ----------
IF COL_LENGTH('Competition_Entries', 'proposal_note') IS NULL ALTER TABLE Competition_Entries ADD proposal_note NVARCHAR(500) NULL;

COMMIT;

SELECT TABLE_NAME, COLUMN_NAME, DATA_TYPE, IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS
WHERE (TABLE_NAME = 'training_sessions' AND COLUMN_NAME IN ('max_heart_rate', 'max_speed_kmh', 'started_at', 'paused_at', 'paused_reason'))
   OR (TABLE_NAME = 'Session_Readings' AND COLUMN_NAME IN ('training_day_id', 'session_id'))
   OR (TABLE_NAME = 'Injury_Marks' AND COLUMN_NAME IN ('record_id', 'horse_id', 'case_id', 'created_by', 'created_at'))
   OR (TABLE_NAME = 'Users' AND COLUMN_NAME = 'avatar_url')
   OR (TABLE_NAME = 'Competition_Entries' AND COLUMN_NAME = 'proposal_note')
ORDER BY TABLE_NAME, COLUMN_NAME;
