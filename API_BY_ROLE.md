# API theo vai trò: Admin, HLV, Chủ ngựa

Sinh tự động từ `@PreAuthorize` và `@Operation` trong các controller của Test API 4 (182 endpoint).
Swagger UI: `/swagger-ui.html`. Bấm **Authorize**, dán `accessToken` lấy từ `POST /api/v1/auth/login` của tài khoản đúng vai trò.
Cột "Vai trò khác" cho biết endpoint còn mở cho vai trò nào (CM = Club Manager, HLV = Trainer, Chủ = Horse Owner, BS = Bác sĩ thú y, Groom, Kho = Warehouse Manager).
Nhiều endpoint dùng chung còn lọc dữ liệu theo phạm vi: HLV chỉ thấy ngựa được phân công, Chủ ngựa chỉ thấy ngựa của mình.

## 0. Dùng chung

### Không cần đăng nhập

**Authentication & Onboarding**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/auth/confirm-account` | Nhân viên xác nhận tài khoản (liên kết trong email) | Công khai |
| POST | `/api/v1/auth/confirm-account` | Nhân viên xác nhận tài khoản (FE gửi mã) | Công khai |
| POST | `/api/v1/auth/forgot-password` | Quên mật khẩu: gửi OTP | Công khai |
| POST | `/api/v1/auth/login` | Đăng nhập hệ thống | Công khai |
| POST | `/api/v1/auth/logout` | Đăng xuất | Công khai |
| POST | `/api/v1/auth/refresh` | Làm mới token | Công khai |
| POST | `/api/v1/auth/register-owner` | Khách hàng tự đăng ký (2 bước, xác thực email bằng OTP) | Công khai |
| POST | `/api/v1/auth/reset-password` | Quên mật khẩu: đặt mật khẩu mới bằng OTP | Công khai |

**VnPayController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/payments/vnpay/ipn` | VNPay IPN (server-to-server) | Công khai |
| GET | `/api/v1/payments/vnpay/return` | VNPay Return URL | Công khai |

### Mọi vai trò đã đăng nhập

**AccountController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| POST | `/api/v1/account/avatar` | Đổi ảnh đại diện | Tất cả |
| PUT | `/api/v1/account/change-password` | Đổi mật khẩu | Tất cả |
| POST | `/api/v1/account/change-password/otp` | Gửi OTP đổi mật khẩu | Tất cả |
| GET | `/api/v1/account/my-profile` | Hồ sơ của tôi | Tất cả |
| PUT | `/api/v1/account/my-profile` | Cập nhật hồ sơ | Tất cả |

**ProtocolController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/protocols` | Danh sách phác đồ tóm tắt | Tất cả |
| GET | `/api/v1/protocols/shipments` | Lô hàng phục vụ phác đồ | Tất cả |

**StallController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/stalls` | Sơ đồ chuồng trại | Tất cả |

**TournamentController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/tournaments` | Danh sách giải đấu | Tất cả |

## 1. Admin (Club Manager): 65 endpoint (30 chỉ riêng vai trò này)

**AssignmentRequestController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/assignments/pending` | Phân công đang chờ | — |

**Audit Logs**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/audit-logs` | Xem nhật ký hệ thống gần nhất | — |
| GET | `/api/v1/audit-logs/paged` | Xem nhật ký phân trang | — |

**Authentication & Onboarding**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| POST | `/api/v1/auth/staff/create` | Quản lý tạo nhân viên | — |

**BillingController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/billing/invoices` | Tất cả hóa đơn | — |
| GET | `/api/v1/billing/invoices/{invoiceId}` | Chi tiết hóa đơn | — |
| PUT | `/api/v1/billing/invoices/{invoiceId}/cancel` | Hủy hóa đơn | — |
| POST | `/api/v1/billing/invoices/generate` | Phát hành hóa đơn tháng | — |
| PUT | `/api/v1/billing/owners/{ownerId}/reactivate` | Kích hoạt lại dịch vụ (BR_14) | — |
| GET | `/api/v1/billing/owners/{ownerId}/service` | Trạng thái dịch vụ của chủ ngựa | — |
| PUT | `/api/v1/billing/owners/{ownerId}/suspend` | Đình chỉ dịch vụ (BR_13) | — |
| PUT | `/api/v1/billing/stalls/{stallId}/monthly-fee` | Đặt phí lưu chuồng tháng | — |

