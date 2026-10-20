-- =====================================================================
-- Xoa cac khoa ngoai TRUNG LAP do Hibernate tu tao (ddl-auto=update).
-- Moi khoa ngoai bi xoa deu con khoa ngoai goc cua nhom (cot keep) giu
-- cung rang buoc, nen du lieu va tinh toan ven khong thay doi.
-- Lap ngay 2026-10-20 tu DB EquineCareDB. Chay 1 lan; chay lai van an toan (IF EXISTS).
-- =====================================================================
USE EquineCareDB;
GO

-- Kiem tra dang chay dung server cua nhom (phai la server 103.245.237.252) truoc khi xoa
SELECT @@SERVERNAME AS server_name, DB_NAME() AS db_name,
       (SELECT COUNT(*) FROM sys.foreign_keys WHERE name LIKE 'FK[a-z0-9]%' AND LEN(name) = 27) AS so_khoa_ngoai_hibernate;
GO

SET XACT_ABORT ON;
BEGIN TRANSACTION;

-- Club_Manager_Profiles.user_id -> Users (giu lai: FK__Club_Mana__user___3B40CD36)
IF OBJECT_ID(N'[dbo].[FK40xrwh44huvei7pb5q6xksdw2]', N'F') IS NOT NULL ALTER TABLE [dbo].[Club_Manager_Profiles] DROP CONSTRAINT [FK40xrwh44huvei7pb5q6xksdw2];

-- Daily_Tasks.groom_id -> Users (giu lai: FK__Daily_Tas__groom__3E1D39E1)
IF OBJECT_ID(N'[dbo].[FKn8dfeutfii0gf6w8fcudjd2nt]', N'F') IS NOT NULL ALTER TABLE [dbo].[Daily_Tasks] DROP CONSTRAINT [FKn8dfeutfii0gf6w8fcudjd2nt];

-- Daily_Tasks.horse_id -> Horses (giu lai: FK__Daily_Tas__horse__3F115E1A)
IF OBJECT_ID(N'[dbo].[FKt54glay0lovjpmn6cq8yhtk66]', N'F') IS NOT NULL ALTER TABLE [dbo].[Daily_Tasks] DROP CONSTRAINT [FKt54glay0lovjpmn6cq8yhtk66];

-- Daily_Tasks.stall_id -> Stalls (giu lai: FK_DT_stall)
IF OBJECT_ID(N'[dbo].[FKjctwwdsi2tp6ye95txvk2vrjq]', N'F') IS NOT NULL ALTER TABLE [dbo].[Daily_Tasks] DROP CONSTRAINT [FKjctwwdsi2tp6ye95txvk2vrjq];

-- Groom_Profiles.user_id -> Users (giu lai: FK__Groom_Pro__user___40F9A68C)
IF OBJECT_ID(N'[dbo].[FK97h676hkecqhqeyse50f3bcre]', N'F') IS NOT NULL ALTER TABLE [dbo].[Groom_Profiles] DROP CONSTRAINT [FK97h676hkecqhqeyse50f3bcre];

-- Horse_Owner_Profiles.user_id -> Users (giu lai: FK__Horse_Own__user___41EDCAC5)
IF OBJECT_ID(N'[dbo].[FK7sgvtrl5nt2o5s9uchmxc7wsp]', N'F') IS NOT NULL ALTER TABLE [dbo].[Horse_Owner_Profiles] DROP CONSTRAINT [FK7sgvtrl5nt2o5s9uchmxc7wsp];

-- Injury_Marks.record_id -> Medical_Records (giu lai: FK__Injury_Ma__recor__46B27FE2)
IF OBJECT_ID(N'[dbo].[FKegdihdceku2srn1vqi0500ok2]', N'F') IS NOT NULL ALTER TABLE [dbo].[Injury_Marks] DROP CONSTRAINT [FKegdihdceku2srn1vqi0500ok2];

-- Inventory_Lots.item_id -> Inventory_Items (giu lai: FK_IL_item)
IF OBJECT_ID(N'[dbo].[FKm1b0nnxq8dlxoqowlpugf70o8]', N'F') IS NOT NULL ALTER TABLE [dbo].[Inventory_Lots] DROP CONSTRAINT [FKm1b0nnxq8dlxoqowlpugf70o8];

