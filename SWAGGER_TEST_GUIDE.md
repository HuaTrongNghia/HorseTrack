# Hướng dẫn test API HorseTrack bằng Swagger

Swagger UI: http://localhost:8080/swagger-ui/index.html

Tổng cộng **115 endpoint**, chia thành 22 nhóm. Một số endpoint có thêm bản sao dưới `/api/...` (không có `v1`), ví dụ `/api/medical/vet-lock`. Hai bản chạy giống hệt nhau, chỉ cần test bản `/api/v1/...`.

---

## 0. Chuẩn bị chung (bắt buộc đọc trước)

### 0.1 Cách lấy token và "Authorize"

1. Mở `POST /api/v1/auth/login` → **Try it out** → nhập body → **Execute**.
2. Copy giá trị `data.accessToken` trong response (chỉ copy chuỗi token, **không** kèm chữ `Bearer`).
3. Bấm nút **Authorize** (ổ khóa, góc trên bên phải) → dán token → **Authorize** → **Close**.
4. Muốn đổi sang tài khoản khác: **Authorize** → **Logout** → dán token mới.

Access token chỉ sống **15 phút**. Nếu bị 401 "Token đã hết hạn" thì login lại hoặc dùng `/auth/refresh` (mục 1.4).

### 0.2 Các tài khoản (role) cần có

Quyền được kiểm tra theo **đúng chuỗi** `role_name` trong bảng `Users`:

| Role | Dùng để test | Cách có tài khoản |
|---|---|---|
| `Club Manager` | Tạo nhân viên, duyệt bệnh án, xem chi tiết giáo án | Phải có sẵn trong DB (API không tạo được) |
| `Veterinarian` | Tạo bệnh án, tạo thực đơn cho ngựa bị khóa | Club Manager tạo qua `/auth/staff/create` |
| `Warehouse Manager` | Quản lý kho (12A), đơn mua hàng (12B), cấp vật tư cho Groom (12C), kiểm kê (12D) | Club Manager tạo qua `/auth/staff/create` |
| `Groom` | Công việc chăm sóc, lịch tập | Club Manager tạo qua `/auth/staff/create` |
| `Trainer` | Lịch tập, thực hiện buổi tập | Club Manager tạo qua `/auth/staff/create` |
| `Horse Owner` | Đăng ký ngựa, xem tiến độ, thanh toán | Tự đăng ký qua `/auth/register-owner` |

### 0.3 Cách đọc kết quả

Mọi response đều có dạng:

```json
{ "success": true, "message": "...", "data": ... }
```

| HTTP | Ý nghĩa trong hệ thống này |
|---|---|
| 200 / 201 | Thành công |
| 400 | Sai dữ liệu đầu vào (`data` liệt kê từng field lỗi) hoặc lỗi nghiệp vụ |
| 401 | Chưa Authorize, token sai/hết hạn/đã logout, hoặc sai mật khẩu khi login |
| 403 | Đúng token nhưng **sai role**, hoặc vi phạm luật nghiệp vụ (không phải chủ ngựa...) |
| 404 | Không tìm thấy (chỉ phần Nutrition dùng 404) |
| 500 | `"Lỗi máy chủ: ..."`. **Chú ý:** nhiều lỗi nghiệp vụ như "Horse not found" hiện cũng trả 500 (xem L4) |

### 0.4 Thứ tự test khuyến nghị

Các chức năng phụ thuộc dữ liệu của nhau, nên test theo thứ tự:

```
Auth (1) → Account (2) → Stall (4) → Horse (3) → Nutrition (5)
       → Training (6, 7, 8) → Care (9) → Medical (10 → 13) → Mock (14 → 17)
```

---

### 0.5 Quy tắc tên đăng nhập và mật khẩu

Áp dụng khi **tạo tài khoản mới** (1.1, 1.3) và khi **đặt mật khẩu mới** (1.7, 2.2):

| | Quy tắc | Ví dụ hợp lệ | Ví dụ không hợp lệ |
|---|---|---|---|
| Tên đăng nhập | 4–30 ký tự; chỉ chữ không dấu, chữ số, dấu chấm `.`, gạch dưới `_` | `owner01`, `nguyen.van_a` | `abc`, `toàn`, `Toan ne`, `user-01` |
| Mật khẩu | 8–64 ký tự; ít nhất 1 chữ cái và 1 chữ số; không có khoảng trắng | `owner123`, `Owner@123` | `123456`, `abcdefgh`, `abc 12345` |

- **Đăng nhập (1.2) không áp quy tắc này**, nên các tài khoản tạo trước đó (ví dụ username có dấu, mật khẩu `123456`) vẫn đăng nhập được bình thường.
- Khi đổi mật khẩu, `oldPassword` không bị kiểm tra theo quy tắc mới.

### 0.6 Cấu hình gửi email (OTP, tài khoản nhân viên)

Server đọc tài khoản email từ **biến môi trường**, không ghi trong code:

| Biến | Ý nghĩa | Mặc định |
|---|---|---|
| `MAIL_USERNAME` | Địa chỉ Gmail dùng để gửi | (trống → chế độ dev) |
| `MAIL_PASSWORD` | **App Password** 16 ký tự của Gmail (không phải mật khẩu Gmail) | (trống) |
| `MAIL_HOST`, `MAIL_PORT` | Máy chủ SMTP | `smtp.gmail.com`, `587` |
| `MAIL_FROM_NAME` | Tên người gửi hiển thị | `HorseTrack EquineCare` |

- **Tạo App Password Gmail:** tài khoản Google → Bảo mật → bật **Xác minh 2 bước** → mục **Mật khẩu ứng dụng** → tạo mật khẩu cho "Mail".
- **Đặt biến trong IntelliJ:** Run → Edit Configurations… → `EquineCareApplication` → Environment variables: `MAIL_USERNAME=xxx@gmail.com;MAIL_PASSWORD=abcdefghijklmnop`.
- **Chế độ dev** (khi chưa đặt `MAIL_USERNAME`): không gửi email thật. Nội dung email, kể cả mã OTP, được in trong console server ở khối `===== [DEV] EMAIL KHÔNG GỬI THẬT =====`. Chỉ dùng để test, không dùng khi triển khai.
- **Quy tắc OTP:** 6 chữ số, hiệu lực **5 phút (300 giây)**, **dùng 1 lần**; yêu cầu mã mới thì mã cũ mất hiệu lực; nhập sai **5 lần** thì mã bị hủy; giữa 2 lần yêu cầu phải chờ **30 giây**. Mã lưu trong RAM nên **restart server thì mã cũ mất**.

---

## 1. Authentication — `/api/v1/auth` (không cần token, trừ 1.3 và 1.5)

### 1.1 `POST /api/v1/auth/register-owner` — Chủ ngựa tự đăng ký

```json
{
  "username": "owner01",
  "password": "owner123",
  "fullName": "Nguyen Van Owner",
  "email": "owner01@test.com",
  "phone": "0901234567"
}
```

- **Mong đợi:** `201`, message "Tạo tài khoản chủ ngựa thành công!".
- **Test lỗi:**
  - Gửi lại y hệt → `400` "Tên đăng nhập đã tồn tại."
  - Đổi username nhưng giữ email → `400` "Email đã được sử dụng."
  - Số điện thoại đã có tài khoản khác dùng (GEN-01) → `400` "Số điện thoại đã được sử dụng." (áp dụng cả khi tạo nhân viên ở 1.3).
  - Bỏ trống `fullName` hoặc `email: "abc"` → `400`, `data` chỉ ra field lỗi.
  - Mật khẩu yếu: dưới 8 ký tự (`"abc1234"`), không có số (`"abcdefgh"`), không có chữ (`"12345678"`), hoặc có dấu cách → `400` "Mật khẩu phải dài 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số, không chứa khoảng trắng".
  - Tên đăng nhập không hợp lệ: dưới 4 hoặc trên 30 ký tự, có dấu tiếng Việt (`"toàn"`), có dấu cách (`"Toan ne"`), hoặc có ký tự khác ngoài chữ, số, `.`, `_` (`"user-01"`) → `400`.

### 1.2 `POST /api/v1/auth/login` — Đăng nhập

```json
{ "username": "owner01", "password": "owner123" }
```

- **Mong đợi:** `200`. `data` gồm `accessToken`, `refreshToken`, `expiresIn` (900), `role`, `userId`.
- **Ghi lại `userId`** của owner. Mục 3.1 và 3.2 sẽ cần nó.
- **Test lỗi:** sai mật khẩu → `401` "Tên đăng nhập hoặc mật khẩu không chính xác".

### 1.3 `POST /api/v1/auth/staff/create` — Club Manager tạo nhân viên

Cần **Authorize bằng token Club Manager**.

```json
{
  "username": "groom01",
  "password": "groom123",
  "fullName": "Tran Van Groom",
  "email": "groom01@test.com",
  "phone": "0907654321",
  "roleName": "Groom",
  "licenseNumber": null,
  "specialization": "Khu A"
}
```

- `roleName` chỉ nhận `Trainer`, `Veterinarian`, `Groom` hoặc `Warehouse Manager`.
- Lặp lại để tạo `vet01` (`"roleName": "Veterinarian"`, `"licenseNumber": "VET-001"`) và `trainer01` (`"roleName": "Trainer"`).
- **Mong đợi:** `201` "Tạo tài khoản nhân viên thành công! Đã gửi tên đăng nhập và mật khẩu tới groom01@test.com."
- Sau khi lưu tài khoản, hệ thống **gửi email** cho nhân viên gồm vai trò, tên đăng nhập và mật khẩu, kèm lời nhắc đổi mật khẩu sau lần đăng nhập đầu.
  - Chế độ dev: message ghi "(Chế độ dev: email chưa gửi thật...)", nội dung email in trong console server.
  - SMTP lỗi: tài khoản **vẫn được tạo**, message báo "KHÔNG gửi được email..." để Club Manager báo thông tin cho nhân viên theo cách khác.
- **Test lỗi:**
  - Không Authorize → `401`.
  - Authorize bằng owner → `403`.
  - `"roleName": "Head Trainer"` → `400` "Role must be Trainer, Veterinarian, Groom, or Warehouse Manager".

### 1.4 `POST /api/v1/auth/refresh` — Làm mới token

```json
{ "refreshToken": "<refreshToken lấy từ login>" }
```

- **Mong đợi:** `200`, trả về cặp `accessToken` và `refreshToken` **mới**.
- **Test lỗi:** gửi lại đúng refresh token cũ lần nữa → bị từ chối (`401`), vì token cũ đã bị thu hồi.

### 1.5 `POST /api/v1/auth/logout` — Đăng xuất

Đang Authorize bằng access token muốn chặn, gửi body:

```json
{ "refreshToken": "<refreshToken hiện tại>" }
```

- **Mong đợi:** `200` "Đăng xuất thành công".
- **Kiểm tra:** gọi `GET /api/v1/account/my-profile` với token cũ → `401` "Token đã bị thu hồi do đăng xuất...".
- Lưu ý: danh sách token bị chặn nằm trong RAM, nên **restart server thì token cũ dùng lại được** (nếu chưa hết 15 phút).

### 1.6 `POST /api/v1/auth/forgot-password` — Quên mật khẩu: gửi OTP

Không cần token.

```json
{ "email": "groom01@horsetrack.com" }
```

- **Mong đợi:** `200` "Nếu email đã đăng ký, mã OTP đã được gửi tới email đó. Mã có hiệu lực 5 phút."
- Thông báo **giống hệt** cho email có và không có tài khoản, để không lộ email nào đã đăng ký. Email không tồn tại thì không có email nào được gửi.
- Gửi lại trong vòng 30 giây → `400` "Vui lòng chờ ... giây". Sau đó gửi lại thì có **mã mới**, mã cũ mất hiệu lực.
- Chế độ dev: lấy mã trong console server.
- SMTP lỗi → `503` "Không gửi được email...".

### 1.7 `POST /api/v1/auth/reset-password` — Quên mật khẩu: đặt mật khẩu mới

Không cần token.

```json
{ "email": "groom01@horsetrack.com", "otp": "482913", "newPassword": "Moi123456", "confirmNewPassword": "Moi123456" }
```

- **Mong đợi:** `200` "Đặt lại mật khẩu thành công...". Login bằng mật khẩu mới được; **mọi refresh token cũ bị thu hồi** (đăng xuất mọi thiết bị).
- **Test lỗi:**
  - Sai OTP → `400` kèm số lần thử còn lại; sai 5 lần → mã bị hủy.
  - Quá 5 phút → `400` "Mã OTP đã hết hạn".
  - Dùng lại cùng mã lần 2 → `400` "Mã OTP không tồn tại hoặc đã được sử dụng".
  - `confirmNewPassword` không khớp, hoặc `newPassword` dưới 6 ký tự → `400`.

---

## 2. Account — `/api/v1/account` (mọi role đã login)

### 2.1 `GET /api/v1/account/my-profile`

- **Mong đợi:** `200`, thông tin của user đang đăng nhập (`username`, `roleName`...).
- **Test lỗi:** không Authorize → `401`.

### 2.2 Đổi mật khẩu bằng OTP (2 bước)

**Bước 1: `POST /api/v1/account/change-password/otp`** (không có body)

- Gửi mã OTP 6 số tới email của tài khoản đang đăng nhập.
- **Mong đợi:** `200` "Đã gửi mã OTP tới gr*****@horsetrack.com. Mã có hiệu lực 5 phút." (email được che bớt).
- Chế độ dev (chưa cấu hình SMTP, xem 0.6): lấy mã trong console server, ở khối `[DEV] EMAIL KHÔNG GỬI THẬT`.
- Gọi lại trong vòng 30 giây → `400` "Bạn vừa yêu cầu mã OTP. Vui lòng chờ ... giây". Sau 30 giây gọi lại thì nhận mã mới, **mã cũ mất hiệu lực**.