**CareTaskController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/care-tasks/assigned` | Việc chăm sóc đã giao | HLV |
| GET | `/api/v1/care-tasks/overdue` | Việc chăm sóc trễ hạn | — |

**Diet Change Requests**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/diet-requests` | Xem danh sách đề xuất thức ăn đặc biệt | BS, Groom, HLV |
| GET | `/api/v1/diet-requests/{requestId}` | Xem chi tiết đề xuất thức ăn đặc biệt | BS, Groom, HLV |

**EmailLogController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/email-logs` | Nhật ký gửi email | — |

**FinanceReportController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/reports/finance` | Báo cáo tài chính | — |

**HorseController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses` | Danh sách ngựa | HLV, BS, Groom |
| GET | `/api/v1/horses/{id}` | Chi tiết ngựa | HLV, BS, Groom, Chủ |
| PUT | `/api/v1/horses/{id}` | Cập nhật hồ sơ ngựa | HLV, BS |
| PUT | `/api/v1/horses/{id}/approve` | Duyệt hồ sơ và cấp chuồng | — |
| DELETE | `/api/v1/horses/{id}/assign-trainer` | Bỏ phân công HLV | — |
| PUT | `/api/v1/horses/{id}/assign-trainer` | Phân công HLV | — |
| GET | `/api/v1/horses/{id}/registrations` | Lịch sử hồ sơ ngựa | Chủ |
| PUT | `/api/v1/horses/{id}/reject` | Từ chối hồ sơ ngựa | — |
| GET | `/api/v1/horses/available-for-training` | Ngựa có thể lập giáo án | HLV |
| GET | `/api/v1/horses/my-horses/{ownerId}` | Ngựa của một chủ | Chủ |
| POST | `/api/v1/horses/register` | Đăng ký ngựa | Chủ |

**HorseHealthController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{id}/grooms` | Groom phụ trách khu của ngựa | HLV |
| GET | `/api/v1/horses/{id}/injuries` | Danh sách chấn thương | HLV, BS, Groom, Chủ |

**Inventory - Protocol Medicines (Warehouse Manager)**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/inventory/protocols/{protocolId}/dispensing` | Lịch sử cấp phát thuốc | Kho, BS |

**ManagerMedicalController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| PUT | `/api/v1/manager/medical-records/{recordId}/approve` | Phê duyệt bệnh án | — |

**Nutrition Plans**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{horseId}/nutrition-plan/today` | Xem thực đơn hôm nay của ngựa | HLV, BS, Groom, Chủ |
| GET | `/api/v1/nutrition-plans` | Xem danh sách thực đơn (lọc theo horseId, status) | HLV, BS, Groom, Chủ |
| GET | `/api/v1/nutrition-plans/{planId}` | Xem chi tiết thực đơn theo ID | HLV, BS, Groom, Chủ |
| GET | `/api/v1/nutrition-plans/feed-items` | Danh sách thức ăn / phụ gia trong kho để lên thực đơn | HLV, BS, Groom |

**PreRaceExamController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/prerace-exams` | Danh sách khám tiền thi đấu | BS |

**RaceProposalController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/race-proposals` | Danh sách đề xuất thi đấu | HLV, Chủ, BS |
| GET | `/api/v1/race-proposals/{id}` | Chi tiết đề xuất thi đấu | HLV, Chủ, BS |

