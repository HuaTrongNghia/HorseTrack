-- =========================================================================
-- DATABASE SCRIPT: MODULE QUẢN LÝ CHUỒNG TRẠI & DINH DƯỠNG (FLOW 4 - MÀN HÌNH 1)
-- Hệ cơ sở dữ liệu: Microsoft SQL Server (EquineCareDB)
-- =========================================================================

-- 1. Bảng Nutrition_Plans (Kế hoạch thực đơn dinh dưỡng tổng quan)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Nutrition_Plans')
BEGIN
    CREATE TABLE Nutrition_Plans (
        plan_id INT IDENTITY(1,1) PRIMARY KEY,
        horse_id INT NOT NULL,
        created_by_id INT NOT NULL,
        created_date DATETIME2 NOT NULL DEFAULT GETDATE(),
        start_date DATE NOT NULL,
        end_date DATE NOT NULL,
        notes NVARCHAR(MAX) NULL,
        CONSTRAINT FK_Nutrition_Plans_Horses FOREIGN KEY (horse_id) REFERENCES Horses(horse_id),
        CONSTRAINT FK_Nutrition_Plans_Users FOREIGN KEY (created_by_id) REFERENCES Users(user_id)
    );
END;

-- 2. Bảng Nutrition_Plan_Details (Chi tiết các bữa ăn trong ngày)
IF NOT EXISTS (SELECT * FROM sys.tables WHERE name = 'Nutrition_Plan_Details')
BEGIN
    CREATE TABLE Nutrition_Plan_Details (
        detail_id INT IDENTITY(1,1) PRIMARY KEY,
        plan_id INT NOT NULL,
        inventory_item_id INT NOT NULL,
        meal_type NVARCHAR(30) NOT NULL, -- MORNING, AFTERNOON, EVENING
        quantity FLOAT NOT NULL,          -- Định lượng (kg)
        CONSTRAINT FK_Nutrition_Plan_Details_Plans FOREIGN KEY (plan_id) REFERENCES Nutrition_Plans(plan_id) ON DELETE CASCADE,
        CONSTRAINT FK_Nutrition_Plan_Details_Items FOREIGN KEY (inventory_item_id) REFERENCES Inventory_Items(item_id)
    );
END;