**Bước 2: `PUT /api/v1/account/change-password`**

```json
{ "oldPassword": "owner123", "newPassword": "owner456", "confirmNewPassword": "owner456", "otp": "482913" }
```

- **Mong đợi:** `200` "Đổi mật khẩu thành công. Các thiết bị khác sẽ phải đăng nhập lại." Sau đó login bằng mật khẩu mới phải được; refresh token cũ (1.4) không dùng được nữa.
- **Test lỗi:**
  - Thiếu `otp`, hoặc `otp` không đủ 6 chữ số → `400`.
  - Sai mật khẩu cũ → `400` "Mật khẩu cũ không đúng." (mã OTP **chưa bị dùng**, sửa mật khẩu cũ rồi gửi lại là được).
  - Mật khẩu mới trùng mật khẩu cũ, hoặc xác nhận không khớp → `400`.
  - Sai OTP → `400` "Mã OTP không đúng. Bạn còn N lần thử."; sai 5 lần → mã bị hủy.
  - Quá 5 phút → `400` "Mã OTP đã hết hạn".
  - Dùng lại mã đã đổi thành công → `400` (mã chỉ dùng 1 lần).

---

## 2B. User Management — `/api/v1/users` & `/api/v1/audit-logs` (Chỉ `Club Manager`)

Phục vụ màn hình **Người dùng & phân quyền** của FE (5.1, 5.2, 5.3, BR_06, BR_10).

### 2B.1 `GET /api/v1/users?page=0&size=20&search=&role=&status=` — Danh sách người dùng (phân trang & lọc)
- **Tham số tùy chọn:**
  - `search`: tìm kiếm không phân biệt hoa thường theo họ tên, username, email.
  - `role`: lọc theo vai trò (`Club Manager`, `Trainer`, `Veterinarian`, `Groom`, `Warehouse Manager`, `Horse Owner` hoặc dạng FE `HeadTrainer`, `WarehouseManager`...).
  - `status`: `ACTIVE` (hoạt động) hoặc `LOCKED` (bị khóa).
- **Mong đợi:** `200`, trả về danh sách `users` kèm `currentPage`, `totalPages`, `totalElements`. Mỗi user có đầy đủ: `id`, `username`, `fullName`, `email`, `role`, `status`, `openTasks`, `horsesOwned`, `specialization`, `licenseNumber`.

### 2B.2 `GET /api/v1/users/all` — Danh sách toàn bộ người dùng (không phân trang)
- **Mong đợi:** `200`, danh sách phẳng tất cả user để FE nạp nhanh vào bảng dữ liệu.

### 2B.3 `GET /api/v1/users/{id}` — Chi tiết người dùng theo ID
- **Mong đợi:** `200`, thông tin tài khoản và profile chuyên môn tương ứng.

### 2B.4 `PUT /api/v1/users/{id}/lock` — Khóa tài khoản người dùng (BR_10)
```json
{
  "reason": "Vi phạm quy định an toàn chuồng trại nhiều lần"
}
```
- **Quy tắc:**
  - Lý do tối thiểu 10 ký tự.
  - Không được tự khóa tài khoản của chính mình (Club Manager hiện tại).
  - Không thể khóa tài khoản Club Manager khác.
  - Tự động thu hồi phiên làm việc (thu hồi refresh tokens) và ghi `Audit_Logs`.
- **Mong đợi:** `200`, `status = "LOCKED"`.

### 2B.5 `PUT /api/v1/users/{id}/unlock` — Mở khóa tài khoản (BR_10)
```json
{
  "reason": "Đã hoàn thành kiểm điểm và cam kết tuân thủ quy chế"
}
```
- **Mong đợi:** `200`, `status = "ACTIVE"`, ghi `Audit_Logs`.

### 2B.6 `PUT /api/v1/users/{id}/role` — Đổi vai trò người dùng (BR_06)
```json
{
  "roleName": "Trainer",
  "reason": "Bổ nhiệm Huấn luyện viên phụ trách nhóm ngựa khu B"
}
```
- **Quy tắc BR_06:**
  - Nếu chuyển từ `Horse Owner` sang vai trò nhân sự: chặn nếu người này đang sở hữu ngựa trong CLB (`horsesOwned > 0`).
  - Nếu chuyển từ nhân sự sang `Horse Owner`: chặn do xung đột lợi ích.
  - Tự động tạo hồ sơ profile tương ứng (`TrainerProfile`, `VeterinarianProfile`...) nếu chưa có.
  - Thu hồi refresh token để người dùng đăng nhập lại nhận vai trò mới và ghi `Audit_Logs`.
- **Mong đợi:** `200`, `roleName` và `role` được cập nhật.

### 2B.7 `GET /api/v1/audit-logs` — Xem nhật ký kiểm toán hệ thống (BR_10)
- **Mong đợi:** `200`, danh sách các hành động gần nhất trên hệ thống (ai thực hiện, thao tác gì, bảng nào, giá trị cũ/mới, IP, thời gian).

---

## 3. Horse — `/api/v1/horses`

Mỗi API có phân quyền riêng, xem bảng ở mục 18.

### 3.1 `POST /api/v1/horses/register` — Đăng ký ngựa

```json
{
  "horseName": "Thunder Test",
  "dob": "2021-05-10",
  "gender": "Stallion",
  "breed": "Thoroughbred",
  "pedigree": "Sire X - Dam Y",
  "weightKg": 480.5,
  "ownerId": 0
}
```

- Thay `ownerId` bằng `userId` của owner (lấy ở mục 1.2).
- `gender` chỉ nhận `Colt`, `Filly`, `Stallion`, `Mare` hoặc `Gelding`.
- **Mong đợi:** `201`. **Ghi lại `data.horseId`**. Ngựa mới có `approvalStatus = "PENDING"`, `stallId` và `stallCode` đều `null` (đang chờ Club Manager duyệt).
- **Test lỗi:**
  - `dob` ở tương lai, `weightKg: 0` hoặc `gender: "Male"` → `400`.
  - Trùng tên ngựa của cùng owner → `500` "Horse with this name already exists...".
  - `ownerId` là id của nhân viên → `500` "User is not a Horse Owner".
  - Horse Owner gửi `ownerId` của người khác → `403` "Chủ ngựa chỉ được đăng ký ngựa cho chính mình...".
  - Trainer, Vet hoặc Groom gọi → `403`.

### 3.2 `GET /api/v1/horses/my-horses/{ownerId}`

- Horse Owner chỉ xem được của chính mình. `ownerId` của người khác → `403`. Club Manager xem được của bất kỳ ai.

- **Mong đợi:** `200`, danh sách **mọi** ngựa của owner, gồm cả ngựa đang chờ duyệt. Mỗi ngựa có:
  - `approvalStatus`: `PENDING` (chờ duyệt) hoặc `APPROVED` (đã duyệt).
  - `stallId`, `stallCode`: chuồng được cấp khi duyệt, `null` nếu chưa duyệt.
- **Kiểm tra luồng duyệt:** owner đăng ký (3.1) → gọi 3.2 thấy `PENDING` → Club Manager duyệt (3.5) → gọi lại 3.2 thấy `APPROVED` và `stallCode` (ví dụ `"A-03"`).
- **Test lỗi:** `ownerId` không tồn tại → `500` "Owner not found".

### 3.3 `GET /api/v1/horses?page=0&size=10&status=`

Chỉ nhân viên CLB (Club Manager, Trainer, Veterinarian, Groom). Owner gọi → `403`.

- Bỏ trống `status` → tất cả ngựa.
- `status=PENDING` → chỉ ngựa chưa có chuồng; ngựa vừa đăng ký ở 3.1 phải có trong đây.
- `status=APPROVED` → chỉ ngựa đã có chuồng.
- `data` gồm `horses`, `currentPage`, `totalPages`, `totalElements`. Mỗi ngựa có thêm `assignedTrainerId`, `assignedTrainerName`.
- **Trainer:** chỉ thấy ngựa được phân công cho mình (3.8); `status=PENDING` hoặc `REJECTED` trả danh sách rỗng.

### 3.4 `GET /api/v1/horses/{id}`

Club Manager, Veterinarian, Groom xem được mọi ngựa. Horse Owner xem ngựa của người khác → `403` "Bạn không phải chủ của ngựa này".
- **Phạm vi Trainer:** Trainer chỉ nhận ngựa được Club Manager phân công cho mình (3.8). Ngựa ngoài phạm vi → `403` "Bạn không được Club Manager phân công phụ trách ngựa ...".

- **Mong đợi:** `200`, chi tiết ngựa.
- `id` không tồn tại → `500` "Horse not found".

### 3.5 Hồ sơ đăng ký ngựa: duyệt, từ chối, nộp lại

Mỗi lần chủ ngựa nộp (3.1) hoặc nộp lại tạo một **hồ sơ** trong bảng `Horse_Registrations`, mã `REG-0001`. Luồng: `PENDING` → `APPROVED` (cấp chuồng), hoặc `REJECTED` (kèm lý do, có thể cho nộp lại) → nộp lại → `PENDING` …

Khi đăng ký (3.1), có thể gửi thêm các field tùy chọn: `color` (màu lông), `heightHands` (chiều cao, đơn vị hand, ví dụ `15.2`), `passportNo` (số hộ chiếu, không được trùng giữa các ngựa), `healthCertIssuer`, `healthCertExpiry`, `note`.

**a) `PUT /api/v1/horses/{id}/approve`: duyệt và cấp chuồng (`Club Manager`)**

```json
{
  "stallId": 1,
  "checks": [
    { "checkKey": "IDENTITY", "passed": true },
    { "checkKey": "HEALTH", "passed": true, "detail": "Giấy còn hạn đến 2027" },
    { "checkKey": "OWNERSHIP", "passed": true }
  ],
  "note": "Hồ sơ đầy đủ"
}
```

- `checks` tùy chọn. Nếu gửi thì phải đủ 3 mục `IDENTITY`, `HEALTH`, `OWNERSHIP` và **tất cả đều đạt** (SRS). Có mục `passed: false` → `400`, hãy từ chối thay vì duyệt.
- Lấy `stallId` có `currentHorseId = null` từ mục 4.1.
- **Mong đợi:** `200`, `approvalStatus = "APPROVED"`, có `stallCode`.
- **Test lỗi:** hồ sơ không ở trạng thái chờ duyệt (đã duyệt hoặc đã từ chối) → `400`; chuồng đã có ngựa → `400`.
- **Chuồng phải `CLEAN` (F1-05):** chuồng `NEEDS_CLEANING` hoặc `MAINTENANCE` → `400` "Chỉ xếp ngựa vào chuồng CLEAN". Duyệt và từ chối hồ sơ đều được ghi Audit Log (`APPROVE_HORSE`, `REJECT_HORSE`).

**b) `PUT /api/v1/horses/{id}/reject`: từ chối (`Club Manager`)**

```json
{
  "reason": "Thiếu giấy chứng nhận sức khỏe còn hiệu lực",
  "allowResubmit": true,
  "checks": [ { "checkKey": "HEALTH", "passed": false, "detail": "Giấy hết hạn" } ]
}
```

- `reason` bắt buộc, dài 10–500 ký tự (SRS). `allowResubmit: true` thì chủ ngựa được sửa và nộp lại.
- **Mong đợi:** `200`, `approvalStatus = "REJECTED"`, `rejectReason`, `allowResubmit`. Chủ ngựa thấy trạng thái này ở "Ngựa của tôi" (3.2, 8.1).
- **Test lỗi:** `reason` dưới 10 ký tự → `400`.

**c) `PUT /api/v1/horses/{id}/resubmit`: chủ ngựa nộp lại (`Horse Owner`, chỉ ngựa của mình)**

```json
{ "healthCertIssuer": "Chi cục Thú y TP.HCM", "healthCertExpiry": "2027-06-30", "note": "Đã bổ sung giấy sức khỏe mới" }
```

- Field nào gửi lên mới được cập nhật: `weightKg`, `pedigree`, `color`, `heightHands`, `passportNo`, `healthCertIssuer`, `healthCertExpiry`.
- **Mong đợi:** `200`, `approvalStatus` quay về `"PENDING"`, có hồ sơ mới (`registrationCode` mới).
- **Test lỗi:**
  - Hồ sơ bị từ chối với `allowResubmit: false` → `400` "Quản lý CLB không cho phép nộp lại hồ sơ này".
  - Hồ sơ đang chờ duyệt hoặc đã duyệt → `400`.
  - Nộp lại ngựa của người khác → `403`.

**d) `GET /api/v1/horses/{id}/registrations`: lịch sử hồ sơ (`Club Manager`, `Horse Owner` chỉ ngựa của mình)**

- **Mong đợi:** `200`, mỗi lần nộp gồm `registrationCode`, `submittedAt`, `status`, `ownerNote`, `decisionNote`, `decidedByName`, `decidedAt` và `checks`, mới nhất trước.

**e) Lọc danh sách (3.3):** `GET /api/v1/horses?status=PENDING | APPROVED | REJECTED`.

### 3.6 `PUT /api/v1/horses/{id}` — Cập nhật ngựa

```json
{ "weightKg": 490, "healthStatus": "HEALTHY", "isLocked": false }
```

