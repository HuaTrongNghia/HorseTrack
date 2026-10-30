/*
  Flow 5 - Giải đấu & đề xuất thi đấu (chủ ngựa đề xuất, giữ chỗ 3 chặng, hàng chờ, khiếu nại, kết quả & giải thưởng).
  Chạy MỘT LẦN trên EquineCareDB TRƯỚC KHI deploy bản BE mới (bản mới đọc các cột dưới đây).
  Script chạy lại được nhiều lần (kiểm tra tồn tại trước khi thêm), toàn bộ trong 1 batch / 1 transaction (lỗi ở bất kỳ câu nào thì không có gì thay đổi).
  Các câu dùng cột vừa thêm chạy qua EXEC để SQL Server không báo "Invalid column name" khi biên dịch batch.
  Lưu ý: luồng cũ (HLV đề xuất -> chủ ngựa đồng ý -> gửi BTC) bị thay thế; trạng thái cũ không còn dùng.
  Nếu bảng Competition_Entries còn đề xuất ở trạng thái cũ, script dừng lại để bạn quyết định (lúc viết script bảng đang trống).
*/
SET XACT_ABORT ON;
SET QUOTED_IDENTIFIER ON;  -- bắt buộc khi tạo chỉ mục có điều kiện (filtered index)
SET ANSI_NULLS ON;
BEGIN TRANSACTION;

IF EXISTS (SELECT 1 FROM dbo.Competition_Entries WHERE registration_status IN
           ('WAITING_OWNER', 'OWNER_DECLINED', 'WAITING_VET', 'READY_TO_SUBMIT', 'SUBMITTED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN'))
BEGIN
    THROW 50001, N'Competition_Entries còn đề xuất ở trạng thái của luồng cũ. Hãy xử lý / xóa các đề xuất này trước khi chạy script.', 1;
END;

/* ---------- 1. Giải đấu: điều kiện tham gia, số suất, trạng thái ---------- */
IF COL_LENGTH('dbo.External_Competitions', 'min_age_years') IS NULL
    ALTER TABLE dbo.External_Competitions ADD min_age_years INT NULL;
IF COL_LENGTH('dbo.External_Competitions', 'max_age_years') IS NULL
    ALTER TABLE dbo.External_Competitions ADD max_age_years INT NULL;
IF COL_LENGTH('dbo.External_Competitions', 'min_weight_kg') IS NULL
    ALTER TABLE dbo.External_Competitions ADD min_weight_kg DECIMAL(6, 2) NULL;
IF COL_LENGTH('dbo.External_Competitions', 'max_weight_kg') IS NULL
    ALTER TABLE dbo.External_Competitions ADD max_weight_kg DECIMAL(6, 2) NULL;
IF COL_LENGTH('dbo.External_Competitions', 'allowed_breeds') IS NULL
    ALTER TABLE dbo.External_Competitions ADD allowed_breeds NVARCHAR(500) NULL;   -- các giống, ngăn cách bằng ';' (trống = mọi giống)
IF COL_LENGTH('dbo.External_Competitions', 'slots') IS NULL
    ALTER TABLE dbo.External_Competitions ADD slots INT NOT NULL CONSTRAINT DF_EC_slots DEFAULT (1);
IF COL_LENGTH('dbo.External_Competitions', 'tournament_status') IS NULL
    ALTER TABLE dbo.External_Competitions ADD tournament_status VARCHAR(20) NOT NULL CONSTRAINT DF_EC_status DEFAULT ('DRAFT');
IF COL_LENGTH('dbo.External_Competitions', 'published_at') IS NULL
    ALTER TABLE dbo.External_Competitions ADD published_at DATETIME2 NULL;

IF OBJECT_ID('dbo.CHK_EC_slots', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.External_Competitions ADD CONSTRAINT CHK_EC_slots CHECK (slots > 0)');
IF OBJECT_ID('dbo.CHK_EC_status', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.External_Competitions ADD CONSTRAINT CHK_EC_status CHECK (tournament_status IN (''DRAFT'', ''PUBLISHED'', ''FINISHED''))');
IF OBJECT_ID('dbo.CHK_EC_age', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.External_Competitions ADD CONSTRAINT CHK_EC_age CHECK (min_age_years IS NULL OR max_age_years IS NULL OR min_age_years <= max_age_years)');
IF OBJECT_ID('dbo.CHK_EC_weight', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.External_Competitions ADD CONSTRAINT CHK_EC_weight CHECK (min_weight_kg IS NULL OR max_weight_kg IS NULL OR min_weight_kg <= max_weight_kg)');
-- Giải đã kết thúc từ trước thì coi như FINISHED
EXEC(N'UPDATE dbo.External_Competitions SET tournament_status = ''FINISHED'' WHERE is_finished = 1 AND tournament_status <> ''FINISHED''');

/* ---------- 2. Bảng thưởng theo hạng ---------- */
IF OBJECT_ID('dbo.Competition_Prizes', 'U') IS NULL
    CREATE TABLE dbo.Competition_Prizes (
        competition_id INT NOT NULL CONSTRAINT FK_CP_competition REFERENCES dbo.External_Competitions (competition_id) ON DELETE CASCADE,
        rank_no INT NOT NULL CONSTRAINT CHK_CP_rank CHECK (rank_no BETWEEN 1 AND 100),
        amount DECIMAL(18, 2) NOT NULL CONSTRAINT CHK_CP_amount CHECK (amount >= 0),
        CONSTRAINT PK_Competition_Prizes PRIMARY KEY (competition_id, rank_no)
    );

/* ---------- 3. Đề xuất: giữ chỗ / hàng chờ, hạn chặng, HLV xác nhận, hóa đơn, khiếu nại ---------- */
IF COL_LENGTH('dbo.Competition_Entries', 'owner_id') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD owner_id INT NULL CONSTRAINT FK_CE_owner REFERENCES dbo.Users (user_id);
IF COL_LENGTH('dbo.Competition_Entries', 'slot_held_at') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD slot_held_at DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'stage_deadline') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD stage_deadline DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'deadline_reminded') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD deadline_reminded BIT NOT NULL CONSTRAINT DF_CE_reminded DEFAULT (0);
IF COL_LENGTH('dbo.Competition_Entries', 'form_score') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD form_score DECIMAL(5, 2) NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'trainer_id') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD trainer_id INT NULL CONSTRAINT FK_CE_trainer REFERENCES dbo.Users (user_id);
IF COL_LENGTH('dbo.Competition_Entries', 'trainer_approved') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD trainer_approved BIT NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'trainer_decided_at') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD trainer_decided_at DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'trainer_reason_code') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD trainer_reason_code VARCHAR(30) NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'trainer_note') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD trainer_note NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'invoice_id') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD invoice_id INT NULL CONSTRAINT FK_CE_invoice REFERENCES dbo.Invoices (invoice_id);
IF COL_LENGTH('dbo.Competition_Entries', 'paid_at') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD paid_at DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'complaint_status') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD complaint_status VARCHAR(20) NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'complaint_note') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD complaint_note NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'complaint_at') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD complaint_at DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'complaint_decided_by') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD complaint_decided_by INT NULL CONSTRAINT FK_CE_complaint_by REFERENCES dbo.Users (user_id);
IF COL_LENGTH('dbo.Competition_Entries', 'complaint_decided_at') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD complaint_decided_at DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'complaint_decision_note') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD complaint_decision_note NVARCHAR(500) NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'closed_at') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD closed_at DATETIME2 NULL;
IF COL_LENGTH('dbo.Competition_Entries', 'closed_reason') IS NULL
    ALTER TABLE dbo.Competition_Entries ADD closed_reason NVARCHAR(500) NULL;

