12E.2 PUT /api/v1/inventory/protocols/{protocolId}/stock-check — Kiểm kho
Hệ thống tự cộng số lượng từng thuốc qua các giai đoạn và so với khả dụng (đang có − đang giữ). Kho chỉ cần báo ngày về cho thuốc thiếu. Body có thể bỏ trống ({}).

{
  "shortages": [
    { "itemId": 11, "eta": "2026-10-25", "purchaseOrderId": null }
  ],
  "note": "Penicillin hết, NCC giao trong tuần"
}
Đủ hết: giữ hàng (tăng heldStock, nhật ký kho có dòng HOLD với refType = "PROTOCOL") → STOCK_AVAILABLE.
Thiếu: giữ phần đang có, ghi số thiếu và eta → STOCK_SHORTAGE, trả về bác sĩ quyết định (12.5 hoặc 12.6).
Phần đã giữ không xuất được bằng 12A.2 (ISSUE) nữa. Thử xuất vượt khả dụng → 400.
Kiểm lại khi hàng về: gọi lại API này (khi WAITING_RESTOCK, REQUIRES_VET_REVIEW hoặc STOCK_SHORTAGE). Lần giữ trước được trả lại (RELEASED) rồi tính lại từ đầu, không bị giữ trùng.
Test lỗi:
Kiểm kho phác đồ đã VET_CONFIRMED hoặc IN_TREATMENT → 400.
itemId trong shortages không có trong phác đồ → 400.
purchaseOrderId của đơn không có thuốc đó, hoặc đơn đã nhận đủ / đã hủy → 400.
12E.3 PUT /api/v1/inventory/protocols/{protocolId}/dispense — Cấp thuốc
Chỉ khi bác sĩ đã xác nhận (VET_CONFIRMED). Xuất toàn bộ phần đã giữ: heldStock và currentStock cùng giảm, nhật ký kho có dòng ISSUE với refType = "PROTOCOL".
Mong đợi: 200, phác đồ chuyển IN_TREATMENT, các dòng stockAnswer chuyển ISSUED.
Cấp khi chưa xác nhận → 400.
12E.4 Đặt mua thuốc cho phác đồ (BR_07)
Tạo đơn mua (12B.2) với "purpose": "PROTOCOL", "protocolId": <id>. requiredBy mặc định lấy ngày cần thuốc của phác đồ. Các dòng thiếu của phác đồ được gắn với đơn và nhận eta của đơn.
Hàng về trễ: nếu ngày về của đơn (lúc tạo, hoặc khi đổi ETA ở 12B.3) trễ hơn requiredBy của phác đồ, phác đồ đang STOCK_SHORTAGE / WAITING_RESTOCK sẽ tự chuyển REQUIRES_VET_REVIEW (isDelayed = true) để bác sĩ quyết định chờ tiếp hay đổi phác đồ.
13–17. Các API dữ liệu giả (mock)
Các API dưới đây không đọc/ghi database. Dữ liệu được code cứng hoặc nằm trong RAM. Chỉ cần kiểm tra là gọi được và trả đúng format. Tất cả yêu cầu đã login (role nào cũng được).

13. Pre-race Exam (khám trước đua) — dữ liệu trong RAM, mất khi restart
GET /api/v1/prerace-exams?tab=CHO_KHAM → 3 đề xuất: DX-0009, DX-0008, DX-0007.

GET /api/v1/prerace-exams?tab=DA_CO_KET_QUA → 2 đề xuất: DX-0006, DX-0004.

POST /api/v1/prerace-exams/DX-0009/examine với body:

{ "isPassed": true, "vetNotes": "Tim mạch ổn định" }
→ 200. Gọi lại bước 1 và 2: DX-0009 đã chuyển sang tab DA_CO_KET_QUA với resultStatus = "ĐẠT".

Test lỗi: khám lại DX-0009, hoặc dùng mã không tồn tại → 400 "Không tìm thấy mã đề xuất hoặc đề xuất đã được khám!".

14. Protocols
GET /api/v1/protocols?tab=... → luôn trả 2 phác đồ cố định (PD-0013, PD-0012), bất kể tab.
GET /api/v1/protocols/shipments → luôn trả 1 lô hàng SHIP-0024.
15. Vaccinations
GET /api/v1/vaccinations?tab=upcoming → luôn trả 2 bản ghi cố định (VC-036, VC-033).

POST /api/v1/vaccinations/VC-036/execute → luôn 200, kể cả mã không tồn tại.

POST /api/v1/vaccinations/schedule với body:

{ "horseIds": [1, 2, 3], "medicineName": "Vaccine cúm ngựa", "scheduledDate": "2026-11-01", "notes": "Mũi nhắc lại" }
→ 200 "Đã tạo lịch Vaccine cúm ngựa thành công cho 3 ngựa.". Gửi "horseIds": [] → 400 "Vui lòng chọn ít nhất một ngựa!".

18. Checklist phân quyền nhanh
Sau khi test chức năng chính, thử gọi mỗi API sau bằng sai role và xác nhận đều nhận 403:

API	Chỉ cho phép
POST /auth/staff/create	Club Manager
PUT /manager/medical-records/{id}/approve	Club Manager
GET /training-plans/{planId}	Trainer, Club Manager
POST /training-plans, GET /training-plans/assignable-staff, POST /training-plans/{id}/sessions	Trainer
POST /nutrition-plans	Trainer, Veterinarian
POST /veterinary/medical-records	Veterinarian
POST /medical/release-lock/{horseId}	Veterinarian
/inventory/** (kho, nhà cung cấp, đơn mua, kiểm kê, xử lý yêu cầu vật tư)	Warehouse Manager
/supply-requests/** (gửi, xem, hủy yêu cầu vật tư)	Groom
POST /medical-management/protocols và các thao tác trên phác đồ (confirm, wait-restock, new-version, progress-notes, complete, cancel)	Veterinarian
GET /medical-management/medications, GET /medical-management/protocols/**	Veterinarian, Club Manager (chỉ xem)
POST /medical/vet-lock	Veterinarian, Trainer, Groom
GET /medical/cases, POST /medical/cases/action	Veterinarian
POST /horses/register, GET /horses/my-horses/{ownerId}	Horse Owner (chỉ của mình), Club Manager
GET /horses	Club Manager, Trainer, Veterinarian, Groom
GET /horses/{id}	Nhân viên, và Horse Owner (chỉ ngựa của mình)
PUT /horses/{id}/approve	Club Manager
PUT /horses/{id}	Club Manager, Trainer, Veterinarian (đổi isLocked hoặc healthStatus: chỉ Veterinarian)
GET /horses/available-for-training	Trainer, Club Manager
POST /care-tasks	Trainer
GET /care-tasks/my-tasks, PUT /care-tasks/{id}/complete	Groom
/training-sessions/**	Trainer, Groom
/owner/training/**, /owner/payments/**	Horse Owner
Mọi API khác (trừ login/register/refresh/logout) chỉ cần đăng nhập. Gọi mà không Authorize phải nhận 401.

19. Lỗi và điểm cần lưu ý phát hiện khi đọc code
Đây là những chỗ có thể làm bạn tưởng mình test sai. Thực ra là do code hoặc DB.

L1. (Đã kiểm tra lại: không còn là lỗi.) DB thật cho phép role Trainer (và Warehouse Manager). Script DATABASE trong repo đã cũ so với DB thật: các giá trị CHECK đều đã đổi sang dạng HEALTHY, CLEAN, FEED... Nên đối chiếu với DB thật thay vì script.
L2. (Đã sửa.) Bệnh án có isLockoutOrdered: true trước đây gán healthStatus = "VetLock = ACTIVE" (không hợp lệ) nên lỗi 500. Giờ gán INJURED.
L3. (Đã sửa.) Kê thuốc vượt tồn kho trước đây trả 500 do String.format dùng %d cho kiểu double. Giờ trả 400 đúng thông báo. Câu truy vấn hàng sắp về cũng đã được viết lại theo bảng thật Purchase_Orders + Purchase_Order_Lines (trước đó lỗi Invalid column name 'item_id').
L4. Lỗi nghiệp vụ trả 500. Service ném RuntimeException ("Horse not found", "Old password is incorrect", duyệt lại hồ sơ...). GlobalExceptionHandler không bắt riêng loại này, nên chúng rơi vào handler chung → 500 "Lỗi máy chủ: ...". Đúng ra nên là 400 hoặc 404.
L5. (Đã sửa.) my-profile trước đây kiểm tra cả role HEAD_TRAINER. Hệ thống đã bỏ role này, chỉ còn Trainer.
L6. Một số API vẫn chưa phân quyền. Các API mock (khám trước đua, protocols, vaccinations) vẫn cho mọi user đã login gọi. Các API ngựa, /medical/** và /medical-management/** đã được phân quyền (xem mục 18).
L7. Thiếu API tạo dữ liệu. Đã có API tạo Training Plan (6.0) và Care Task (9.0). Đã có module kho cho nhân viên kho (12A–12D). Đã có kiểm kho và cấp thuốc cho phác đồ (12E). Vẫn chưa có API tạo Stall.
L8. AuditController và AudtitController là class rỗng. Hai class này không có endpoint nào và tên sau bị gõ sai chính tả.