- Mọi field đều tùy chọn; chỉ field nào gửi lên mới được cập nhật.
- `healthStatus` chỉ nhận `HEALTHY`, `WATCH`, `INJURED` hoặc `QUARANTINE` (không phân biệt hoa thường).
- **Phân quyền:** Club Manager, Trainer hoặc Veterinarian được sửa. Groom hoặc Owner gọi → `403`. Trainer chỉ sửa được ngựa được phân công cho mình.
- **Chỉ Veterinarian được đổi `isLocked`.** Trainer hoặc Club Manager gửi `"isLocked"` khác giá trị hiện tại → `403` "Chỉ Bác sĩ thú y mới được thay đổi trạng thái khóa huấn luyện (isLocked)". Gửi `isLocked` bằng đúng giá trị hiện tại thì không bị chặn.
- **Chỉ Veterinarian được đổi `healthStatus`.** Trainer hoặc Club Manager gửi `healthStatus` khác giá trị hiện tại → `403` "Chỉ Bác sĩ thú y mới được thay đổi trạng thái sức khỏe (healthStatus)". Trainer và Club Manager chỉ còn sửa được `weightKg`.
- `healthStatus` sai giá trị (ví dụ `"Need_Check"`) → `400` thay vì `500` như trước.

### 3.7 `GET /api/v1/horses/available-for-training` (`Trainer`, `Club Manager`)

- **Mong đợi:** `200`, danh sách ngựa có thể lên giáo án (id, tên, giống, cân nặng). Trainer chỉ thấy ngựa được phân công cho mình, chưa VetLock và chưa có giáo án `ACTIVE` / `PAUSED`.

### 3.8 `PUT /api/v1/horses/{id}/assign-trainer`: phân công HLV phụ trách (`Club Manager`)

```json
{ "trainerId": 24 }
```

- **Mong đợi:** `200` "Đã phân công HLV ... phụ trách ngựa ...", `data.assignedTrainerId` = `trainerId`. HLV nhận email thông báo; ghi Audit Log `ASSIGN_TRAINER`.
- **Đổi HLV:** gọi lại với `trainerId` khác. Giáo án `ACTIVE` / `PAUSED` của ngựa được **bàn giao** cho HLV mới; HLV cũ mất quyền xem ngựa đó.
- **Bắt buộc trước 5, 6, 9.0:** ngựa chưa phân công thì không Trainer nào xem, lập giáo án, lập khẩu phần hay giao care task được.
- **Test lỗi:**

| Trường hợp | Kết quả |
|---|---|
| Token Trainer, Groom, Owner | `403` |
| Thiếu `trainerId` | `400` (validation) |
| Ngựa chưa được duyệt (chưa có chuồng) | `400` "...chưa thể phân công HLV" |
| `trainerId` không phải Trainer (ví dụ Groom, Club Manager) | `400` "Chỉ được phân công nhân viên có vai trò Trainer..." |
| Tài khoản Trainer đã bị khóa | `400` |
| Phân công lại đúng HLV đang phụ trách | `400` "...đã được phân công cho ..." |
| `id` hoặc `trainerId` không tồn tại | `404` |

- **Kiểm tra phạm vi Trainer:** đăng nhập Trainer → `GET /horses` chỉ còn ngựa được phân công; `GET /horses/{id}` của ngựa khác → `403`.

---

## 4. Stall — `GET /api/v1/stalls` (mọi role đã login)

- **Mong đợi:** `200`, sơ đồ chuồng gồm `stallId`, `stallCode`, `barnZone`, `cleanStatus`, `currentHorseId`, `horseName`, `isLocked`.
- Dùng để tìm chuồng trống cho mục 3.5. Sau khi duyệt, gọi lại để thấy ngựa đã vào chuồng.

---

## 5. Nutrition — Thực đơn dinh dưỡng

### 5.1 `POST /api/v1/nutrition-plans` — Tạo thực đơn
> **Phạm vi Trainer:** Trainer gọi với ngựa không được Club Manager phân công cho mình (3.8) → `403`.

Chỉ `Trainer` hoặc `Veterinarian` được gọi.

```json
{
  "horseId": 1,
  "startDate": "2026-10-19",
  "endDate": "2026-10-31",
  "notes": "Thực đơn tăng sức bền",
  "details": [
    { "inventoryItemId": 1, "mealType": "MORNING", "quantity": 3.5 },
    { "inventoryItemId": 2, "mealType": "EVENING", "quantity": 2 }
  ]
}
```

- `inventoryItemId` phải có thật trong bảng `Inventory_Items`.
- `mealType` chỉ nhận `MORNING`, `AFTERNOON` hoặc `EVENING`.
- **Luật nghiệp vụ BR_03** (đây là phần quan trọng nhất để test):

| Ngựa (`isLocked`) | Người gọi | Kết quả mong đợi |
|---|---|---|
| `false` | Trainer | `201`, trả về `planId` |
| `false` | Veterinarian | `403` "Ngựa khỏe mạnh. Quyền lên thực đơn thuộc về Huấn luyện viên (Trainer)." |
| `true` | Trainer | `403` "Ngựa đang bị khóa y tế. Chỉ Bác sĩ Thú y..." |
| `true` | Veterinarian | `201` |
| bất kỳ | Groom / Owner | `403` (sai role) |

  Để đổi `isLocked` của ngựa, dùng mục 3.6 hoặc 10.1 / 10.2.

- **Test lỗi khác:**
  - `endDate` trước `startDate` → `400`.
  - `details: []` → `400`.
  - `horseId` không tồn tại → `404`.

### 5.2 `GET /api/v1/horses/{horseId}/nutrition-plan/today`
> **Phạm vi Trainer:** Trainer gọi với ngựa không được Club Manager phân công cho mình (3.8) → `403`.

- **Mong đợi:** `200`, danh sách món ăn hôm nay của ngựa (khi hôm nay nằm trong khoảng `startDate` – `endDate`).
- Không có thực đơn nào cho hôm nay → `200` với `data: []`.
- `horseId` không tồn tại → `404`.

---

## 6. Training Plan — `/api/v1/training-plans`

### 6.0 `POST /api/v1/training-plans` — Trainer tạo giáo án

Chỉ `Trainer` được gọi. Người đang đăng nhập sẽ được ghi là HLV phụ trách giáo án.

```json
{
  "horseId": 2,
  "planName": "Giáo án tăng sức bền tháng 11",
  "startDate": "2026-10-21",
  "endDate": "2026-11-30",
  "goal": "Tăng sức bền cho cự ly 1600m"
}
```

- **Mong đợi:** `201` "Tạo giáo án huấn luyện thành công", `data` là `planId`. Giáo án mới có trạng thái **`PENDING` (đang chờ)**, chỉ chuyển `ACTIVE` theo 7.2; DB tự sinh mã `plan_code` dạng `KH-0002`.
- **Ghi lại `planId`** để dùng cho 6.3 và 6.5.
- **Test lỗi:**

| Trường hợp | Kết quả |
|---|---|
| Token Groom, Owner hoặc Club Manager | `403` |
| Thiếu `planName`, hoặc `startDate` ở quá khứ | `400` (validation) |
| `endDate` trước `startDate` | `400` "Ngày kết thúc (endDate) không được nhỏ hơn..." |
| `horseId` không tồn tại | `404` |
| Ngựa chưa được duyệt (chưa có chuồng) | `400` "Ngựa chưa được duyệt và cấp chuồng..." |
| Ngựa không được phân công cho Trainer đang đăng nhập (3.8) | `403` "Bạn không được Club Manager phân công phụ trách ngựa ..." |
| Ngựa đang VetLock (`POST /medical/vet-lock` trước) | `400` "Ngựa đang bị khóa y tế (VetLock)..." |
| Tạo lần 2 cho cùng ngựa khi giáo án cũ còn `PENDING`, `ACTIVE` hoặc `PAUSED` | `400` "Ngựa này đang có một giáo án PENDING, ACTIVE hoặc PAUSED..." |

Sau khi tạo, ngựa sẽ biến mất khỏi `GET /api/v1/horses/available-for-training` (3.7), vì đã có giáo án chưa kết thúc (`PENDING` / `ACTIVE` / `PAUSED`).

**Vòng đời giáo án:**

| Trạng thái | Khi nào |
|---|---|
| `PENDING` (đang chờ) | Vừa tạo; hoặc sau khi bác sĩ mở khóa VetLock |
| `ACTIVE` (đang chạy) | Có ít nhất 1 buổi tập Groom đã **xác nhận** (7.2) **và** đã tới `startDate`. Xác nhận khi đã tới ngày thì chuyển ngay; chưa tới ngày thì hệ thống tự chuyển (kiểm tra mỗi 5 phút, `app.training.activate-check-ms`) |
| `PAUSED` | Ngựa bị VetLock khi giáo án đang chạy |
| `COMPLETED` | Buổi tập cuối cùng chưa kết thúc được hoàn thành |
| `CANCELLED` | Đã hủy |

### 6.1 `GET /api/v1/training-plans/dashboard-stats` (Club Manager, Trainer, Groom, Veterinarian)

- **Mong đợi:** `200`, gồm `totalActivePlans`, `todaysSessions`, `injuredHorses`. Trainer chỉ nhận số liệu của ngựa được phân công. Owner, Warehouse Manager → `403`.

### 6.2 `GET /api/v1/training-plans?status=ACTIVE&page=0&size=10` (Club Manager, Trainer, Groom, Veterinarian)

- **Mong đợi:** `200`, danh sách giáo án có `progress` (% buổi đã hoàn thành). Trainer chỉ thấy giáo án của ngựa được phân công. Owner, Warehouse Manager → `403`.
- **Ghi lại `planId`**.

### 6.3 `GET /api/v1/training-plans/{planId}`

Chỉ `Trainer` hoặc `Club Manager`.

- **Mong đợi:** `200`, chi tiết giáo án và danh sách `sessions`.
- **Test lỗi:** Groom gọi → `403`. Trainer xem giáo án của ngựa không được phân công cho mình → `403`. `planId` sai → `404`.
- 6.4 và 6.5 cũng trả `403` khi giáo án thuộc ngựa không được phân công cho Trainer đang đăng nhập.

### 6.4 `GET /api/v1/training-plans/assignable-staff?planId={planId}` — Groom có thể phân công

Chỉ `Trainer`. Theo SRS: chọn khu chuồng → ngựa trong khu → **Groom của khu đó**.

- `planId` bắt buộc. Hệ thống lấy khu chuồng (`barn_zone`) của ngựa trong giáo án, rồi trả về **chỉ những Groom đang hoạt động phụ trách đúng khu đó**. Khu của Groom lấy từ `Groom_Profiles.assigned_barn_zone`; so khớp không phân biệt hoa thường.
- **Mong đợi:** `200`, mỗi phần tử gồm `userId`, `fullName`, `roleName` (luôn là `Groom`), `barnZone`. **Ghi lại `userId`** để dùng ở 6.5.
- Ví dụ với dữ liệu hiện tại: giáo án của ngựa "Chieu Da" (chuồng `A-02`, khu "Khu A") trả về NghiaNgu, QuocTruong, test_maphu.
- **Test lỗi:**
  - Thiếu `planId` → `400`. `planId` không tồn tại → `404`.
  - Ngựa của giáo án chưa có chuồng → `400`.
  - Khu không có Groom nào → `200` với danh sách rỗng.

### 6.5 `POST /api/v1/training-plans/{planId}/sessions` — Phân công buổi tập

Chỉ `Trainer`.

```json
{
  "sessionDate": "2026-10-22T07:00:00",
  "activityType": "Chạy nước kiệu 2000m",
  "notes": "Tập nhẹ",
  "assignedToId": 14
}
```

- `assignedToId` lấy từ 6.4. `sessionDate` phải nằm trong thời gian giáo án và không ở quá khứ.
- **Mong đợi:** `201`. Buổi tập mới có trạng thái `PENDING`.
- **Test lỗi** (đều trả `400` kèm thông báo rõ lý do):
  - `assignedToId` là Trainer, bác sĩ, chủ ngựa… → "Chỉ được phân công buổi tập cho Groom".
  - Groom khác khu với ngựa (ví dụ `groom_01`, khu "Vệ sinh chuồng trại") → "Groom ... phụ trách khu '...', không phải khu '...' của ngựa ...".
  - Groom đã bị vô hiệu hóa → `400`.
  - Ngựa đang VetLock (BR-03) → "Ngựa ... đang bị khóa huấn luyện (VetLock), không thể xếp lịch tập".
  - `sessionDate` trước ngày bắt đầu hoặc sau ngày kết thúc giáo án → "Ngày tập ... nằm ngoài thời gian giáo án".
  - `sessionDate` ở quá khứ → `400`.
  - Giáo án đã `COMPLETED` hoặc `CANCELLED` → `400`.
  - `assignedToId` không tồn tại → `404`.
  - Groom đã có buổi tập khác trong khoảng ±60 phút (F21-05, BR34) → `400` "Groom ... đã có buổi tập lúc ..., trùng khung giờ 60 phút". Thời lượng chỉnh bằng `app.training.session-duration-minutes`.

---

## 7. Training Session — `/api/v1/training-sessions` (`Trainer` hoặc `Groom`)

### 7.1 `GET /api/v1/training-sessions/my-schedule?startDate=2026-10-19&endDate=2026-10-26`

- Login bằng đúng nhân viên được gán ở 6.5.
- **Mong đợi:** `200`, thấy buổi tập vừa tạo. **Ghi lại `sessionId`**.
- **Test lỗi:** thiếu `startDate` → `400`.

### 7.2 `PUT /api/v1/training-sessions/{sessionId}/confirm`: Groom xác nhận buổi tập (`Groom`)