-- Prescription_Items.record_id -> Medical_Records (giu lai: FK__Prescript__recor__4E53A1AA)
IF OBJECT_ID(N'[dbo].[FK1wb64ydbjm5h7p3fxgnsci2f1]', N'F') IS NOT NULL ALTER TABLE [dbo].[Prescription_Items] DROP CONSTRAINT [FK1wb64ydbjm5h7p3fxgnsci2f1];

-- Protocol_Items.phase_id -> Protocol_Phases (giu lai: FK_PI_phase)
IF OBJECT_ID(N'[dbo].[FKia7lckck8rr4f1u3muxl8f180]', N'F') IS NOT NULL ALTER TABLE [dbo].[Protocol_Items] DROP CONSTRAINT [FKia7lckck8rr4f1u3muxl8f180];

-- Protocol_Phases.protocol_id -> Treatment_Protocols (giu lai: FK_PP_protocol)
IF OBJECT_ID(N'[dbo].[FKhs3uveyi5fls5sqbn6vhgne7c]', N'F') IS NOT NULL ALTER TABLE [dbo].[Protocol_Phases] DROP CONSTRAINT [FKhs3uveyi5fls5sqbn6vhgne7c];

-- Stock_Transactions.item_id -> Inventory_Items (giu lai: FK_ST_item)
IF OBJECT_ID(N'[dbo].[FKbu0o4vmyocuoka0c99p4xp8dy]', N'F') IS NOT NULL ALTER TABLE [dbo].[Stock_Transactions] DROP CONSTRAINT [FKbu0o4vmyocuoka0c99p4xp8dy];

-- Stock_Transactions.lot_id -> Inventory_Lots (giu lai: FK_ST_lot)
IF OBJECT_ID(N'[dbo].[FK7rr9xuxyjjsxwvulmol8mirad]', N'F') IS NOT NULL ALTER TABLE [dbo].[Stock_Transactions] DROP CONSTRAINT [FK7rr9xuxyjjsxwvulmol8mirad];

-- Stock_Transactions.performed_by -> Users (giu lai: FK_ST_user)
IF OBJECT_ID(N'[dbo].[FKfo0gahx989s3a0x5tq2rwn4iq]', N'F') IS NOT NULL ALTER TABLE [dbo].[Stock_Transactions] DROP CONSTRAINT [FKfo0gahx989s3a0x5tq2rwn4iq];

-- Trainer_Profiles.user_id -> Users (giu lai: FK__Trainer_P__user___51300E55)
IF OBJECT_ID(N'[dbo].[FKcnouyhrcuky4thfva7wrmgo5v]', N'F') IS NOT NULL ALTER TABLE [dbo].[Trainer_Profiles] DROP CONSTRAINT [FKcnouyhrcuky4thfva7wrmgo5v];

-- Treatment_Protocols.horse_id -> Horses (giu lai: FK_TPR_horse)
IF OBJECT_ID(N'[dbo].[FKak2sxnvhmqy82u73pyv0g14ou]', N'F') IS NOT NULL ALTER TABLE [dbo].[Treatment_Protocols] DROP CONSTRAINT [FKak2sxnvhmqy82u73pyv0g14ou];

-- Veterinarian_Profiles.user_id -> Users (giu lai: FK__Veterinar__user___56E8E7AB)
IF OBJECT_ID(N'[dbo].[FKmvd5nveu30y7bo7r2y1qjw7kh]', N'F') IS NOT NULL ALTER TABLE [dbo].[Veterinarian_Profiles] DROP CONSTRAINT [FKmvd5nveu30y7bo7r2y1qjw7kh];

COMMIT TRANSACTION;
GO

-- Kiem tra lai: ket qua phai rong (khong con cap khoa ngoai trung cot + bang tham chieu)
SELECT OBJECT_NAME(fk.parent_object_id) AS tbl, COL_NAME(fkc.parent_object_id, fkc.parent_column_id) AS col,
       OBJECT_NAME(fk.referenced_object_id) AS ref, COUNT(*) AS so_khoa_ngoai
FROM sys.foreign_keys fk JOIN sys.foreign_key_columns fkc ON fkc.constraint_object_id = fk.object_id
GROUP BY fk.parent_object_id, fkc.parent_object_id, fkc.parent_column_id, fk.referenced_object_id
HAVING COUNT(*) > 1;