**RegistrationAttachmentController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{horseId}/registration-attachments` | Danh sách giấy tờ hồ sơ | Chủ |
| GET | `/api/v1/horses/registration-attachments/{attachmentId}/file` | Xem hoặc tải giấy tờ | Chủ |

**StaffAssignmentController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/users/{id}/barn-zone` | Khu chăm sóc của Groom | — |
| PUT | `/api/v1/users/{id}/barn-zone` | Phân công / đổi / bỏ khu chăm sóc của Groom | — |

**StallController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| PUT | `/api/v1/stalls/{id}/clean-status` | Cập nhật trạng thái chuồng | Groom |

**TournamentController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| POST | `/api/v1/tournaments` | Thêm giải đấu | — |

**TrainingExecutionController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| PUT | `/api/v1/training-sessions/{sessionId}/evaluate` | Đánh giá buổi tập | HLV |
| PUT | `/api/v1/training-sessions/{sessionId}/execute` | Hoàn thành hoặc hủy buổi tập | HLV, Groom |
| GET | `/api/v1/training-sessions/my-schedule` | Lịch tập của tôi | HLV, Groom |

**TrainingPlanController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/training-plans` | Danh sách giáo án | HLV, Groom, BS |
| GET | `/api/v1/training-plans/{planId}` | Chi tiết giáo án và lịch tập | HLV |
| PUT | `/api/v1/training-plans/{planId}/cancel` | Hủy giáo án | HLV |
| PUT | `/api/v1/training-plans/{planId}/resume` | Cho giáo án chạy lại | HLV |
| GET | `/api/v1/training-plans/dashboard-stats` | Số liệu bảng điều khiển giáo án | HLV, Groom, BS |

**Treatment Protocols (Veterinarian)**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/medical-management/medications` | Danh mục thuốc (chỉ đọc) | BS |
| GET | `/api/v1/medical-management/protocols` | Danh sách phác đồ | BS |
| GET | `/api/v1/medical-management/protocols/{protocolId}` | Chi tiết phác đồ | BS |

**User Management**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/users` | Lấy danh sách người dùng phân trang & tìm kiếm | — |
| GET | `/api/v1/users/{id}` | Xem chi tiết một người dùng theo ID | — |
| PUT | `/api/v1/users/{id}/lock` | Khóa tài khoản người dùng (BR_10) | — |
| POST | `/api/v1/users/{id}/resend-confirmation` | Gửi lại email xác nhận tài khoản nhân viên | — |
| PUT | `/api/v1/users/{id}/role` | Đổi vai trò người dùng (BR_06 & BR_10) | — |
| PUT | `/api/v1/users/{id}/unlock` | Mở khóa tài khoản người dùng (BR_10) | — |
| GET | `/api/v1/users/all` | Lấy toàn bộ danh sách người dùng (không phân trang) | — |

**VaccinationController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/vaccinations` | Lịch tiêm phòng / tẩy giun | BS, HLV, Groom, Chủ |

## 2. HLV (Trainer): 45 endpoint (10 chỉ riêng vai trò này)

**AssignmentRequestController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| PUT | `/api/v1/assignments/{id}/accept` | Xác nhận phân công | Groom |
| PUT | `/api/v1/assignments/{id}/reject` | Từ chối phân công | Groom |
| GET | `/api/v1/assignments/mine` | Phân công chờ tôi xác nhận | Groom |

**CareTaskController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| POST | `/api/v1/care-tasks` | Giao việc chăm sóc | — |
| GET | `/api/v1/care-tasks/assigned` | Việc chăm sóc đã giao | CM |

**Diet Change Requests**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/diet-requests` | Xem danh sách đề xuất thức ăn đặc biệt | BS, Groom, CM |
| POST | `/api/v1/diet-requests` | Mã phu hoặc HLV gửi đề xuất thức ăn đặc biệt cho ngựa | Groom |
| GET | `/api/v1/diet-requests/{requestId}` | Xem chi tiết đề xuất thức ăn đặc biệt | BS, Groom, CM |

**HorseController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses` | Danh sách ngựa | CM, BS, Groom |
| GET | `/api/v1/horses/{id}` | Chi tiết ngựa | CM, BS, Groom, Chủ |
| PUT | `/api/v1/horses/{id}` | Cập nhật hồ sơ ngựa | CM, BS |
| GET | `/api/v1/horses/available-for-training` | Ngựa có thể lập giáo án | CM |