Không cần body. Buổi tập HLV vừa phân công (6.5) ở trạng thái `PENDING`; Groom được gán xác nhận thì chuyển `CONFIRMED`.

- **Mong đợi:** `200`, `data` là trạng thái giáo án sau khi xác nhận:
  - `"ACTIVE"` + "Đã xác nhận buổi tập. Giáo án đang chạy." khi đã tới `startDate` của giáo án.
  - `"PENDING"` + "...Giáo án sẽ chạy khi tới ngày bắt đầu." khi chưa tới ngày; tới ngày hệ thống tự chuyển `ACTIVE`.
- **Test lỗi:**

| Trường hợp | Kết quả |
|---|---|
| Groom khác (không được gán) hoặc token Trainer | `403` |
| Xác nhận lần 2 (buổi đã `CONFIRMED`, `COMPLETED`, `CANCELLED`) | `400` "Chỉ xác nhận được buổi tập đang chờ xác nhận..." |
| Buổi tập đã qua giờ | `400` "...đã qua giờ, không thể xác nhận..." |
| Ngựa đang VetLock | `400` |
| `sessionId` không tồn tại | `404` |

### 7.3 `PUT /api/v1/training-sessions/{sessionId}/execute`

```json
{ "status": "COMPLETED", "notes": "Ngựa chạy tốt" }
```

- `status` chỉ nhận `COMPLETED` hoặc `CANCELLED`.
- **Hoàn thành (`COMPLETED`) chỉ khi buổi đã được xác nhận (7.2) và giáo án đang `ACTIVE`. Hủy (`CANCELLED`) được cả buổi `PENDING` lẫn `CONFIRMED`.**
- **Mong đợi:** `200`. Nếu không còn buổi `PENDING` / `CONFIRMED` nào, giáo án tự chuyển sang `COMPLETED` (kiểm tra lại bằng 6.2).
- **Test lỗi:**
  - `COMPLETED` khi buổi còn `PENDING` (chưa xác nhận) → `400` "Buổi tập chưa được xác nhận...".
  - `COMPLETED` khi giáo án còn `PENDING` (chưa tới ngày bắt đầu) → `400`.
  - Thực hiện lại cùng buổi → `400` "Không thể cập nhật buổi tập đang ở trạng thái COMPLETED."
  - Nhân viên khác (không được gán) gọi → `403`.
  - `"status": "CANCELLED"` mà không có `notes` → `400` "Phải ghi lý do (notes) khi hủy buổi tập".
  - `"status": "COMPLETED"` khi ngựa đang VetLock (F22-01) → `400`. Chỉ được hủy buổi tập.

---

## 8. Owner Training — `/api/v1/owner/training` (`Horse Owner`)

### 8.1 `GET /api/v1/owner/training/my-horses`

- **Mong đợi:** `200`, mọi ngựa của owner đang login (lấy theo token, không cần truyền id), gồm `horseId`, `horseName`, `approvalStatus` (`PENDING`/`APPROVED`), `stallId`, `stallCode`. Trạng thái giống hệt 3.2.

### 8.2 `GET /api/v1/owner/training/horses/{horseId}/progress`

- **Mong đợi:** `200`, giáo án `ACTIVE` mới nhất kèm `progress` và lịch sử `sessions`.
- **Test lỗi:**
  - Xem ngựa của owner khác → `403`.
  - Ngựa chưa có giáo án → `500` "Ngựa hiện chưa có giáo án nào".

---

## 9. Care Task — `/api/v1/care-tasks` (bảng `Daily_Tasks`)

Luồng: **Trainer giao việc** (9.0) → **Groom xem việc hôm nay** (9.1) → **Groom hoàn thành** (9.2).

### 9.0 `POST /api/v1/care-tasks` — Trainer giao việc cho Groom
> **Phạm vi Trainer:** Trainer gọi với ngựa không được Club Manager phân công cho mình (3.8) → `403`.

Chỉ `Trainer` được gọi. Lấy `groomId` từ `GET /training-plans/assignable-staff?planId=...` (6.4): danh sách đó chỉ gồm Groom cùng khu chuồng với ngựa.

```json
{
  "horseId": 2,
  "groomId": 2,
  "taskType": "FEED",
  "title": "Cho ăn bữa sáng",
  "taskDate": "2026-10-20",
  "scheduledTime": "07:30:00",
  "instructions": "Cho ăn theo thực đơn dinh dưỡng hôm nay"
}
```

- `taskType` chỉ nhận `FEED`, `CLEAN`, `GROOM`, `WALK`, `COOL`, `HOOF`, `MEDICATION` hoặc `OTHER`.
- `taskDate` nên để **hôm nay**, để Groom thấy ngay ở 9.1. `scheduledTime` có định dạng `HH:mm:ss`.
- **Mong đợi:** `201` "Đã giao việc chăm sóc cho Groom thành công". `data` gồm `taskId`, `taskCode` (DB tự sinh dạng `TK-0001`), `status = "TODO"`, kèm chuồng của ngựa (`stallCode`, `stallZone`).
- **Test lỗi:**

| Trường hợp | Kết quả |
|---|---|
| Token Groom, Owner, Vet hoặc Club Manager | `403` |
| `taskType: "FEEDING"`, thiếu `scheduledTime`, hoặc `taskDate` ở quá khứ | `400` (validation) |
| `horseId` hoặc `groomId` không tồn tại | `404` |
| Ngựa chưa được duyệt (chưa có chuồng) | `400` "Ngựa chưa được duyệt và cấp chuồng..." |
| `groomId` là id của Trainer hoặc Vet | `400` "Chỉ được giao việc chăm sóc cho nhân viên có vai trò Groom." |
| `groomId` là Groom phụ trách khu khác với khu chuồng của ngựa (F4-03) | `400` "Groom ... phụ trách khu ..., không phải khu ... của ngựa ..." |

### 9.1 `GET /api/v1/care-tasks/my-tasks` (`Groom`)

- Login bằng đúng Groom được giao ở 9.0.
- **Mong đợi:** `200`, danh sách việc có `taskDate` là hôm nay, sắp theo `scheduledTime`. **Ghi lại `taskId`**.

### 9.3 `GET /api/v1/care-tasks/overdue`: việc trễ hạn (`Club Manager`, BR_05)

- Hệ thống quét **mỗi 5 phút**. Việc chưa xong (`TODO` hoặc `IN_PROGRESS`) quá **45 phút** sau giờ hẹn thì được đánh dấu trễ (`warningNote`, `overdue = true`), và các Club Manager nhận **một email tổng hợp**. Mỗi việc chỉ bị cảnh báo một lần.
- **Mong đợi:** `200`, danh sách việc đang trễ. `GET /care-tasks/my-tasks` của Groom cũng có `overdue` và `warningNote`.
- **Cách test nhanh:** giao việc (9.0) với `taskDate` là hôm nay và `scheduledTime` sớm hơn hiện tại ít nhất 45 phút, chờ tối đa 5 phút rồi gọi API này.

### 9.2 `PUT /api/v1/care-tasks/{taskId}/complete` (`Groom`)

- **Mong đợi:** `200` "Đã hoàn thành công việc!". Task chuyển sang `status = "DONE"` và có `completedAt`.
- Nếu task là `FEED`, tồn kho (`Inventory_Items.current_stock`) của các món trong thực đơn của ngựa bị trừ. Kiểm tra lại bằng SQL.
- **Test lỗi:**
  - Hoàn thành lại cùng task → `400` "...đã được đánh dấu hoàn thành trước đó".
  - Groom khác gọi → `403`.
  - Trainer hoặc Owner gọi → `403`.
  - `taskId` không tồn tại → `404`.

---

## 10. Medical (VetLock) — `/api/v1/medical`

### 10.1 `POST /api/v1/medical/vet-lock` — Khóa tập khẩn cấp (`Veterinarian`, `Trainer`, `Groom`)
> **Phạm vi Trainer:** Trainer gọi với ngựa không được Club Manager phân công cho mình (3.8) → `403`.

Nhân viên trực tiếp chăm sóc hoặc huấn luyện được bấm khi phát hiện chấn thương. Owner hoặc Club Manager gọi → `403`.

```json
{ "horseId": 1, "reason": "Nghi chấn thương chân trước" }
```

- **Mong đợi:** `200`. Kiểm tra bằng 3.4: ngựa có `isLocked = true`, và `healthStatus` tùy người bấm:
- **Tự động khi khóa (F3-03):** mọi buổi tập chưa thực hiện (`PENDING` và `CONFIRMED`) của ngựa chuyển `CANCELLED` (ghi chú "Hủy tự động do ngựa bị khóa huấn luyện…"), giáo án đang chạy chuyển **`PAUSED`**, HLV phụ trách và chủ ngựa nhận email. Thao tác được ghi Audit Log (`VET_LOCK`).
- **Khi bác sĩ mở khóa (10.2):** giáo án `PAUSED` về **`PENDING`**; HLV phân công lại buổi tập, Groom xác nhận thì giáo án chạy lại. HLV và chủ ngựa nhận email, ghi Audit Log (`VET_UNLOCK`).

| Người bấm | `healthStatus` sau khi khóa |
|---|---|
| Veterinarian | `INJURED` (bác sĩ kết luận chấn thương) |
| Trainer hoặc Groom | `WATCH` (chỉ khóa tập, chờ bác sĩ khám) |

  Sau khi Trainer hoặc Groom khóa, bác sĩ khám rồi đổi sang `INJURED` qua `PUT /horses/{id}` (3.6) nếu đúng là chấn thương, hoặc mở khóa (10.2) nếu ngựa ổn.
- **Test lỗi:** khóa lại lần nữa → `400` "Ngựa này hiện đang bị khóa y tế rồi!".

### 10.2 `POST /api/v1/medical/release-lock/{horseId}` — Mở khóa (`Veterinarian`)

- Chỉ bác sĩ thú y được mở khóa.
- **Mong đợi:** `200`, ngựa có `isLocked = false`, `healthStatus = "HEALTHY"`.
- **Test lỗi:**
  - Groom, Trainer, Owner hoặc Club Manager gọi → `403`.
  - Mở khóa ngựa đang không bị khóa → `400` "Ngựa này hiện không bị khóa y tế, không cần mở khóa!".

### 10.3 Ca khám / sự cố (bảng `Vet_Cases`)

Luồng: Groom hoặc Trainer **báo sự cố** → ca `NEW` vào hộp thư bác sĩ → bác sĩ **ghi kết quả khám** (`MONITORING`) → **đóng ca** (`CLOSED`).

**a) `POST /api/v1/medical/cases`: báo sự cố (`Groom`, `Trainer`)**

```json
{ "horseId": 1, "caseType": "Đi khập khiễng", "severity": "HIGH", "description": "Chân trước phải sưng sau buổi tập", "imageUrl": null }
```

- `severity`: `LOW`, `MEDIUM` (mặc định), `HIGH` hoặc `CRITICAL`. `imageUrl` là đường dẫn ảnh đã tải lên (không gửi ảnh base64).
- Groom chỉ báo được cho **ngựa thuộc khu mình phụ trách**. Trainer báo được cho mọi ngựa.
- **Mong đợi:** `201` "Đã gửi ca C-0001 cho Bác sĩ thú y". `source` là `GROOM_REPORT` hoặc `TRAINER_REQUEST`, `status = "NEW"`.
- **Test lỗi:** Groom báo cho ngựa khác khu → `403`; `horseId` không tồn tại → `404`; thiếu `caseType` hoặc `description` → `400`.

**b) `GET /api/v1/medical/cases?status=ALL`: hộp thư ca khám (`Veterinarian`, `Groom`, `Trainer`)**

- **Bác sĩ** thấy mọi ca. **Groom / Trainer** chỉ thấy **ca do chính mình báo**, kèm kết quả khám (`vetResult`) khi bác sĩ đã xử lý.
- `status`: `ALL` (mặc định), `NEW`, `IN_EXAM`, `MONITORING`, `CLOSED`, `CANCELLED`.
- Mỗi ca có `caseId` (mã `C-0001`), `id`, `horseName`, `source`, `issueType`, `severity`, `status`, `description`, `reportedByName`, `assignedVetName`, `vetResult`, `createdAt`, `closedAt`.

### 10.4 Bác sĩ xử lý ca (`Veterinarian`)

**a) `POST /api/v1/medical/cases/action`: ghi kết quả khám**

```json
{ "caseId": "C-0001", "vetNotes": "Viêm gân nhẹ", "treatmentDirection": "Chườm đá 3 ngày, nghỉ tập" }
```

- `caseId` nhận mã `C-0001` hoặc ID số. Tham số `?vetId=` cũ vẫn được chấp nhận nhưng bỏ qua: bác sĩ là người đang đăng nhập.
- **Mong đợi:** `200`, ca chuyển `MONITORING`, có `assignedVetName` và `vetResult`.
- **Test lỗi:** ca đã đóng → `400`; mã ca không tồn tại → `404`; thiếu `caseId` hoặc `vetNotes` → `400`.
- Cần điều trị bằng thuốc thì lập phác đồ (12.2) với `caseId` là **ID số** của ca.

**b) `PUT /api/v1/medical/cases/{caseId}/close`: đóng ca**

```json
{ "outcome": "Đã hồi phục, cho tập lại nhẹ" }
```

- **Mong đợi:** `200`, `status = "CLOSED"`, có `closedAt`. Groom báo ca sẽ thấy kết luận này ở 10.3b.

---

## 11. Medical Record (bệnh án) — luồng 3 bước