-- Trạng thái mới
IF OBJECT_ID('dbo.CHK_CE_owner_declined', 'C') IS NOT NULL
    ALTER TABLE dbo.Competition_Entries DROP CONSTRAINT CHK_CE_owner_declined;
IF OBJECT_ID('dbo.CHK_Entry_Status', 'C') IS NOT NULL
    ALTER TABLE dbo.Competition_Entries DROP CONSTRAINT CHK_Entry_Status;
ALTER TABLE dbo.Competition_Entries ADD CONSTRAINT CHK_Entry_Status CHECK (registration_status IN (
    'WAITLISTED', 'WAITING_TRAINER', 'TRAINER_REJECTED', 'TRAINER_EXPIRED',
    'WAITING_VET_EXAM', 'VET_FAILED', 'VET_EXPIRED', 'WAITING_PAYMENT', 'PAYMENT_EXPIRED',
    'REGISTERED', 'RESULT_RECORDED', 'CANCELLED', 'INELIGIBLE', 'NO_SLOT'));
IF OBJECT_ID('dbo.DF_CE_registration_status', 'D') IS NOT NULL
    ALTER TABLE dbo.Competition_Entries DROP CONSTRAINT DF_CE_registration_status;
ALTER TABLE dbo.Competition_Entries ADD CONSTRAINT DF_CE_registration_status DEFAULT ('WAITLISTED') FOR registration_status;
IF OBJECT_ID('dbo.CHK_CE_complaint', 'C') IS NULL
    EXEC(N'ALTER TABLE dbo.Competition_Entries ADD CONSTRAINT CHK_CE_complaint CHECK (complaint_status IS NULL OR complaint_status IN (''PENDING'', ''ACCEPTED'', ''REJECTED''))');

-- Đề xuất lại (sau khi mất suất) được tạo dòng mới cho cùng ngựa: bỏ ràng buộc 1 ngựa / 1 giải,
-- thay bằng: mỗi ngựa và mỗi chủ ngựa chỉ có 1 đề xuất còn hiệu lực cho mỗi giải
IF EXISTS (SELECT 1 FROM sys.key_constraints WHERE name = 'UQ_Horse_Competition')
    ALTER TABLE dbo.Competition_Entries DROP CONSTRAINT UQ_Horse_Competition;
IF EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UQ_Horse_Competition' AND object_id = OBJECT_ID('dbo.Competition_Entries'))
    DROP INDEX UQ_Horse_Competition ON dbo.Competition_Entries;
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_CE_owner_active' AND object_id = OBJECT_ID('dbo.Competition_Entries'))
    EXEC(N'CREATE UNIQUE INDEX UX_CE_owner_active ON dbo.Competition_Entries (competition_id, owner_id)
        WHERE registration_status IN (''WAITLISTED'', ''WAITING_TRAINER'', ''WAITING_VET_EXAM'', ''WAITING_PAYMENT'', ''REGISTERED'')');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'UX_CE_horse_active' AND object_id = OBJECT_ID('dbo.Competition_Entries'))
    CREATE UNIQUE INDEX UX_CE_horse_active ON dbo.Competition_Entries (competition_id, horse_id)
        WHERE registration_status IN ('WAITLISTED', 'WAITING_TRAINER', 'WAITING_VET_EXAM', 'WAITING_PAYMENT', 'REGISTERED');
IF NOT EXISTS (SELECT 1 FROM sys.indexes WHERE name = 'IX_CE_competition_status' AND object_id = OBJECT_ID('dbo.Competition_Entries'))
    CREATE INDEX IX_CE_competition_status ON dbo.Competition_Entries (competition_id, registration_status, proposed_at);

COMMIT TRANSACTION;
PRINT N'Đã cập nhật schema giải đấu flow 5.';