**HorseHealthController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{id}/grooms` | Groom phụ trách khu của ngựa | CM |
| GET | `/api/v1/horses/{id}/injuries` | Danh sách chấn thương | CM, BS, Groom, Chủ |

**MedicalController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/medical/cases` | Hộp thư ca khám | BS, Groom |
| POST | `/api/v1/medical/cases` | Báo sự cố sức khỏe | Groom |

**Nutrition Plans**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{horseId}/nutrition-plan/today` | Xem thực đơn hôm nay của ngựa | BS, Groom, CM, Chủ |
| GET | `/api/v1/nutrition-plans` | Xem danh sách thực đơn (lọc theo horseId, status) | BS, Groom, CM, Chủ |
| POST | `/api/v1/nutrition-plans` | Tạo thực đơn dinh dưỡng mới | BS |
| DELETE | `/api/v1/nutrition-plans/{planId}` | Xóa thực đơn dinh dưỡng | BS |
| GET | `/api/v1/nutrition-plans/{planId}` | Xem chi tiết thực đơn theo ID | BS, Groom, CM, Chủ |
| PUT | `/api/v1/nutrition-plans/{planId}` | Chỉnh sửa thực đơn dinh dưỡng | BS |
| PUT | `/api/v1/nutrition-plans/{planId}/cancel` | Hủy thực đơn dinh dưỡng | BS |
| POST | `/api/v1/nutrition-plans/{planId}/feed-tasks` | Gửi khẩu phần thành việc cho ăn cho Groom | BS |
| GET | `/api/v1/nutrition-plans/feed-items` | Danh sách thức ăn / phụ gia trong kho để lên thực đơn | BS, Groom, CM |

**RaceProposalController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/race-proposals` | Danh sách đề xuất thi đấu | Chủ, BS, CM |
| POST | `/api/v1/race-proposals` | Gửi đề xuất thi đấu | — |
| GET | `/api/v1/race-proposals/{id}` | Chi tiết đề xuất thi đấu | Chủ, BS, CM |
| PUT | `/api/v1/race-proposals/{id}/organizer-rejected` | Ghi nhận ban tổ chức từ chối | — |
| PUT | `/api/v1/race-proposals/{id}/result` | Ghi kết quả thi đấu | — |
| PUT | `/api/v1/race-proposals/{id}/submitted` | Ghi nhận đã gửi ban tổ chức | — |

**TrainingExecutionController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| PUT | `/api/v1/training-sessions/{sessionId}/evaluate` | Đánh giá buổi tập | CM |
| PUT | `/api/v1/training-sessions/{sessionId}/execute` | Hoàn thành hoặc hủy buổi tập | Groom, CM |
| PUT | `/api/v1/training-sessions/{sessionId}/resume` | Cho buổi tập tiếp tục | — |
| PUT | `/api/v1/training-sessions/{sessionId}/stop` | Dừng buổi tập | — |
| GET | `/api/v1/training-sessions/my-schedule` | Lịch tập của tôi | Groom, CM |

**TrainingPlanController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/training-plans` | Danh sách giáo án | CM, Groom, BS |
| POST | `/api/v1/training-plans` | Lập giáo án huấn luyện | — |
| GET | `/api/v1/training-plans/{planId}` | Chi tiết giáo án và lịch tập | CM |
| PUT | `/api/v1/training-plans/{planId}/cancel` | Hủy giáo án | CM |
| PUT | `/api/v1/training-plans/{planId}/resume` | Cho giáo án chạy lại | CM |
| POST | `/api/v1/training-plans/{planId}/sessions` | Thêm buổi tập vào giáo án | — |
| GET | `/api/v1/training-plans/assignable-staff` | Nhân sự có thể phân công | — |
| GET | `/api/v1/training-plans/dashboard-stats` | Số liệu bảng điều khiển giáo án | CM, Groom, BS |

**VaccinationController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/vaccinations` | Lịch tiêm phòng / tẩy giun | BS, CM, Groom, Chủ |