Đây là luồng nghiệp vụ hoàn chỉnh. Test theo đúng thứ tự.

### 11.1 `POST /api/v1/veterinary/medical-records` — Bác sĩ tạo bệnh án (`Veterinarian`)

```json
{
  "horseId": 1,
  "visitDate": "2026-10-19",
  "symptoms": "Đi khập khiễng",
  "diagnosis": "Viêm gân nhẹ chân trước phải",
  "isLockoutOrdered": false,
  "injuryMarks": [
    { "bodyPartCode": "FRONT_RIGHT_LEG", "coordX": 120.5, "coordY": 340, "severityLevel": "MINOR" }
  ],
  "prescriptionItems": [
    { "itemId": 1, "dosage": "2 lần/ngày", "quantityPrescribed": 1 }
  ]
}
```

- `itemId` phải có trong `Inventory_Items` và đủ tồn kho.
- `severityLevel` phải là `MINOR`, `MODERATE` hoặc `SEVERE`.
- `isLockoutOrdered: true` sẽ khóa tập ngựa và đặt `healthStatus = "INJURED"`. Muốn test lại các mục khác thì mở khóa bằng 10.2.
- Số lượng khả dụng = tồn kho hiện tại + phần **chưa nhận** của các đơn mua hàng (`Purchase_Order_Lines`) có trạng thái `ORDERED`, `IN_TRANSIT`, `DELAYED` hoặc `PARTIALLY_RECEIVED` và ngày dự kiến về không trễ hơn `visitDate`.
- **Mong đợi:** `201`, `data` = `recordId`. **Ghi lại `recordId`**. Bệnh án ở trạng thái `PENDING_APPROVAL`.
- **Test lỗi:**
  - Thiếu `diagnosis` → `400`.
  - Groom gọi → `403`.
  - Kê vượt tồn kho (ví dụ vật tư "Gel bôi chống viêm", tồn 2, `quantityPrescribed: 10`) → `400` "Vật tư [Gel bôi chống viêm] không đủ đáp ứng. Khả dụng (bao gồm lô hàng sắp về): [2], Yêu cầu: [10]...".

### 11.2 `PUT /api/v1/manager/medical-records/{recordId}/approve` — Quản lý duyệt (`Club Manager`)

- **Mong đợi:** `200` "Đã phê duyệt phác đồ điều trị. Chờ chủ ngựa thanh toán." Bệnh án chuyển sang `APPROVED_AWAITING_PAYMENT`.
- **Test lỗi:** duyệt lại lần nữa → `500` "Hồ sơ không ở trạng thái chờ duyệt".

### 11.3 `POST /api/v1/owner/payments/medical-records/{recordId}/vnpay` — Chủ ngựa thanh toán (`Horse Owner`)

- Login bằng **chủ của con ngựa** trong bệnh án.
- **Mong đợi:** `200` "Thanh toán VNPay thành công...". Bệnh án chuyển sang `IN_TREATMENT`. Đây là **thanh toán giả lập**, không gọi VNPay thật.
- **Test lỗi:**
  - Owner khác gọi → `403` "Bạn không phải chủ của ngựa này".
  - Thanh toán trước khi duyệt, hoặc thanh toán lần 2 → `500` "Hồ sơ không ở trạng thái chờ thanh toán".

---

## 12. Phác đồ điều trị — `/api/v1/medical-management` (`Veterinarian`)

> **Cần chạy trước:** `HorseTrack/DATABASE_fix_protocol_items_fk.sql` (một lần). Script này xóa khóa ngoại thừa `Protocol_Items → medications` do Hibernate tạo. Nếu chưa chạy, tạo phác đồ (12.2) sẽ lỗi `500`.

Luồng đầy đủ (BR_07), phối hợp với kho ở mục **12E**:

```
Bác sĩ lập (12.2) ─► WAITING_STOCK_CHECK ─► Kho kiểm hàng (12E.2)
   ├─ đủ hàng ─► STOCK_AVAILABLE ─► Bác sĩ xác nhận (12.4) ─► VET_CONFIRMED ─► Kho cấp thuốc (12E.3) ─► IN_TREATMENT
   │                                                                     ─► Ghi tiến trình (12.7) ─► Kết thúc (12.8) ─► COMPLETED
   └─ thiếu hàng ─► STOCK_SHORTAGE ─► Bác sĩ chọn:
          • chờ hàng (12.5) ─► WAITING_RESTOCK ─► hàng về, kho kiểm lại (12E.2) ─► STOCK_AVAILABLE ...
          • phiên bản mới (12.6) ─► bản cũ SUPERSEDED, bản mới WAITING_STOCK_CHECK
   (Đơn mua cho phác đồ về trễ hơn requiredBy ─► REQUIRES_VET_REVIEW, bác sĩ chọn lại như trên)
Hủy (12.9) được ở mọi bước trước khi cấp thuốc ─► CANCELLED
```

Club Manager được **xem** danh mục thuốc và phác đồ (12.1, 12.3) nhưng không quyết định gì (BR_07).

### 12.1 `GET /api/v1/medical-management/medications` — Danh mục thuốc (chỉ đọc)

- **Mong đợi:** `200`, các vật tư nhóm `MEDICINE`, `VACCINE`, `MEDICAL_SUPPLY` lấy từ `Inventory_Items`, kèm `availableStock`, `route`, `isControlled`, `withdrawalHours`. **Ghi lại `itemId`** các thuốc sẽ kê.
- Nếu danh sách rỗng: nhờ nhân viên kho thêm thuốc (12A.1, `category: "MEDICINE"`) và nhập kho (12A.2).

### 12.2 `POST /api/v1/medical-management/protocols` — Lập phác đồ

```json
{
  "horseId": 2,
  "caseId": null,
  "recordId": null,
  "diagnosis": "Viêm gân nhẹ chân trước phải",
  "requiredBy": "2026-10-30",
  "note": "Theo dõi sưng mỗi ngày",
  "phases": [
    {
      "phaseName": "Chống viêm",
      "startDate": "2026-10-21",
      "durationDays": 5,
      "goal": "Giảm sưng",
      "items": [
        { "itemId": 10, "quantity": 4, "dosage": "1 viên/ngày" },
        { "itemId": 11, "quantity": 2, "dosage": "Tiêm 1 lọ cách ngày" }
      ]
    },
    {
      "phaseName": "Phục hồi",
      "startDate": "2026-10-26",
      "durationDays": 7,
      "items": [ { "itemId": 10, "quantity": 3 } ]
    }
  ]
}
```

- **Mong đợi:** `201`. `protocolCode` do DB tự sinh dạng `PD-0001`, `versionNo = 1`, `state = "WAITING_STOCK_CHECK"`, có `totalCost` = tổng (số lượng × đơn giá). **Ghi lại `protocolId`**.
- **Test lỗi:**
  - Kê vật tư không phải thuốc (ví dụ nhóm `FEED`) → `400`.
  - Trùng `itemId` trong cùng một giai đoạn → `400`.
  - `phases: []`, hoặc `startDate` / `requiredBy` ở quá khứ → `400`.
  - `horseId`, `caseId` hoặc `recordId` không tồn tại → `404`.
  - Token Trainer, Groom hoặc nhân viên kho → `403`.

### 12.3 Xem phác đồ (`Veterinarian`, `Club Manager`)

- `GET /api/v1/medical-management/protocols?state=&horseId=`: danh sách, mới nhất trước.
- `GET /api/v1/medical-management/protocols/{protocolId}`: chi tiết gồm `phases`, `stockAnswer` (kết quả kiểm kho từng thuốc: cần, khả dụng, đã giữ, thiếu, ngày về, trạng thái), `progressNotes`, cùng phiên bản trước/sau (`replacesProtocolCode`, `replacedByProtocolCode`).

### 12.4 `PUT /api/v1/medical-management/protocols/{protocolId}/confirm` — Xác nhận phương án kho

- Chỉ khi kho đã giữ đủ hàng (`STOCK_AVAILABLE`) → `VET_CONFIRMED`. Khi đó kho mới được cấp thuốc.
- Gọi khi `STOCK_SHORTAGE` → `400` (BR_07: thiếu thuốc thì không được duyệt cấp).

### 12.5 `PUT /api/v1/medical-management/protocols/{protocolId}/wait-restock` — Chờ hàng về

```json
{ "waitEta": null, "note": "Chờ thuốc về, ngựa vẫn nghỉ tập" }
```

- Chỉ khi `STOCK_SHORTAGE` hoặc `REQUIRES_VET_REVIEW` → `WAITING_RESTOCK`. Bỏ trống `waitEta` thì lấy ngày về muộn nhất mà kho đã báo.

### 12.6 `POST /api/v1/medical-management/protocols/{protocolId}/new-version` — Tạo phiên bản mới

- Body giống 12.2 (cùng `horseId`). Dùng khi muốn đổi thuốc hoặc số lượng vì thiếu hàng hay hàng về trễ.
- **Mong đợi:** `201`. Phác đồ mới có `versionNo = 2` và `replacesProtocolId` trỏ về bản cũ. Bản cũ chuyển `SUPERSEDED` và **trả lại hàng đã giữ** (tồn kho `heldStock` giảm).
- **Test lỗi:** tạo phiên bản mới từ bản đã `SUPERSEDED`, `IN_TREATMENT` hoặc `COMPLETED` → `400`; khác `horseId` → `400`.

### 12.7 `POST /api/v1/medical-management/protocols/{protocolId}/progress-notes` — Ghi tiến trình

```json
{ "content": "Ngày 3: giảm sưng, ngựa đi lại bình thường" }
```

- Chỉ khi `IN_TREATMENT` (sau khi kho đã cấp thuốc). Trạng thái khác → `400`.

### 12.8 `PUT /api/v1/medical-management/protocols/{protocolId}/complete` — Kết thúc điều trị

```json
{ "content": "Hồi phục hoàn toàn, có thể tập lại nhẹ" }
```

- `IN_TREATMENT` → `COMPLETED`, lưu `outcome` và `closedAt`.

### 12.9 `PUT /api/v1/medical-management/protocols/{protocolId}/cancel` — Hủy phác đồ

```json
{ "content": "Ngựa đã khỏi, không cần điều trị" }
```

- Chỉ khi chưa cấp thuốc. Hàng đã giữ được trả lại kho, phác đồ chuyển `CANCELLED`.

---

## 12A. Inventory — `/api/v1/inventory` (`Warehouse Manager`)

Toàn bộ nhóm này chỉ dành cho **nhân viên kho**. Mọi role khác gọi đều nhận `403`.

**Chuẩn bị tài khoản:** Club Manager tạo nhân viên kho qua `POST /api/v1/auth/staff/create` (1.3):

```json
{
  "username": "kho01",
  "password": "kho12345",
  "fullName": "Nguyen Van Kho",
  "email": "kho01@test.com",
  "roleName": "Warehouse Manager"
}
```

Sau đó login bằng `kho01` và Authorize.

### 12A.1 `POST /api/v1/inventory/items` — Thêm vật tư mới

```json
{
  "itemName": "Băng thun cổ chân",
  "category": "MEDICAL_SUPPLY",
  "unit": "cuộn",
  "unitPrice": 35000,
  "minSafetyStock": 20,
  "brand": "Vetrap",
  "note": "Dùng băng chân sau khi tập"
}
```

- `category` chỉ nhận `FEED`, `SUPPLEMENT`, `MEDICINE`, `VACCINE`, `MEDICAL_SUPPLY`, `STABLE_SUPPLY`, `EQUIPMENT` hoặc `OTHER`.
- **Mong đợi:** `201`. `data` có `itemCode` (DB tự sinh dạng `VT-0013`) và `currentStock = 0`. Vì tồn kho 0 < mức an toàn 20 nên `belowSafetyLevel = true`. **Ghi lại `itemId`**.
- **Test lỗi:**
  - Thêm lại cùng tên trong cùng nhóm → `400` "Vật tư ... đã tồn tại...".
  - `category: "Medication"` hoặc `unitPrice: -1` → `400`.

### 12A.2 `POST /api/v1/inventory/stock-movements` — Nhập / xuất / điều chỉnh / hủy

Các ví dụ dưới dùng `itemId: 13`. Hãy thay bằng `itemId` bạn vừa tạo ở 12A.1.

**a) Nhập kho (RECEIVE)**: bắt buộc `lotNo`.

```json
{ "itemId": 13, "movementType": "RECEIVE", "quantity": 50, "lotNo": "LOT-2026-10-A", "expiryDate": "2027-10-31", "note": "Nhập từ NCC Vetrap" }
```

- **Mong đợi:** `201`. `data.transaction` có `txType = "RECEIVE"`, `balanceAfter = 50`, `lotNo`, `txCode`. `data.item.currentStock = 50`, `belowSafetyLevel = false`.
- Nhập tiếp cùng `lotNo` thì số lượng được cộng dồn vào lô đó. Nếu gửi `expiryDate` khác với hạn dùng đã lưu của lô → `400`.

**b) Xuất kho (ISSUE)**: `lotNo` tùy chọn. Nếu có thì trừ đúng lô đó.

```json
{ "itemId": 13, "movementType": "ISSUE", "quantity": 35, "lotNo": "LOT-2026-10-A", "note": "Cấp cho khu A" }
```

- **Mong đợi:** `201`, `balanceAfter = 15`. Vì khả dụng 15 < mức an toàn 20, `data.lowStockWarning` có nội dung "Cảnh báo (BR_08)...".
- **Test lỗi:**
  - Xuất nhiều hơn khả dụng → `400` "Không đủ hàng khả dụng để xuất...".
  - Xuất nhiều hơn số còn lại trong lô → `400` "Lô ... chỉ còn ...".
  - `lotNo` không tồn tại → `404`.

