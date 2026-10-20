-- =====================================================================
-- Cho phep phac do dieu tri ke thuoc tu Inventory_Items.
--
-- Hibernate (ddl-auto=update truoc day) da tu tao khoa ngoai
--   Protocol_Items.item_id -> medications
-- Trong khi khoa ngoai dung cua nhom la FK_PI_item: Protocol_Items.item_id -> Inventory_Items.
-- Hai khoa ngoai cung luc khien moi dong thuoc phai co id trung o CA HAI bang, nen tao phac do se loi.
-- Script nay chi xoa khoa ngoai thua do Hibernate tao; FK_PI_item duoc giu nguyen.
-- Chay 1 lan; chay lai van an toan (IF EXISTS).
-- =====================================================================
USE EquineCareDB;
GO

IF OBJECT_ID(N'[dbo].[FKi2v2h684uqbna035hggemyvr3]', N'F') IS NOT NULL
    ALTER TABLE [dbo].[Protocol_Items] DROP CONSTRAINT [FKi2v2h684uqbna035hggemyvr3];
GO

-- Kiem tra: Protocol_Items.item_id chi con tro toi Inventory_Items (FK_PI_item)
SELECT fk.name, COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS col, OBJECT_NAME(fk.referenced_object_id) AS ref
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
WHERE OBJECT_NAME(fk.parent_object_id) = 'Protocol_Items';
GO

-- =====================================================================
-- (TUY CHON) Xoa cac bang thua do Hibernate tu tao, code KHONG con dung:
--   prescriptions, treatment_stages, medications  (phac do cu, da thay bang Treatment_Protocols/Protocol_Phases/Protocol_Items)
--   care_tasks                                    (da thay bang Daily_Tasks)
-- Bang medications hien co 4 dong du lieu thu. Bo comment khoi duoi neu nhom dong y xoa.
-- =====================================================================
/*
DROP TABLE IF EXISTS [dbo].[prescriptions];
DROP TABLE IF EXISTS [dbo].[treatment_stages];
DROP TABLE IF EXISTS [dbo].[medications];
DROP TABLE IF EXISTS [dbo].[care_tasks];
*/