## 3. Chủ ngựa (Horse Owner): 24 endpoint (11 chỉ riêng vai trò này)

**HorseController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{id}` | Chi tiết ngựa | CM, HLV, BS, Groom |
| GET | `/api/v1/horses/{id}/registrations` | Lịch sử hồ sơ ngựa | CM |
| PUT | `/api/v1/horses/{id}/resubmit` | Nộp lại hồ sơ ngựa | — |
| GET | `/api/v1/horses/my-horses/{ownerId}` | Ngựa của một chủ | CM |
| POST | `/api/v1/horses/register` | Đăng ký ngựa | CM |

**HorseHealthController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{id}/injuries` | Danh sách chấn thương | CM, HLV, BS, Groom |

**Nutrition Plans**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{horseId}/nutrition-plan/today` | Xem thực đơn hôm nay của ngựa | HLV, BS, Groom, CM |
| GET | `/api/v1/nutrition-plans` | Xem danh sách thực đơn (lọc theo horseId, status) | HLV, BS, Groom, CM |
| GET | `/api/v1/nutrition-plans/{planId}` | Xem chi tiết thực đơn theo ID | HLV, BS, Groom, CM |

**OwnerInvoiceController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/owner/invoices` | Hóa đơn của tôi | — |
| GET | `/api/v1/owner/invoices/{invoiceId}` | Chi tiết hóa đơn | — |
| POST | `/api/v1/owner/invoices/{invoiceId}/vnpay` | Thanh toán qua VNPay Sandbox | — |
| GET | `/api/v1/owner/invoices/payment-history` | Lịch sử thanh toán | — |

**OwnerPaymentController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| POST | `/api/v1/owner/payments/medical-records/{recordId}/vnpay` | Thanh toán bệnh án qua VNPay | — |

**OwnerTrainingController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/owner/training/horses/{horseId}/progress` | Tiến độ tập của ngựa | — |
| GET | `/api/v1/owner/training/my-horses` | Ngựa của tôi | — |

**RaceProposalController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/race-proposals` | Danh sách đề xuất thi đấu | HLV, BS, CM |
| GET | `/api/v1/race-proposals/{id}` | Chi tiết đề xuất thi đấu | HLV, BS, CM |
| PUT | `/api/v1/race-proposals/{id}/owner-response` | Chủ ngựa phản hồi đề xuất | — |

**RegistrationAttachmentController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/horses/{horseId}/registration-attachments` | Danh sách giấy tờ hồ sơ | CM |
| POST | `/api/v1/horses/{horseId}/registration-attachments` | Đính kèm giấy tờ hồ sơ | — |
| DELETE | `/api/v1/horses/{horseId}/registration-attachments/{attachmentId}` | Gỡ giấy tờ hồ sơ | — |
| GET | `/api/v1/horses/registration-attachments/{attachmentId}/file` | Xem hoặc tải giấy tờ | CM |

**VaccinationController**

| Method | Endpoint | Chức năng | Vai trò khác |
|---|---|---|---|
| GET | `/api/v1/vaccinations` | Lịch tiêm phòng / tẩy giun | BS, CM, HLV, Groom |

## Tóm tắt số lượng

| Vai trò | Endpoint có quyền | Chỉ riêng vai trò |
|---|---|---|
| Admin (Club Manager) | 65 | 30 |
| HLV (Trainer) | 45 | 10 |
| Chủ ngựa (Horse Owner) | 24 | 11 |
| Dùng chung (công khai + mọi vai trò đăng nhập) | 19 | — |