**c) Điều chỉnh sau kiểm đếm (ADJUST)**: `quantity` âm để giảm, dương để tăng. Bắt buộc `note`.

```json
{ "itemId": 13, "movementType": "ADJUST", "quantity": -2, "lotNo": "LOT-2026-10-A", "note": "Kiểm đếm thực tế thiếu 2 cuộn" }
```

- **Mong đợi:** `201`, `balanceAfter = 13`.
- **Test lỗi:**
  - Thiếu `note` → `400`.
  - `quantity: 0` → `400`.
  - Giảm làm tồn kho âm, hoặc thấp hơn số đang giữ cho phác đồ → `400`.

**d) Hủy hàng hỏng hoặc hết hạn (DISPOSE)**: giống ISSUE nhưng bắt buộc `note`.

### 12A.3 `GET /api/v1/inventory/items?category=&keyword=`

- **Mong đợi:** `200`, danh sách vật tư có `currentStock` (đang có), `heldStock` (đang giữ), `availableStock` (khả dụng) và `belowSafetyLevel`.
- Thử `category=FEED`, hoặc `keyword=VT-0013` (tìm theo mã), hoặc `keyword=yến` (tìm theo tên).

### 12A.4 `GET /api/v1/inventory/items/{itemId}`

- **Mong đợi:** `200`, thông tin vật tư kèm `lots` (từng lô: số lô, hạn dùng, số lượng).
- `itemId` không tồn tại → `404`.

### 12A.5 `PUT /api/v1/inventory/items/{itemId}` — Sửa thông tin vật tư

- Body giống 12A.1. **Không sửa được tồn kho ở đây**: tồn kho chỉ thay đổi qua 12A.2, để luôn có nhật ký kho.

### 12A.6 `PUT /api/v1/inventory/items/{itemId}/safety-level` — Đặt mức an toàn (BR_08)

```json
{ "minSafetyStock": 10 }
```

- **Mong đợi:** `200`. Với ví dụ trên (khả dụng 13), `belowSafetyLevel` chuyển thành `false`.
- `minSafetyStock: -5` → `400`.

### 12A.7 `GET /api/v1/inventory/low-stock` — Cảnh báo tồn kho thấp (BR_08)

- **Mong đợi:** `200`, chỉ những vật tư có khả dụng thấp hơn mức an toàn.

### 12A.8 `GET /api/v1/inventory/transactions?itemId=&type=&fromDate=&toDate=&page=0&size=20` — Nhật ký kho

- **Mong đợi:** `200`, mọi phát sinh kho mới nhất trước, mỗi dòng có `balanceAfter`, `performedByName` và `note`.
- Thử lọc `type=RECEIVE`, `itemId=13`, hoặc `fromDate=2026-10-20&toDate=2026-10-20`. `toDate` trước `fromDate` → `400`.

---

## 12B. Nhà cung cấp & đơn mua hàng — `/api/v1/inventory` (`Warehouse Manager`)

Luồng: **thêm nhà cung cấp** → **tạo đơn** → (đổi ngày về / đang vận chuyển) → **nhận hàng** (tự nhập kho) hoặc **hủy đơn**.

### 12B.1 Nhà cung cấp

- `POST /api/v1/inventory/suppliers`

  ```json
  { "supplierName": "Công ty Thú y Sài Gòn", "contactPhone": "0901234567", "contactEmail": "sales@tysg.vn", "address": "12 Lê Lợi, Q1" }
  ```

  **Mong đợi:** `201`. **Ghi lại `supplierId`**. Thêm trùng tên → `400`. Email hoặc số điện thoại sai định dạng → `400`.
- `GET /api/v1/inventory/suppliers?activeOnly=false`: danh sách nhà cung cấp.
- `PUT /api/v1/inventory/suppliers/{supplierId}`: body giống trên. Thêm `"isActive": false` để ngừng hợp tác; nhà cung cấp đã ngừng thì không tạo đơn mới được.

### 12B.2 `POST /api/v1/inventory/purchase-orders` — Tạo đơn mua

```json
{
  "supplierId": 1,
  "purpose": "RESTOCK",
  "expectedDeliveryDate": "2026-10-25",
  "requiredBy": "2026-10-30",
  "note": "Bổ sung kho tháng 10",
  "lines": [
    { "itemId": 13, "quantity": 30 },
    { "itemId": 5, "quantity": 100, "unitPrice": 24000 }
  ]
}
```

- `purpose`: `RESTOCK` (bổ sung kho), `REQUISITION` (mua cho yêu cầu vật tư của Groom, xem 12C, kèm `requisitionId`) hoặc `PROTOCOL` (mua thuốc còn thiếu cho phác đồ, xem 12E.4, kèm `protocolId`).
- Bỏ trống `unitPrice` thì lấy đơn giá hiện tại của vật tư.
- **Mong đợi:** `201`. `orderCode` do DB tự sinh dạng `SHIP-0001`, `status = "ORDERED"`, có `totalAmount` và từng dòng kèm `poLineId`. **Ghi lại `orderId` và các `poLineId`**.
- **Test lỗi:**
  - Trùng `itemId` trong 2 dòng → `400`.
  - `lines: []` → `400`.
  - `expectedDeliveryDate` ở quá khứ → `400`.
  - `purpose: "REQUISITION"` mà thiếu `requisitionId` → `400`.
  - `expectedDeliveryDate` trễ hơn `requiredBy` → đơn được tạo ngay với `status = "DELAYED"`.

### 12B.3 `PUT /api/v1/inventory/purchase-orders/{orderId}/eta` — Đổi ngày về

```json
{ "expectedDeliveryDate": "2026-11-05", "note": "NCC báo trễ do vận chuyển" }
```

- **Mong đợi:** `200`. `previousEta` lưu ngày cũ, ghi chú được nối thêm. Ngày mới trễ hơn `requiredBy` → `status = "DELAYED"` và `etaLaterThanRequired = true` (BR_07).

### 12B.4 `PUT /api/v1/inventory/purchase-orders/{orderId}/in-transit` — Đang vận chuyển

- Chỉ đơn `ORDERED` hoặc `DELAYED` → `IN_TRANSIT`. Gọi với đơn ở trạng thái khác → `400`.

### 12B.5 `PUT /api/v1/inventory/purchase-orders/{orderId}/receive` — Nhận hàng và nhập kho

```json
{
  "lines": [
    { "poLineId": 1, "quantityReceived": 20, "lotNo": "PO1-LOT-A", "expiryDate": "2027-10-31" }
  ],
  "note": "Nhận đợt 1"
}
```

- **Mong đợi:** `200`. Nhận chưa đủ thì `status = "PARTIALLY_RECEIVED"`, nhận đủ mọi dòng thì `RECEIVED` và có `receivedAt`.
- Tồn kho tăng tương ứng (kiểm tra bằng 12A.4). Nhật ký kho (12A.8) có dòng `RECEIVE` với `refType = "PURCHASE_ORDER"`, `refId = orderId`.
- **Test lỗi:**
  - Nhận vượt số còn lại → `400` "...chỉ còn ... chưa nhận".
  - `poLineId` của đơn khác → `400`.
  - Thiếu `lotNo` → `400`.
  - Nhận đơn đã `RECEIVED` hoặc `CANCELLED` → `400`.

### 12B.6 `PUT /api/v1/inventory/purchase-orders/{orderId}/cancel` — Hủy đơn

```json
{ "reason": "Đặt nhầm số lượng" }
```

- Chỉ hủy được khi **chưa nhận hàng**. Đã nhận một phần → `400`.

### 12B.7 Xem đơn

- `GET /api/v1/inventory/purchase-orders?status=&supplierId=`: lọc theo trạng thái hoặc nhà cung cấp.
- `GET /api/v1/inventory/purchase-orders/{orderId}`: chi tiết từng dòng (đã đặt, đã nhận, còn lại, lô).

---

## 12C. Yêu cầu vật tư của Groom — `/api/v1/supply-requests` (`Groom`) và `/api/v1/inventory/supply-requests` (`Warehouse Manager`)

Luồng trạng thái: `SUBMITTED` → `ISSUED`. Nếu thiếu hàng: `SUBMITTED` → `WAITING_RESTOCK` → (hàng về) `CHECKING_STOCK` → `ISSUED`. Có thể kết thúc bằng `REJECTED` (kho từ chối) hoặc `CANCELLED` (Groom hủy).

### 12C.0 `GET /api/v1/supply-requests/items`: danh mục vật tư được yêu cầu (`Groom`)

- Vật tư **thật trong kho**, mọi nhóm trừ `MEDICINE` và `VACCINE`, kèm `availableStock`. Dùng cho ô chọn vật tư khi gửi yêu cầu (12C.1), thay cho dữ liệu mẫu.
- **Mong đợi:** `200`, mỗi vật tư có `itemId`, `itemCode`, `itemName`, `category`, `unit`, `availableStock`.

### 12C.1 `POST /api/v1/supply-requests` — Groom gửi yêu cầu (`Groom`)

```json
{ "itemId": 13, "quantity": 5, "reason": "Băng chân cho ngựa sau buổi tập", "neededBy": "2026-10-21" }
```

- Khu chuồng (`barnZone`) tự lấy từ hồ sơ Groom.
- **Mong đợi:** `201`. `requisitionCode` do DB tự sinh dạng `RQ-0001`, `status = "SUBMITTED"`. **Ghi lại `requisitionId`**.
- **Test lỗi:**
  - Yêu cầu vật tư nhóm `MEDICINE` hoặc `VACCINE` → `400` (thuốc phải đi theo phác đồ của bác sĩ).
  - Token của nhân viên kho hoặc Trainer → `403`.

### 12C.2 `GET /api/v1/supply-requests/my` (`Groom`)

- Các yêu cầu của chính Groom kèm trạng thái, số đã cấp, lý do từ chối và mã đơn mua liên quan.

### 12C.3 `PUT /api/v1/supply-requests/{requisitionId}/cancel` (`Groom`)

- Groom hủy yêu cầu của mình khi kho chưa xử lý xong. Hủy yêu cầu của Groom khác → `403`. Hủy yêu cầu đã `ISSUED` → `400`.

### 12C.4 `GET /api/v1/inventory/supply-requests?status=&barnZone=` (`Warehouse Manager`)

- Danh sách yêu cầu, kèm `availableStock` để kho quyết định cấp ngay hay phải chờ hàng.

### 12C.5 `PUT /api/v1/inventory/supply-requests/{requisitionId}/issue` — Cấp vật tư (`Warehouse Manager`)

```json
{ "issuedQuantity": 5, "lotNo": "LOT-2026-10-A", "note": "Cấp cho khu A" }
```

- Body có thể bỏ trống (`{}`) để cấp đủ số lượng yêu cầu. Được cấp ít hơn, không được cấp nhiều hơn.
- **Mong đợi:** `200`, `status = "ISSUED"`. Tồn kho giảm, nhật ký kho có dòng `ISSUE` với `refType = "REQUISITION"`.
- **Test lỗi:** kho không đủ khả dụng → `400` "Không đủ hàng khả dụng... Hãy chuyển yêu cầu sang chờ hàng (wait-restock) hoặc tạo đơn mua hàng."

### 12C.6 Khi thiếu hàng

- **Cách 1:** `PUT /api/v1/inventory/supply-requests/{requisitionId}/wait-restock` → `WAITING_RESTOCK`.
- **Cách 2:** tạo đơn mua (12B.2) với `"purpose": "REQUISITION", "requisitionId": <id>`. Đơn phải có vật tư của yêu cầu. Yêu cầu tự chuyển sang `WAITING_RESTOCK`.
- Khi đơn được **nhận đủ** (12B.5), yêu cầu tự chuyển sang `CHECKING_STOCK`, rồi kho cấp như 12C.5. Nếu đơn bị hủy, yêu cầu trở về `SUBMITTED`.

### 12C.7 `PUT /api/v1/inventory/supply-requests/{requisitionId}/reject` — Từ chối (`Warehouse Manager`)

```json
{ "reason": "Khu A đã được cấp đủ trong tuần" }
```

- **Mong đợi:** `200`, `status = "REJECTED"`, có `rejectReason`. Thiếu `reason` → `400`.

---

## 12D. Kiểm kê hàng tháng — `/api/v1/inventory/stock-counts` (`Warehouse Manager`)

Luồng: **tạo phiếu (DRAFT)** → sửa số đếm nếu cần → **chốt phiếu (DONE)**, khi đó tồn kho được điều chỉnh về đúng số đếm thực tế.

### 12D.1 `POST /api/v1/inventory/stock-counts` — Tạo phiếu

```json
{
  "countMonth": "2026-10",
  "note": "Kiểm kê cuối tháng 10",
  "lines": [
    { "itemId": 13, "countedQty": 36, "reason": "Hư hỏng 2 cuộn" },
    { "itemId": 5, "countedQty": 500 }
  ]
}
```

- Mỗi tháng chỉ có một phiếu. `systemQty` là tồn kho hệ thống tại lúc nhập phiếu, `difference = countedQty - systemQty`.
- **Mong đợi:** `201`. `countCode = "KK-2026-10"`, `status = "DRAFT"`, có `linesWithDifference`. **Ghi lại `countId`**.
- **Test lỗi:**
  - Tạo lần 2 cho cùng tháng → `400`.
  - `countMonth` ở tương lai, hoặc sai định dạng (`"10/2026"`) → `400`.
  - `countedQty` âm → `400`.

### 12D.2 `PUT /api/v1/inventory/stock-counts/{countId}` — Sửa phiếu

- Body giống 12D.1, gửi **đầy đủ** danh sách dòng; dòng không gửi sẽ bị bỏ. Chỉ sửa được khi còn `DRAFT`.

### 12D.3 `PUT /api/v1/inventory/stock-counts/{countId}/complete` — Chốt phiếu

- Với mỗi dòng, hệ thống lấy tồn kho **tại lúc chốt**, rồi ghi `ADJUST` (`refType = "STOCK_COUNT"`) để tồn kho bằng đúng số đếm.
- **Mong đợi:** `200`, `status = "DONE"`. Tồn kho (12A.4) bằng `countedQty`.
- **Test lỗi:**
  - Dòng có chênh lệch mà chưa có `reason` → `400`. Hãy sửa phiếu (12D.2) trước.
  - Chốt lại phiếu đã `DONE` → `400`.
  - Số đếm thấp hơn lượng đang giữ cho phác đồ → `400`.

### 12D.4 Xem phiếu

- `GET /api/v1/inventory/stock-counts`: danh sách, mới nhất trước.
- `GET /api/v1/inventory/stock-counts/{countId}`: từng dòng với số hệ thống, số đếm, chênh lệch và lý do.

---

## 12E. Kiểm kho & cấp thuốc cho phác đồ — `/api/v1/inventory/protocols` (`Warehouse Manager`)

Phần việc của kho trong luồng ở mục 12. Kho **không có quyền quyết định điều trị** (BR_07): chỉ trả lời có đủ hàng hay không, giữ hàng, và cấp thuốc sau khi bác sĩ xác nhận.

### 12E.1 Xem phác đồ chờ kho

- `GET /api/v1/inventory/protocols`: mặc định các phác đồ `WAITING_STOCK_CHECK`, `WAITING_RESTOCK`, `REQUIRES_VET_REVIEW`, `VET_CONFIRMED`, sắp theo ngày cần thuốc. Thêm `?state=...` để lọc một trạng thái.
- `GET /api/v1/inventory/protocols/{protocolId}`: thuốc theo giai đoạn và kết quả kiểm kho gần nhất.

### 12E.2 `PUT /api/v1/inventory/protocols/{protocolId}/stock-check` — Kiểm kho

Hệ thống tự cộng số lượng từng thuốc qua các giai đoạn và so với **khả dụng** (đang có − đang giữ). Kho chỉ cần báo ngày về cho thuốc thiếu. Body có thể bỏ trống (`{}`).

```json
{
  "shortages": [
    { "itemId": 11, "eta": "2026-10-25", "purchaseOrderId": null }
  ],
  "note": "Penicillin hết, NCC giao trong tuần"
}
```

- **Đủ hết:** giữ hàng (tăng `heldStock`, nhật ký kho có dòng `HOLD` với `refType = "PROTOCOL"`) → `STOCK_AVAILABLE`.
- **Thiếu:** giữ phần đang có, ghi số thiếu và `eta` → `STOCK_SHORTAGE`, trả về bác sĩ quyết định (12.5 hoặc 12.6).
- Phần đã giữ **không xuất được** bằng 12A.2 (ISSUE) nữa. Thử xuất vượt khả dụng → `400`.
- **Kiểm lại khi hàng về:** gọi lại API này (khi `WAITING_RESTOCK`, `REQUIRES_VET_REVIEW` hoặc `STOCK_SHORTAGE`). Lần giữ trước được trả lại (`RELEASED`) rồi tính lại từ đầu, không bị giữ trùng.
- **Test lỗi:**
  - Kiểm kho phác đồ đã `VET_CONFIRMED` hoặc `IN_TREATMENT` → `400`.
  - `itemId` trong `shortages` không có trong phác đồ → `400`.
  - `purchaseOrderId` của đơn không có thuốc đó, hoặc đơn đã nhận đủ / đã hủy → `400`.

### 12E.3 `PUT /api/v1/inventory/protocols/{protocolId}/dispense` — Cấp thuốc

- Chỉ khi bác sĩ đã xác nhận (`VET_CONFIRMED`). Xuất toàn bộ phần đã giữ: `heldStock` và `currentStock` cùng giảm, nhật ký kho có dòng `ISSUE` với `refType = "PROTOCOL"`.
- **Mong đợi:** `200`, phác đồ chuyển `IN_TREATMENT`, các dòng `stockAnswer` chuyển `ISSUED`.
- Cấp khi chưa xác nhận → `400`.

### 12E.4 Đặt mua thuốc cho phác đồ (BR_07)

- Tạo đơn mua (12B.2) với `"purpose": "PROTOCOL", "protocolId": <id>`. `requiredBy` mặc định lấy ngày cần thuốc của phác đồ. Các dòng thiếu của phác đồ được gắn với đơn và nhận `eta` của đơn.
- **Hàng về trễ:** nếu ngày về của đơn (lúc tạo, hoặc khi đổi ETA ở 12B.3) trễ hơn `requiredBy` của phác đồ, phác đồ đang `STOCK_SHORTAGE` / `WAITING_RESTOCK` sẽ tự chuyển `REQUIRES_VET_REVIEW` (`isDelayed = true`) để bác sĩ quyết định chờ tiếp hay đổi phác đồ.

---

## 12F. Hóa đơn & thanh toán VNPay Sandbox — `/api/v1/billing`, `/api/v1/owner/invoices`, `/api/v1/payments/vnpay`

Dùng các bảng có sẵn `Invoices`, `Invoice_Line_Items`, `Payment_Transactions` và `Horse_Owner_Profiles.service_status`; không cần sửa DB. **Chỉ chạy môi trường Sandbox**: server từ chối khởi động nếu `pay-url` / `api-url` không phải `sandbox.vnpayment.vn`.

### 12F.0 Chuẩn bị

1. **Tài khoản VNPay Sandbox:** đăng ký tại https://sandbox.vnpayment.vn/devreg/ → nhận `vnp_TmnCode` và `vnp_HashSecret` qua email.
2. **IntelliJ → Run → Edit Configurations → Environment variables** (không ghi vào `application.properties`):
   ```
   VNPAY_TMN_CODE=<mã website>;VNPAY_HASH_SECRET=<chuỗi bí mật>
   ```
   Chưa đặt thì API thanh toán trả `503` "Cổng VNPay Sandbox chưa được cấu hình".
3. **Thẻ test của Sandbox** (ngân hàng NCB): số thẻ `9704198526191432198`, tên `NGUYEN VAN A`, ngày phát hành `07/15`, OTP `123456`.
4. **Giá thật trong DB:** hiện mọi chuồng có `monthly_base_fee = 0`, nên phải đặt phí lưu chuồng (12F.1) trước khi phát hành hóa đơn. Các nguồn phí khác lấy tự động nếu có dữ liệu:

| Dòng phí | Nguồn dữ liệu |
|---|---|
| `BOARDING` – Lưu chuồng | `Stalls.monthly_base_fee` của chuồng ngựa đang ở |
| `DAILY_CARE` – Vật tư chăm sóc | `Task_Material_Usage` của việc chăm sóc `DONE` trong kỳ |
| `MEDICATION` – Thuốc điều trị | **Không** nằm trong hóa đơn tháng: thu theo từng giai đoạn phác đồ bằng hóa đơn riêng `GD-...` (12F.8, BR_16) |
| `COMPETITION` – Thi đấu | `entry_fee_charged`, `transport_fee` của `Competition_Entries` có giải diễn ra trong kỳ |

### 12F.1 `PUT /api/v1/billing/stalls/{stallId}/monthly-fee` (`Club Manager`)

```json
{ "monthlyBaseFee": 6000000 }
```

- **Mong đợi:** `200`, `data` = phí mới. Ghi Audit Log `UPDATE_STALL_FEE`.
- **Test lỗi:** số âm hoặc có phần lẻ → `400`; `stallId` sai → `404`; token khác Club Manager → `403`.

### 12F.2 `POST /api/v1/billing/invoices/generate?billingMonth=2026-10` (`Club Manager`)

- Lập hóa đơn kỳ `billingMonth` cho mọi ngựa đã được duyệt (có chuồng). Hệ thống cũng **tự chạy 00:30 ngày 1 hằng tháng** cho kỳ tháng trước (BR_12).
- **Mong đợi:** `201`, `data` gồm `created`, `skippedExisting` (ngựa đã có hóa đơn kỳ này), `skippedZero` (không phát sinh phí), `invoiceCodes` (dạng `HD-202610-0006`). Chủ ngựa nhận email hóa đơn.
- Hạn thanh toán = ngày phát hành + 9 ngày (phát hành ngày 1 → hạn ngày 10).
- **Test lỗi:** `billingMonth` sai định dạng hoặc là tháng tương lai → `400`. Gọi lại cùng kỳ → không tạo trùng (`skippedExisting`).

### 12F.3 `GET /api/v1/billing/invoices?status=&billingMonth=&ownerId=&page=0&size=20` (`Club Manager`)

- Mỗi hóa đơn có `principal` (gốc), `lateFee`, `reactivationFee`, `amountDue`, `amountPaid`, `daysOverdue`, `daysSinceIssue`, `suspensionEligible` (≥ 25 ngày, BR_13), `terminationDue` (quá hạn > 60 ngày, BR_15), `ownerServiceStatus`.
- `GET /api/v1/billing/invoices/{id}`: thêm `lines` (dòng phí) và `transactions` (lịch sử giao dịch).
- `PUT /api/v1/billing/invoices/{id}/cancel` `{ "reason": "Lập sai phí lưu chuồng" }`: chỉ hóa đơn đang mở và chưa có giao dịch; lý do 10–255 ký tự → ngược lại `400`.

**Cách tính tiền (BR_12, BR_14):**
- Từ ngày sau hạn: phí trễ hạn = gốc × 1% × số ngày quá hạn. Hóa đơn `UNPAID` quá hạn được tác vụ hằng ngày (00:15) chuyển `OVERDUE` và báo chủ ngựa.
- Ngày thứ 20 kể từ phát hành: email cảnh báo chủ ngựa. Ngày thứ 25: email Club Manager danh sách đủ điều kiện đình chỉ.
- Chủ ngựa đang bị đình chỉ: cộng phí kích hoạt lại 3.000.000 VND vào hóa đơn mở có hạn sớm nhất (chỉ một lần).
- Các mức trên chỉnh được bằng `app.billing.*` trong `application.properties`.

### 12F.4 Đình chỉ / kích hoạt lại dịch vụ (`Club Manager`)

- `PUT /api/v1/billing/owners/{ownerId}/suspend` `{ "reason": "Nợ quá 25 ngày chưa thanh toán" }` → `200`, `serviceStatus = SUSPENDED`. Chỉ khi chủ ngựa có hóa đơn mở ≥ 25 ngày kể từ phát hành, ngược lại `400`. Ghi Audit Log, email chủ ngựa.
- Chủ ngựa trả hết hóa đơn mở qua VNPay → hệ thống tự chuyển `REACTIVATION_PENDING`.
- `PUT /api/v1/billing/owners/{ownerId}/reactivate` → `ACTIVE`. Chỉ khi đang `REACTIVATION_PENDING`, ngược lại `400`.
- `GET /api/v1/billing/owners/{ownerId}/service`: xem trạng thái hiện tại.

### 12F.5 Chủ ngựa xem hóa đơn (`Horse Owner`)

- `GET /api/v1/owner/invoices`: hóa đơn còn phải trả (`UNPAID`, `OVERDUE`). `?includeClosed=true` để xem cả đã trả / đã hủy.
- `GET /api/v1/owner/invoices/{id}`: chi tiết, `amountDue` là số tiền sẽ thanh toán nếu trả hôm nay. Hóa đơn của người khác → `403`.
- `GET /api/v1/owner/invoices/payment-history`: chỉ giao dịch đã kết thúc (`VERIFIED`, `FAILED`).

### 12F.6 Thanh toán: `POST /api/v1/owner/invoices/{id}/vnpay` (`Horse Owner`)

1. Gọi API → `201`, `data.paymentUrl`. Giao dịch được tạo ở trạng thái `PENDING_VERIFICATION`, số tiền = `amountDue` tại thời điểm tạo.
2. Mở `paymentUrl` trên trình duyệt → trang VNPay Sandbox → chọn ngân hàng NCB, nhập thẻ test ở 12F.0.
3. VNPay chuyển về `GET /api/v1/payments/vnpay/return?...`. Server kiểm tra chữ ký rồi **hỏi lại VNPay bằng `querydr`**. Chỉ khi VNPay xác nhận thành công và đúng số tiền thì giao dịch mới thành `VERIFIED` và hóa đơn thành `PAID`. Kết quả trả JSON `status = SUCCESS | FAILED | PENDING | INVALID`; nếu đặt `VNPAY_FRONTEND_RESULT_URL` thì chuyển hướng về trang đó kèm `?status=&invoiceId=&txnRef=`.
4. Kiểm tra lại bằng 12F.5: hóa đơn biến khỏi danh sách mở, xuất hiện trong lịch sử thanh toán. Chủ ngựa nhận email biên nhận.

- **IPN** `GET /api/v1/payments/vnpay/ipn`: VNPay gọi server-to-server. Muốn dùng thì khai báo URL công khai (ví dụ ngrok `https://xxxx.ngrok-free.app/api/v1/payments/vnpay/ipn`) trong trang quản trị merchant Sandbox. Trả `RspCode`: `00` thành công, `97` sai chữ ký, `01` không có giao dịch, `04` sai số tiền, `02` đã xử lý (idempotent).
- **Test lỗi:** hóa đơn đã trả hoặc đã hủy → `400`; hóa đơn của người khác → `403`; chưa cấu hình VNPay → `503`. Hủy trên trang VNPay → giao dịch `FAILED`, hóa đơn giữ nguyên.

### 12F.7 `GET /api/v1/reports/finance?fromMonth=2026-01&toMonth=2026-12` (`Club Manager`)

Báo cáo tài chính từ dữ liệu thật; tiền đã thu **chỉ tính giao dịch VNPay đã được server xác minh** (`VERIFIED`).

| Phần | Nội dung |
|---|---|
| `totals` | Phát hành, đã thu, tỷ lệ thu, phí trễ hạn đã thu, gốc còn phải thu, phí trễ hạn đang phát sinh, chi phí nhập hàng (đơn mua đã nhận), dòng tiền ròng, số hóa đơn phát hành / đã trả / quá hạn |
| `months` | Theo tháng: phát hành (theo kỳ), đã thu (theo ngày xác minh), tỷ lệ thu của kỳ, chi phí nhập hàng |
| `revenueByService` | Theo loại phí: phát hành và đã thu |
| `debtAgeing` | Tuổi nợ: chưa đến hạn, quá hạn 1–15 ngày, 16–60 ngày (đủ điều kiện đình chỉ), trên 60 ngày (BR_15) |
| `debtByOwner` | Công nợ từng chủ ngựa: số hóa đơn mở, gốc, phí trễ hạn, tổng phải trả, số ngày quá hạn lớn nhất, cờ đình chỉ / chấm dứt |

- Mặc định: tháng 1 năm nay → tháng hiện tại. **Test lỗi:** sai định dạng, `fromMonth` sau `toMonth`, hoặc quá 36 tháng → `400`; token khác Club Manager → `403`.

### 12F.8 Thanh toán phác đồ theo giai đoạn (BR_16, BR_17)

> **Chạy trước một lần:** `DATABASE_protocol_payment_hold.sql` (cho phép trạng thái `ON_HOLD_PAYMENT` trong `Treatment_Protocols`).

1. Bác sĩ xác nhận phác đồ (`PUT /medical-management/protocols/{id}/confirm`, mục 12) → **mỗi giai đoạn có thuốc được lập một hóa đơn** mã `GD-<protocol_id 6 số>-<phase_no 2 số>`, ví dụ `GD-000001-02`:
   - Dòng `MEDICATION` = số lượng × đơn giá trong `Protocol_Items` của giai đoạn đó. Giai đoạn không có thuốc thì không lập hóa đơn.
   - **Hạn thanh toán = ngày bắt đầu giai đoạn** (giai đoạn đã bắt đầu thì hạn là hôm nay). Chủ ngựa nhận email liệt kê các hóa đơn.
   - `GET /medical-management/protocols/{id}`: mỗi `phases[]` có `invoiceId`, `invoiceCode`, `invoiceStatus`; có thêm `emergency`.
2. **Tới ngày bắt đầu một giai đoạn mà hóa đơn của giai đoạn đó chưa thanh toán** → phác đồ chuyển `ON_HOLD_PAYMENT` (kiểm tra ngay khi xác nhận và hằng ngày lúc 00:20). Chủ ngựa và bác sĩ nhận email, ghi Audit Log `PROTOCOL_PAYMENT_HOLD`.
   - Khi đang tạm dừng: kho cấp thuốc (`PUT /inventory/protocols/{id}/dispense`) → `400` "...tạm dừng do chưa thanh toán giai đoạn tới hạn (BR_16)"; bác sĩ không ghi tiến trình được.
   - Bác sĩ vẫn được kết thúc phác đồ đã cấp thuốc (`complete`); phác đồ chưa cấp thuốc vẫn hủy hoặc tạo phiên bản mới được.
3. Chủ ngựa trả hóa đơn giai đoạn qua VNPay (12F.6). Khi giao dịch được xác minh, **phác đồ tự tiếp tục**: `IN_TREATMENT` nếu đã cấp thuốc, ngược lại `VET_CONFIRMED`. Ghi Audit Log `PROTOCOL_PAYMENT_RESUME`, báo bác sĩ.
4. **BR_17 – ca cấp cứu:** phác đồ có `caseId` trỏ tới ca khám `severity = CRITICAL` vẫn được lập hóa đơn nhưng **không bị tạm dừng** vì chưa thanh toán (`emergency = true`).
5. Phác đồ bị hủy hoặc có phiên bản mới → hóa đơn giai đoạn chưa trả chuyển `CANCELLED`. Kết thúc sớm → hủy hóa đơn của các giai đoạn chưa bắt đầu.

- Hóa đơn giai đoạn vẫn hiện trong danh sách hóa đơn của chủ ngựa / Club Manager và trong báo cáo (loại `MEDICATION`). Phí trễ hạn tính từ sau ngày bắt đầu giai đoạn. Mốc đình chỉ (BR_13) tính theo hạn thanh toán nên hóa đơn phát hành sớm không bị tính đủ 25 ngày quá sớm.

> **Lưu ý:** API cũ `POST /api/v1/owner/payments/medical-records/{recordId}/vnpay` vẫn là **giả lập** (không gọi VNPay), thuộc luồng bệnh án cũ.

---

## 13–17. Các API dữ liệu giả (mock)

Các API dưới đây **không đọc/ghi database**. Dữ liệu được code cứng hoặc nằm trong RAM. Chỉ cần kiểm tra là gọi được và trả đúng format. Tất cả yêu cầu đã login (role nào cũng được).

### 13. Pre-race Exam (khám trước đua) — dữ liệu trong RAM, mất khi restart

1. **`GET /api/v1/prerace-exams?tab=CHO_KHAM`** → 3 đề xuất: `DX-0009`, `DX-0008`, `DX-0007`.
2. **`GET /api/v1/prerace-exams?tab=DA_CO_KET_QUA`** → 2 đề xuất: `DX-0006`, `DX-0004`.
3. **`POST /api/v1/prerace-exams/DX-0009/examine`** với body:

   ```json
   { "isPassed": true, "vetNotes": "Tim mạch ổn định" }
   ```

   → `200`. Gọi lại bước 1 và 2: `DX-0009` đã chuyển sang tab `DA_CO_KET_QUA` với `resultStatus = "ĐẠT"`.

4. **Test lỗi:** khám lại `DX-0009`, hoặc dùng mã không tồn tại → `400` "Không tìm thấy mã đề xuất hoặc đề xuất đã được khám!".

### 14. Protocols

- **`GET /api/v1/protocols?tab=...`** → luôn trả 2 phác đồ cố định (`PD-0013`, `PD-0012`), bất kể tab.
- **`GET /api/v1/protocols/shipments`** → luôn trả 1 lô hàng `SHIP-0024`.

### 15. Vaccinations

- **`GET /api/v1/vaccinations?tab=upcoming`** → luôn trả 2 bản ghi cố định (`VC-036`, `VC-033`).
- **`POST /api/v1/vaccinations/VC-036/execute`** → luôn `200`, kể cả mã không tồn tại.
- **`POST /api/v1/vaccinations/schedule`** với body:

  ```json
  { "horseIds": [1, 2, 3], "medicineName": "Vaccine cúm ngựa", "scheduledDate": "2026-11-01", "notes": "Mũi nhắc lại" }
  ```

  → `200` "Đã tạo lịch Vaccine cúm ngựa thành công cho 3 ngựa.". Gửi `"horseIds": []` → `400` "Vui lòng chọn ít nhất một ngựa!".

---

## 18. Checklist phân quyền nhanh

Sau khi test chức năng chính, thử gọi mỗi API sau bằng **sai role** và xác nhận đều nhận `403`:

| API | Chỉ cho phép |
|---|---|
| `POST /auth/staff/create` | Club Manager |
| `PUT /manager/medical-records/{id}/approve` | Club Manager |
| `GET /training-plans/{planId}` | Trainer, Club Manager |
| `POST /training-plans`, `GET /training-plans/assignable-staff`, `POST /training-plans/{id}/sessions` | Trainer |
| `POST /nutrition-plans` | Trainer, Veterinarian |
| `POST /veterinary/medical-records` | Veterinarian |
| `POST /medical/release-lock/{horseId}` | Veterinarian |
| `/inventory/**` (kho, nhà cung cấp, đơn mua, kiểm kê, xử lý yêu cầu vật tư) | Warehouse Manager |
| `/supply-requests/**` (gửi, xem, hủy yêu cầu vật tư) | Groom |
| `POST /medical-management/protocols` và các thao tác trên phác đồ (confirm, wait-restock, new-version, progress-notes, complete, cancel) | Veterinarian |
| `GET /medical-management/medications`, `GET /medical-management/protocols/**` | Veterinarian, Club Manager (chỉ xem) |
| `POST /medical/vet-lock`, `POST /medical/cases` (Trainer), `POST /nutrition-plans`, `POST /care-tasks` | Trainer chỉ với ngựa được phân công |
| `POST /medical/vet-lock` | Veterinarian, Trainer, Groom |
| `GET /medical/cases` | Veterinarian (mọi ca), Groom và Trainer (ca của mình) |
| `POST /medical/cases` | Groom, Trainer |
| `POST /medical/cases/action`, `PUT /medical/cases/{caseId}/close` | Veterinarian |
| `POST /horses/register`, `GET /horses/my-horses/{ownerId}` | Horse Owner (chỉ của mình), Club Manager |
| `GET /horses` | Club Manager, Trainer (chỉ ngựa được phân công), Veterinarian, Groom |
| `GET /training-plans`, `GET /training-plans/dashboard-stats` | Club Manager, Trainer (chỉ ngựa được phân công), Groom, Veterinarian |
| `GET /horses/{id}` | Nhân viên, và Horse Owner (chỉ ngựa của mình) |
| `PUT /horses/{id}/approve`, `PUT /horses/{id}/reject`, `PUT /horses/{id}/assign-trainer` | Club Manager |
| `PUT /horses/{id}/resubmit` | Horse Owner (chỉ ngựa của mình) |
| `GET /horses/{id}/registrations` | Club Manager, Horse Owner (chỉ ngựa của mình) |
| `PUT /horses/{id}` | Club Manager, Trainer, Veterinarian (đổi `isLocked` hoặc `healthStatus`: chỉ Veterinarian) |
| `GET /horses/available-for-training` | Trainer, Club Manager |
| `POST /care-tasks` | Trainer |
| `GET /care-tasks/my-tasks`, `PUT /care-tasks/{id}/complete` | Groom |
| `GET /care-tasks/overdue` | Club Manager |
| `/training-sessions/**` | Trainer, Groom (`PUT /training-sessions/{id}/confirm`: chỉ Groom được gán) |
| `/owner/training/**`, `/owner/payments/**`, `/owner/invoices/**` | Horse Owner |
| `/billing/**`, `GET /reports/finance` | Club Manager |
| `GET /payments/vnpay/ipn`, `GET /payments/vnpay/return` | Không cần đăng nhập (kiểm tra chữ ký VNPay) |

Mọi API khác (trừ login/register/refresh/logout) chỉ cần đăng nhập. Gọi mà không Authorize phải nhận `401`.

---

## 19. Lỗi và điểm cần lưu ý phát hiện khi đọc code

Đây là những chỗ có thể làm bạn tưởng mình test sai. Thực ra là do code hoặc DB.

- **L1. (Đã kiểm tra lại: không còn là lỗi.)** DB thật cho phép role `Trainer` (và `Warehouse Manager`). Script `DATABASE` trong repo đã cũ so với DB thật: các giá trị CHECK đều đã đổi sang dạng `HEALTHY`, `CLEAN`, `FEED`... Nên đối chiếu với DB thật thay vì script.
- **L2. (Đã sửa.)** Bệnh án có `isLockoutOrdered: true` trước đây gán `healthStatus = "VetLock = ACTIVE"` (không hợp lệ) nên lỗi `500`. Giờ gán `INJURED`.
- **L3. (Đã sửa.)** Kê thuốc vượt tồn kho trước đây trả `500` do `String.format` dùng `%d` cho kiểu `double`. Giờ trả `400` đúng thông báo. Câu truy vấn hàng sắp về cũng đã được viết lại theo bảng thật `Purchase_Orders` + `Purchase_Order_Lines` (trước đó lỗi `Invalid column name 'item_id'`).
- **L4. Lỗi nghiệp vụ trả `500`.** Service ném `RuntimeException` ("Horse not found", "Old password is incorrect", duyệt lại hồ sơ...). `GlobalExceptionHandler` không bắt riêng loại này, nên chúng rơi vào handler chung → `500 "Lỗi máy chủ: ..."`. Đúng ra nên là `400` hoặc `404`.
- **L5. (Đã sửa.)** `my-profile` trước đây kiểm tra cả role `HEAD_TRAINER`. Hệ thống đã bỏ role này, chỉ còn `Trainer`.
- **L6. Một số API vẫn chưa phân quyền.** Các API mock (khám trước đua, protocols, vaccinations) vẫn cho **mọi user đã login** gọi. Các API ngựa, `/medical/**` và `/medical-management/**` đã được phân quyền (xem mục 18).
- **L7. Thiếu API tạo dữ liệu.** Đã có API tạo Training Plan (6.0) và Care Task (9.0). Đã có module kho cho nhân viên kho (12A–12D). Đã có kiểm kho và cấp thuốc cho phác đồ (12E). Vẫn chưa có API tạo Stall.
- **L8. `AuditController` và `AudtitController` là class rỗng.** Hai class này không có endpoint nào và tên sau bị gõ sai chính tả.
