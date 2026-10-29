# Hướng dẫn test API HorseTrack bằng Swagger

Swagger UI: http://localhost:8080/swagger-ui/index.html

Tổng cộng **165 endpoint**, chia thành 23 nhóm. Một số endpoint có thêm bản sao dưới `/api/...` (không có `v1`), ví dụ `/api/medical/vet-lock`. Hai bản chạy giống hệt nhau, chỉ cần test bản `/api/v1/...`.

> **Bắt buộc trước khi chạy bản backend này:** chạy `DATABASE_fe_requests_schema.sql` trên DB (thêm cột ngưỡng / tạm dừng cho buổi tập, `Session_Readings.session_id`, chấn thương gắn với ngựa, `Users.avatar_url`, `Competition_Entries.proposal_note`). Chưa chạy script thì các API buổi tập, chấn thương, hồ sơ cá nhân, đề xuất thi đấu trả `500` "Invalid column name".
>
> **Và `DATABASE_email_logs.sql`** (bảng nhật ký gửi email `Email_Logs`). Chưa chạy thì email vẫn gửi bình thường, chỉ không ghi được nhật ký và `GET /email-logs` trả `500`.
>
> **Và `DATABASE_staff_email_confirmation.sql`** (cột xác nhận email của `Users`; tài khoản đã có được coi là đã xác nhận). Chưa chạy thì đăng nhập và mọi API đọc người dùng trả `500` "Invalid column name 'email_verified_at'".
>
> **Và `DATABASE_owner_horse_registration.sql`** (cột `Horses.height_cm`, `Registration_Attachments.file_content`, chỉ mục số điện thoại không trùng `UX_Users_phone`). Chưa chạy thì API ngựa và giấy tờ hồ sơ trả `500` "Invalid column name".
>
> **Và `DATABASE_qa_concurrency.sql`** (cột `version` cho `Training_Sessions`, `Training_Plans`, `Horses`, `Invoices` + chỉ mục `UX_plan_open_per_horse`). Chưa chạy thì mọi API đọc ngựa, giáo án, buổi tập, hóa đơn trả `500` "Invalid column name 'version'".
>
> **Mật khẩu DB và JWT secret không còn trong `application.properties`.** Máy cá nhân: sao chép `config/secrets.example.properties` thành `config/secrets.properties` (đã gitignore) và điền `DB_PASSWORD`, `JWT_SECRET`; hoặc đặt biến môi trường cùng tên. Thiếu thì server không khởi động ("Could not resolve placeholder 'DB_PASSWORD'"). Server: đặt GitHub Secrets `DB_PASSWORD`, `JWT_SECRET` (tùy chọn `DB_URL`, `DB_USERNAME`); thiếu thì bước deploy dừng trước khi gỡ container đang chạy.

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
| 409 | Hai người cùng sửa một dòng ("Dữ liệu vừa được người khác cập nhật. Vui lòng tải lại và thử lại."), hoặc 2 yêu cầu song song cùng lập giáo án cho một ngựa |
| 500 | `"Lỗi máy chủ: ..."`. **Chú ý:** nhiều lỗi nghiệp vụ như "Horse not found" hiện cũng trả 500 (xem L4) |

### 0.4 Thứ tự test khuyến nghị

Các chức năng phụ thuộc dữ liệu của nhau, nên test theo thứ tự:

```
Auth (1) → Account (2) → Stall (4) → Horse (3) → Nutrition (5)
       → Training (6, 7, 8) → Care (9) → Medical (10 → 12) → Thi đấu (13) → Mock (15 → 17)
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

### 1.1 `POST /api/v1/auth/register-owner` — Chủ ngựa tự đăng ký (xác thực email bằng OTP, 2 bước)

**Bước 1:** gửi thông tin, **không có `otp`**:

```json
{
  "username": "owner01",
  "password": "owner123",
  "fullName": "Nguyen Van Owner",
  "email": "owner01@gmail.com",
  "phone": "0901234567"
}
```

- **Mong đợi:** `200` "Đã gửi mã OTP tới ow*****@gmail.com...", `data = { otpRequired: true, email, ttlSeconds }`. **Chưa tạo tài khoản.** FE hiển thị ô nhập OTP.
- Trước khi gửi mã: kiểm tra trùng **tên đăng nhập, email, số điện thoại** (không kiểm tra trùng họ tên); tên miền email phải nhận được thư (bản ghi MX/A, tắt bằng `app.mail.verify-domain=false`); máy chủ thư từ chối gửi → `400` "Không gửi được mã OTP tới ... Email có thể không tồn tại". Chưa cấu hình `MAIL_USERNAME` thì mã chỉ in ra log server.
- Gọi lại bước 1 để **gửi lại mã** (cách nhau tối thiểu `app.otp.resend-cooldown-seconds`, mặc định 30 giây).

**Bước 2:** gửi lại **cùng thông tin kèm `"otp": "123456"`** (mã trong email, hiệu lực 5 phút, sai tối đa 5 lần).

- **Mong đợi:** `201` "Đăng ký tài khoản chủ ngựa thành công!". `phone` được chuẩn hóa (`+84 912 345 678` → `0912345678`). Ghi nhật ký `REGISTER_OWNER`.
- **Test lỗi:**
  - Sai OTP → `400` "Mã OTP không đúng. Bạn còn ... lần thử."; hết hạn → `400`.
  - Email tên miền không có thật (ví dụ `abc@gmail.con`) → `400` "Email ... không có thật: tên miền ... không nhận thư".
  - `phone` bỏ trống hoặc sai định dạng → `400` (số điện thoại bắt buộc, dùng cho hồ sơ ngựa).
  - `username` có dạng số điện thoại (ví dụ `0912345678`) → `400`.
  - Họ tên trùng với người khác → **vẫn đăng ký được**.
  - Gửi lại y hệt sau khi đã tạo → `400` "Tên đăng nhập đã tồn tại."
  - Đổi username nhưng giữ email → `400` "Email đã được sử dụng."
  - Số điện thoại đã có tài khoản khác dùng (GEN-01) → `400` "Số điện thoại đã được sử dụng." (áp dụng cả khi tạo nhân viên ở 1.3).
  - Bỏ trống `fullName` hoặc `email: "abc"` → `400`, `data` chỉ ra field lỗi.
  - Mật khẩu yếu: dưới 8 ký tự (`"abc1234"`), không có số (`"abcdefgh"`), không có chữ (`"12345678"`), hoặc có dấu cách → `400` "Mật khẩu phải dài 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số, không chứa khoảng trắng".
  - Tên đăng nhập không hợp lệ: dưới 4 hoặc trên 30 ký tự, có dấu tiếng Việt (`"toàn"`), có dấu cách (`"Toan ne"`), hoặc có ký tự khác ngoài chữ, số, `.`, `_` (`"user-01"`) → `400`.

### 1.2 `POST /api/v1/auth/login` — Đăng nhập

```json
{ "identifier": "owner01@gmail.com", "password": "owner123" }
```

- `identifier` nhận **email, số điện thoại hoặc tên đăng nhập** (`"owner01@gmail.com"`, `"0901234567"`, `"+84901234567"`, `"owner01"` đều được).
- Vẫn nhận khóa cũ `username` (`{ "username": "0901234567", "password": "..." }`) để FE chưa đổi không bị lỗi.
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
  "email": "groom01@gmail.com",
  "phone": "0907654321",
  "roleName": "Groom",
  "licenseNumber": null
}
```

- `roleName` chỉ nhận `Trainer`, `Veterinarian`, `Groom` hoặc `Warehouse Manager`.
- `username`, `password` **bắt buộc** (Club Manager nhập). Nhân viên đăng nhập bằng tên đăng nhập, email hoặc số điện thoại cùng mật khẩu.
- **Groom không còn được gán khu chuồng lúc tạo** (`specialization` bị bỏ qua với Groom); phân công khu bằng 3.9 sau khi Groom xác nhận.
- **Mong đợi:** `201` "Tạo tài khoản nhân viên thành công! Nhân viên cần nhấn \"Xác nhận tài khoản\"... Đã gửi email xác nhận tới groom01@gmail.com."
- **Email gửi nhân viên:** vai trò, nút **"Xác nhận tài khoản"** (hiệu lực `app.staff.confirm-ttl-hours` = 72 giờ), **tên đăng nhập, email và mật khẩu**, lời nhắc đổi mật khẩu sau lần đăng nhập đầu.
  - Chế độ dev: liên kết xác nhận và mật khẩu in trong console server.
  - SMTP lỗi: tài khoản **vẫn được tạo**, message báo "KHÔNG gửi được email..." → dùng gửi lại (dưới).
- **Chưa xác nhận:**
  - Đăng nhập → `403` "Tài khoản chưa được xác nhận. Hãy mở email từ HorseTrack và nhấn \"Xác nhận tài khoản\"."
  - **HLV / Groom chưa được phân công** → `400` "Nhân viên ... chưa xác nhận tài khoản qua email, chưa thể ...": phân công HLV cho ngựa (3.8), khu chuồng cho Groom (3.9), buổi tập (6.5), việc chăm sóc (9.0). Groom chưa xác nhận không hiện trong 6.4 và `GET /horses/{id}/grooms`, không nhận việc cho ăn tự động.
  - `GET /users`, `GET /users/{id}` có `emailConfirmed` (false) và `emailVerifiedAt` để FE hiển thị "Chờ xác nhận" và khóa nút phân công.
- **Xác nhận:** nhân viên nhấn nút trong email → `GET /api/v1/auth/confirm-account?token=...` (không cần đăng nhập) → trang "Xác nhận thành công"; đặt `STAFF_LOGIN_URL` thì chuyển về trang đăng nhập FE kèm `?confirmed=true&message=...`. FE tự làm trang xác nhận thì gọi `POST /api/v1/auth/confirm-account` `{ "token": "..." }`. Liên kết chỉ dùng 1 lần; sai / đã dùng / hết hạn → `400`.
- **Gửi lại email xác nhận:** `POST /api/v1/users/{id}/resend-confirmation` (`Club Manager`): gửi lại tên đăng nhập, **mật khẩu tạm mới** (hệ thống không lưu mật khẩu gốc nên không gửi lại được mật khẩu cũ) và liên kết mới (liên kết cũ hết hiệu lực). Nhân viên đã xác nhận → `400`.
- Khi triển khai: đặt `STAFF_CONFIRM_URL` = địa chỉ công khai, ví dụ `http://103.245.237.252:8080/api/v1/auth/confirm-account` (mặc định `localhost` chỉ mở được trên máy chạy backend).
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

- **Mong đợi:** `200`, thông tin của user đang đăng nhập (`username`, `roleName`, `avatarUrl`...).
- **Test lỗi:** không Authorize → `401`.

### 2.1A `PUT /api/v1/account/my-profile`: sửa thông tin của chính mình

```json
{ "fullName": "Nguyễn Văn A", "phone": "0912345678" }
```

- Chỉ đổi được họ tên và số điện thoại (không đổi username, email, vai trò). `phone` để trống `""` để xóa số.
- **Mong đợi:** `200`, trả lại hồ sơ đã cập nhật. Ghi nhật ký `UPDATE_PROFILE`.
- **Test lỗi:** `fullName` trống hoặc 1 ký tự → `400`; `phone` sai định dạng (ví dụ `123`) → `400`; số điện thoại đã thuộc tài khoản khác → `400` "Số điện thoại ... đã được tài khoản khác sử dụng".

### 2.1B `POST /api/v1/account/avatar`: ảnh đại diện

- Body `multipart/form-data`, trường `file`. Chỉ nhận PNG, JPEG, WEBP, tối đa 1 MB.
- **Mong đợi:** `200`, `avatarUrl` dạng `data:image/png;base64,...` (FE gán thẳng vào `<img src>`). `GET /my-profile` trả cùng giá trị.
- **Test lỗi:** không chọn file → `400`; file GIF / PDF → `400` "Ảnh đại diện phải là PNG, JPEG hoặc WEBP"; file trên 1 MB → `400`.

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

### 2B.8 `GET /api/v1/email-logs` — Nhật ký gửi email (`Club Manager`)

Mọi email hệ thống gửi (OTP, tài khoản nhân viên, hóa đơn, phân công, cảnh báo...) đều được ghi vào bảng `Email_Logs`, kể cả khi gửi thất bại.

- Tham số lọc (đều tùy chọn): `userId`, `purpose`, `status`, `toEmail` (chứa chuỗi, không phân biệt hoa thường), `fromDate`, `toDate` (`yyyy-MM-dd`), `page` (mặc định 0), `size` (mặc định 20, tối đa 100). Ví dụ: `GET /api/v1/email-logs?userId=49&purpose=OTP`.
- Mỗi dòng: `logId, toEmail, userId, userFullName, purpose, subject, status, error, sentAt`, mới nhất trước.
- `status`: `SENT` (gửi thành công), `FAILED` (máy chủ thư báo lỗi, `error` ghi lý do), `SKIPPED` (server chưa cấu hình `MAIL_USERNAME`, chỉ in ra console, không gửi thật).
- `purpose`: `OTP`, `STAFF_ACCOUNT`, `BILLING`, `INVOICE`, `TRAINER_ASSIGNMENT`, `GROOM_ZONE`, `CARE_TASK_OVERDUE`, `NUTRITION`, `RACE_PROPOSAL`, `TRAINING_INCIDENT`, `TRAINING_PAUSE`, `VET_LOCK`.
- `userId` gắn theo email người nhận; email chưa có tài khoản (ví dụ OTP đăng ký chủ ngựa) thì `userId = null`.
- **Test lỗi:** `status` khác 3 giá trị trên → `400`; `toDate` trước `fromDate` → `400`; role khác Club Manager → `403`.

### 2B.7 `GET /api/v1/audit-logs` — Xem nhật ký kiểm toán hệ thống (BR_10)
- **Mong đợi:** `200`, danh sách các hành động gần nhất trên hệ thống (ai thực hiện, thao tác gì, bảng nào, giá trị cũ/mới, IP, thời gian).
- `actionType` giữ đủ tối đa 50 ký tự (trước đây bị cắt còn 20, ví dụ "CREATE_NUTRITION_PLA"). Bản ghi cũ đã bị cắt vẫn giữ nguyên.

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
  "heightCm": 165,
  "color": "Hồng",
  "note": "Ngựa nhập từ Úc"
}
```

- **Horse Owner không cần gửi `ownerId`** (lấy tài khoản đang đăng nhập). Club Manager đăng ký hộ thì bắt buộc `ownerId`.
- **Số điện thoại chủ ngựa lấy từ hồ sơ tài khoản**, không nhập trên hồ sơ ngựa; response có `ownerPhone`. Tài khoản chưa có số điện thoại → `400` (cập nhật bằng `PUT /account/my-profile`).
- **Chiều cao thống nhất theo cm:** `heightCm` 120–205. Response trả `heightCm`. (FE cũ gửi `heightHands` vẫn được nhận và đổi sang cm, 1 hand = 10.16 cm.)
- **Đã bỏ hộ chiếu và giấy chứng nhận sức khỏe**: `passportNo`, `healthCertIssuer`, `healthCertExpiry` không còn trong request / response.
- Giấy tờ sở hữu đính kèm bằng 3.11 sau khi đăng ký.
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
- `data` gồm `horses`, `currentPage`, `totalPages`, `totalElements`. Mỗi ngựa có thêm `ownerName`, `assignedTrainerId`, `assignedTrainerName`.
- **Trainer:** chỉ thấy ngựa được phân công cho mình (3.8); `status=PENDING` hoặc `REJECTED` trả danh sách rỗng.

### 3.4 `GET /api/v1/horses/{id}`

Club Manager, Veterinarian, Groom xem được mọi ngựa. Horse Owner xem ngựa của người khác → `403` "Bạn không phải chủ của ngựa này".
- **Phạm vi Trainer:** Trainer chỉ nhận ngựa được Club Manager phân công cho mình (3.8). Ngựa ngoài phạm vi → `403` "Bạn không được Club Manager phân công phụ trách ngựa ...".

- **Mong đợi:** `200`, chi tiết ngựa, gồm `ownerName` và `injuries[]` (danh sách chấn thương, xem 3.10).
- `id` không tồn tại → `404`.

### 3.5 Hồ sơ đăng ký ngựa: duyệt, từ chối, nộp lại

Mỗi lần chủ ngựa nộp (3.1) hoặc nộp lại tạo một **hồ sơ** trong bảng `Horse_Registrations`, mã `REG-0001`. Luồng: `PENDING` → `APPROVED` (cấp chuồng), hoặc `REJECTED` (kèm lý do, có thể cho nộp lại) → nộp lại → `PENDING` …

Khi đăng ký (3.1), có thể gửi thêm các field tùy chọn: `color` (màu lông), `heightCm` (chiều cao, cm), `note`. Giấy tờ đính kèm xem ở 3.11.

**a) `PUT /api/v1/horses/{id}/approve`: duyệt và cấp chuồng (`Club Manager`)**

```json
{
  "stallId": 1,
  "checks": [
    { "checkKey": "IDENTITY", "passed": true },
    { "checkKey": "OWNERSHIP", "passed": true, "detail": "Hợp đồng mua bán hợp lệ" }
  ],
  "note": "Hồ sơ đầy đủ"
}
```

- `checks` tùy chọn. Nếu gửi thì phải đủ 2 mục `IDENTITY`, `OWNERSHIP` (không còn mục giấy sức khỏe) và **tất cả đều đạt** (SRS). Trước khi duyệt, xem giấy tờ chủ ngựa đính kèm ở 3.11. Có mục `passed: false` → `400`, hãy từ chối thay vì duyệt.
- Lấy `stallId` có `currentHorseId = null` từ mục 4.1.
- **Mong đợi:** `200`, `approvalStatus = "APPROVED"`, có `stallCode`.
- **Test lỗi:** hồ sơ không ở trạng thái chờ duyệt (đã duyệt hoặc đã từ chối) → `400`; chuồng đã có ngựa → `400`.
- **Chuồng phải `CLEAN` (F1-05):** chuồng `NEEDS_CLEANING` hoặc `MAINTENANCE` → `400` "Chỉ xếp ngựa vào chuồng CLEAN". Duyệt và từ chối hồ sơ đều được ghi Audit Log (`APPROVE_HORSE`, `REJECT_HORSE`).

**b) `PUT /api/v1/horses/{id}/reject`: từ chối (`Club Manager`)**

```json
{
  "reason": "Giấy tờ sở hữu không rõ chữ ký bên bán",
  "allowResubmit": true,
  "checks": [ { "checkKey": "OWNERSHIP", "passed": false, "detail": "Thiếu chữ ký" } ]
}
```

- `reason` bắt buộc, dài 10–500 ký tự (SRS). `allowResubmit: true` thì chủ ngựa được sửa và nộp lại.
- **Mong đợi:** `200`, `approvalStatus = "REJECTED"`, `rejectReason`, `allowResubmit`. Chủ ngựa thấy trạng thái này ở "Ngựa của tôi" (3.2, 8.1).
- **Test lỗi:** `reason` dưới 10 ký tự → `400`.

**c) `PUT /api/v1/horses/{id}/resubmit`: chủ ngựa nộp lại (`Horse Owner`, chỉ ngựa của mình)**

```json
{ "heightCm": 166, "note": "Đã bổ sung hợp đồng có chữ ký" }
```

- Field nào gửi lên mới được cập nhật: `weightKg`, `pedigree`, `color`, `heightCm`.
- Hồ sơ mới chưa có giấy tờ: tải lại giấy tờ bằng 3.11 sau khi nộp lại.
- **Mong đợi:** `200`, `approvalStatus` quay về `"PENDING"`, có hồ sơ mới (`registrationCode` mới).
- **Test lỗi:**
  - Hồ sơ bị từ chối với `allowResubmit: false` → `400` "Quản lý CLB không cho phép nộp lại hồ sơ này".
  - Hồ sơ đang chờ duyệt hoặc đã duyệt → `400`.
  - Nộp lại ngựa của người khác → `403`.

**d) `GET /api/v1/horses/{id}/registrations`: lịch sử hồ sơ (`Club Manager`, `Horse Owner` chỉ ngựa của mình)**

- **Mong đợi:** `200`, mỗi lần nộp gồm `registrationCode`, `submittedAt`, `status`, `ownerNote`, `decisionNote`, `decidedByName`, `decidedAt` và `checks`, mới nhất trước.

**e) Lọc danh sách (3.3):** `GET /api/v1/horses?status=PENDING | APPROVED | REJECTED`.

### 3.11 Giấy tờ đính kèm hồ sơ đăng ký ngựa — `Registration_Attachments`

- **`POST /api/v1/horses/{horseId}/registration-attachments`** (`Horse Owner` của ngựa): `multipart/form-data`, trường `file` (PDF / JPG / PNG, tối đa 10 MB, tối đa 10 tệp mỗi hồ sơ), `docType` = `OWNERSHIP` (mặc định) hoặc `OTHER`. Chỉ khi hồ sơ mới nhất đang `PENDING_REVIEW`. Hệ thống kiểm tra **nội dung thật** của tệp (không tin đuôi tệp). **Mong đợi:** `201`, `{ attachmentId, registrationId, registrationCode, docType, fileName, mimeType, fileSizeKb, fileUrl, uploadedAt }`.
  - Lỗi: tệp không phải PDF/JPG/PNG (kể cả đổi đuôi) → `400`; trên 10 MB → `400`; `docType` khác `OWNERSHIP`/`OTHER` → `400`; hồ sơ đã duyệt / từ chối → `400`; ngựa của người khác → `403`.
- **`GET /api/v1/horses/{horseId}/registration-attachments`** (`Club Manager`, chủ ngựa): mọi giấy tờ của mọi hồ sơ, mới nhất trước. Chủ ngựa khác → `403`. `GET /horses/{id}/registrations` (lịch sử hồ sơ) cũng trả `attachments[]` theo từng hồ sơ.
- **`GET /api/v1/horses/registration-attachments/{attachmentId}/file`** (`Club Manager`, chủ ngựa): trả nội dung tệp để xem trên trình duyệt; `?download=true` để tải về. FE gọi kèm header `Authorization` (ví dụ `fetch` → `blob` → `URL.createObjectURL`).
- **`DELETE /api/v1/horses/{horseId}/registration-attachments/{attachmentId}`** (`Horse Owner`): gỡ tệp khi hồ sơ còn chờ duyệt; đã xử lý → `400`.

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

**Bỏ phân công HLV:** `DELETE /api/v1/horses/{id}/assign-trainer` (`Club Manager`)

- **Mong đợi:** `200`, `data.assignedTrainerId = null`. HLV cũ nhận email, ghi Audit Log `UNASSIGN_TRAINER`.
- **Test lỗi:** ngựa chưa có HLV → `400`; ngựa còn giáo án `PENDING` / `ACTIVE` / `PAUSED` → `400` "...Hãy phân công HLV khác để bàn giao giáo án, hoặc kết thúc / hủy giáo án trước..."; token khác Club Manager → `403`.

### 3.9 Phân công / đổi / bỏ khu chăm sóc của Groom (`Club Manager`)

- `GET /api/v1/users/{id}/barn-zone`: khu hiện tại của Groom (`barnZone = null` là chưa phân công).
- `PUT /api/v1/users/{id}/barn-zone`:
  ```json
  { "barnZone": "Khu B" }
  ```
  Gửi `{ "barnZone": null }` (hoặc chuỗi rỗng) để **bỏ phân công**.
- **Mong đợi:** `200`, `data.barnZone` là khu mới (tên khu không phân biệt hoa thường, trả về đúng tên trong DB). Groom nhận email; ghi Audit Log `ASSIGN_GROOM_ZONE` / `UNASSIGN_GROOM_ZONE`.
- **Test lỗi:**

| Trường hợp | Kết quả |
|---|---|
| `id` không phải Groom | `400` |
| Khu không có trong bảng `Stalls` | `400` "Khu ... không tồn tại. Các khu hiện có: ..." |
| Gửi đúng khu hiện tại / bỏ phân công khi chưa có khu | `400` |
| Groom còn việc chăm sóc (`TODO`, `IN_PROGRESS`, từ hôm nay) hoặc buổi tập chưa thực hiện ở khu hiện tại | `400` "...còn X việc chăm sóc và Y buổi tập chưa thực hiện..." |
| `id` không tồn tại | `404` |

### 3.10 Hồ sơ sức khỏe và Groom theo khu của ngựa

**Chấn thương (sơ đồ 2D):**

- `GET /api/v1/horses/{id}/injuries` (Club Manager, Trainer phụ trách, Veterinarian, Groom): danh sách chấn thương, mới nhất trước. Cũng có trong `GET /horses/{id}` (`injuries[]`).
- `POST /api/v1/horses/{id}/injuries` (`Veterinarian`):

  ```json
  { "part": "Chân trước trái", "severity": "Trung bình", "note": "Sưng nhẹ", "x": 30.5, "y": 60, "caseId": "C-0012" }
  ```

  - `severity`: `Nhẹ` / `Trung bình` / `Nặng` hoặc `MINOR` / `MODERATE` / `SEVERE`. `x`, `y` là phần trăm 0–100 trên sơ đồ. `caseId` tùy chọn: ID ca (`12`) hoặc mã ca (`C-0012`) của chính ngựa này.
  - **Mong đợi:** `201`, `{ injuryId, part, severity, severityLabel, note, x, y, caseId, caseCode, createdByName, createdAt }`; nhật ký `MARK_INJURY`.
  - **Test lỗi:** `severity` lạ → `400`; `x` > 100 → `400`; ca của ngựa khác → `400`; ca không tồn tại → `404`; Trainer / Groom gọi → `403`.
- `DELETE /api/v1/horses/{id}/injuries/{injuryId}` (`Veterinarian`): gỡ đánh dấu; nhật ký `REMOVE_INJURY`. Chấn thương thuộc ngựa khác → `404`; chấn thương ghi trong bệnh án (11.1) → `400`.

**`PUT /api/v1/horses/{id}/health-status`** (`Veterinarian`):

```json
{ "status": "WATCH", "reason": "Theo dõi sau khi chạy 2000m" }
```

- `status`: `HEALTHY`, `WATCH`, `INJURED`, `QUARANTINE` (hoặc "Khỏe mạnh", "Theo dõi", "Chấn thương", "Cách ly"). **Mong đợi:** `200`, `data` là trạng thái mới; nhật ký `UPDATE_HEALTH_STATUS` kèm lý do. Không đổi khóa huấn luyện (khóa dùng 10.1).
- **Test lỗi:** trạng thái lạ hoặc trùng trạng thái hiện tại → `400`; thiếu `reason` → `400`; role khác → `403`.

**`GET /api/v1/horses/{id}/grooms`** (`Trainer` phụ trách ngựa, `Club Manager`): Groom đang hoạt động phụ trách khu chuồng của ngựa, dùng để chọn `groomId` khi giao việc chăm sóc (9.0) hoặc `assignedToId` khi phân công buổi tập (6.5). Mỗi phần tử `{ userId, fullName, barnZone }`. Ngựa chưa có chuồng → `400`; Trainer không phụ trách ngựa → `403`.

---

## 4. Stall — `GET /api/v1/stalls` (mọi role đã login)

- **Mong đợi:** `200`, sơ đồ chuồng gồm `stallId`, `stallCode`, `barnZone`, `cleanStatus`, `currentHorseId`, `horseName`, `isLocked`.
- Dùng để tìm chuồng trống cho mục 3.5. Sau khi duyệt, gọi lại để thấy ngựa đã vào chuồng.

### 4.1 `PUT /api/v1/stalls/{id}/clean-status` (`Groom` chuồng trong khu mình, `Club Manager`)

```json
{ "status": "NEEDS_CLEANING", "note": "Rơm ướt" }
```

- `status`: `CLEAN`, `NEEDS_CLEANING`, `MAINTENANCE`. **Mong đợi:** `200`, chuồng sau cập nhật; ghi nhật ký `UPDATE_STALL_STATUS`.
- Ngoài ra, khi Groom hoàn thành việc dọn chuồng (`taskType = CLEAN`, 9.2) chuồng **tự chuyển `CLEAN`** (trừ chuồng đang `MAINTENANCE`).
- **Test lỗi:** Groom đổi chuồng khu khác → `403`; trạng thái giống hiện tại → `400`; `status` khác 3 giá trị → `400`; Trainer / Owner gọi → `403`.

---

## 5. Nutrition — Khẩu phần dinh dưỡng & đề xuất thức ăn

**Ai lập khẩu phần (BR_03):** ngựa khỏe → **HLV phụ trách ngựa** (khẩu phần `STANDARD`); ngựa đang khóa huấn luyện / chấn thương → **Bác sĩ thú y** (khẩu phần `MEDICAL`). Club Manager chỉ xem.

**Ai xem được:** Trainer chỉ ngựa được phân công (3.8); Horse Owner chỉ ngựa của mình; Groom chỉ ngựa trong khu phụ trách (3.9); Bác sĩ thú y và Club Manager xem mọi ngựa. Ngoài phạm vi → `403`.

### 5.1 `POST /api/v1/nutrition-plans` — Lập khẩu phần (`Trainer`, `Veterinarian`)

```json
{
  "horseId": 6,
  "startDate": "2026-10-22",
  "endDate": "2026-10-31",
  "notes": "Khẩu phần tăng sức bền",
  "details": [
    { "inventoryItemId": 1, "mealType": "MORNING", "quantity": 3.5 },
    { "inventoryItemId": 1, "mealType": "NOON", "quantity": 2 },
    { "inventoryItemId": 2, "mealType": "EVENING", "quantity": 2 }
  ]
}
```

- `mealType`: `MORNING` (07:00), `NOON` (11:30), `EVENING` (17:00). `AFTERNOON` vẫn được nhận và lưu thành `NOON`. Giờ từng bữa chỉnh bằng `app.nutrition.*-time`.
- **Mong đợi:** `201`, `data` là `planId`, `status = ACTIVE`, `planType` theo BR_03. Ghi Audit Log `CREATE_NUTRITION_PLAN`.
- **Thay thế:** mỗi ngựa chỉ có một khẩu phần áp dụng tại một thời điểm. Khẩu phần mới giao thời gian với khẩu phần đang áp dụng thì khẩu phần cũ **kết thúc trước ngày khẩu phần mới bắt đầu** (hoặc bị hủy nếu chưa bắt đầu); việc cho ăn của khẩu phần cũ từ ngày đó bị hủy.
- **Gửi cho nhân viên:** khi lập (và mỗi ngày lúc 00:05), mỗi bữa của khẩu phần thành một **việc cho ăn** (`FEED`) giao cho Groom phụ trách khu chuồng của ngựa, kèm định lượng từng món (`instructions`) và lưu ý đặc biệt (`dietNote`). Hôm nay chỉ gửi các bữa chưa tới giờ, **đồng thời gửi luôn các bữa của ngày mai** (lập khẩu phần buổi tối thì Groom vẫn thấy lịch ngày mai ngay). Áp dụng giống nhau cho khẩu phần thường của HLV và khẩu phần y tế của bác sĩ (việc có `medicalDiet = true`, tiêu đề "(khẩu phần y tế)"). Groom xem ở `GET /care-tasks/my-tasks` (9.1), ngày mai: `?date=yyyy-MM-dd`.

| Trường hợp | Kết quả |
|---|---|
| Ngựa khỏe, Trainer phụ trách | `201` |
| Ngựa khỏe, Veterinarian / Club Manager | `403` "...Khẩu phần do HLV phụ trách ngựa lập (BR_03)" |
| Ngựa đang khóa, Trainer | `403` "...Chỉ Bác sĩ thú y được lập khẩu phần lúc này (BR_03)" |
| Ngựa đang khóa, Veterinarian | `201`, `planType = MEDICAL` |
| Trainer không phụ trách ngựa | `403` |
| Ngựa chưa có chuồng; `endDate` trước `startDate` hoặc ở quá khứ; **`startDate` ở quá khứ** (sửa khẩu phần: chỉ được giữ nguyên ngày bắt đầu cũ, không đổi sang ngày đã qua); `details` rỗng; `mealType` sai | `400` |
| Món không phải thức ăn (thuốc kê đơn, thuốc tiêm / bôi, vaccine, vật tư y tế) | `400` "...không phải thức ăn. Chọn món trong danh sách GET /nutrition-plans/feed-items..." |
| `horseId` / `inventoryItemId` không tồn tại | `404` |

### 5.2 Xem khẩu phần

- `GET /api/v1/nutrition-plans/feed-items` (Trainer, Veterinarian, Groom, Club Manager): các món được dùng trong khẩu phần — nhóm Thức ăn / Thức ăn bổ sung, hoặc sản phẩm bổ sung / tiêu hóa đường uống không cần kê đơn.

- `GET /api/v1/horses/{horseId}/nutrition-plan/today`: các món của khẩu phần **đang áp dụng** hôm nay (khẩu phần đã hủy / kết thúc không hiện). Không có → `data: []`.
- `GET /api/v1/nutrition-plans?horseId=&status=` (`ACTIVE`, `ENDED`, `CANCELLED`): danh sách, tự lọc theo phạm vi người gọi.
- `GET /api/v1/nutrition-plans/{planId}`: chi tiết (món theo bữa, `planType`, `status`, `specialInstructions`).

### 5.3 Sửa / hủy / xóa / gửi lại (`Trainer` với khẩu phần thường của ngựa khỏe; `Veterinarian` với khẩu phần y tế hoặc ngựa đang khóa)

- `PUT /api/v1/nutrition-plans/{planId}` (body như 5.1): chỉ khẩu phần `ACTIVE`, không đổi ngựa. Việc cho ăn chưa làm được hủy và gửi lại theo định lượng mới.
- `PUT /api/v1/nutrition-plans/{planId}/cancel`: `ACTIVE` → `CANCELLED`, hủy các việc cho ăn chưa làm.
- `DELETE /api/v1/nutrition-plans/{planId}`: chỉ xóa được khẩu phần **chưa bắt đầu** và chưa có đề xuất gắn vào; còn lại `400` → dùng hủy.
- `POST /api/v1/nutrition-plans/{planId}/feed-tasks?date=2026-10-23`: gửi (lại) việc cho ăn của một ngày cho Groom; bữa đã có việc thì bỏ qua. `data` = số việc vừa tạo.
- **Test lỗi:** Trainer sửa / hủy khẩu phần y tế hoặc khi ngựa đang khóa, Veterinarian sửa khẩu phần thường của ngựa khỏe, Club Manager → `403`; khẩu phần đã hủy / kết thúc → `400`.

**Khi Groom hoàn thành việc cho ăn** (`PUT /care-tasks/{id}/complete`): kho trừ đúng các món của bữa đó qua sổ cái (nhật ký kho `ISSUE`, nguồn `TASK`) và ghi chi phí vật tư (`Task_Material_Usage`, tính vào hóa đơn tháng dòng `DAILY_CARE`). Thiếu hàng không chặn việc hoàn thành; lý do ghi vào `notes` của việc.

**Khi ngựa bị khóa / mở khóa (BR_03):** khóa (10.1, bệnh án chỉ định khóa, hoặc 3.6) → khẩu phần thường của HLV `ENDED` (khẩu phần chưa bắt đầu `CANCELLED`), việc cho ăn chưa làm bị hủy, bác sĩ thú y nhận email để lập khẩu phần y tế. Mở khóa (10.2) → khẩu phần y tế `ENDED`, HLV phụ trách nhận email để lập lại khẩu phần thường.

### 5.4 Đề xuất thức ăn đặc biệt — `/api/v1/diet-requests`

- `POST /api/v1/diet-requests` (`Groom` trong khu của ngựa, `Trainer` phụ trách ngựa):
  ```json
  { "horseId": 6, "itemId": 3, "item": "Cà rốt 1 kg/ngày", "reason": "Ngựa biếng ăn, cần bổ sung" }
  ```
  `itemId` tùy chọn (phải tồn tại nếu gửi); `reason` 10–500 ký tự. **Mong đợi:** `201`, `status = PENDING`.
- **Người duyệt theo trạng thái khóa của ngựa (BR_03):**
  - Ngựa **không khóa** huấn luyện → gửi **HLV phụ trách ngựa** (email `DIET_REQUEST`). HLV gửi đề xuất cho ngựa khỏe → `400` (HLV sửa khẩu phần trực tiếp, 5.x). Ngựa chưa có HLV → `400`.
  - Ngựa **đang khóa** huấn luyện → gửi **Bác sĩ thú y** (email cho mọi bác sĩ đang hoạt động).
  - Xét theo trạng thái khóa **tại thời điểm duyệt**: ngựa bị khóa / mở khóa sau khi gửi thì đề xuất đang chờ tự chuyển người duyệt.
- `GET /api/v1/diet-requests?horseId=&status=`, `GET /api/v1/diet-requests/{id}`: lọc theo phạm vi người gọi. Mỗi đề xuất có `approverRole` (`Trainer` / `Veterinarian`), `approverName`, `canDecide` (người đang đăng nhập được duyệt).
- `PUT /api/v1/diet-requests/{id}/decide` (**HLV phụ trách** nếu ngựa không khóa, **`Veterinarian`** nếu ngựa đang khóa; sai người → `403`): `{ "approve": true, "note": "Đồng ý" }`. Duyệt → món được thêm vào `specialInstructions` của khẩu phần đang áp dụng và hiện ngay trên các việc cho ăn chưa làm của Groom (`dietNote`). Từ chối → `note` ≥ 10 ký tự. Ghi Audit Log.
- **Test lỗi:** Groom ngoài khu / Trainer không phụ trách → `403`; duyệt khi ngựa chưa có khẩu phần đang áp dụng → `400` "...Hãy lập khẩu phần trước khi duyệt"; xử lý lần 2 → `400`; Club Manager duyệt → `403`; bác sĩ duyệt đề xuất của ngựa không khóa hoặc HLV duyệt đề xuất của ngựa đang khóa → `403`.

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
  "goal": "Tăng sức bền cho cự ly 1600m",
  "type": "Chuẩn bị thi đấu",
  "phases": [
    { "name": "Nền tảng", "weeks": 3, "intensity": "MEDIUM", "distanceM": 2000, "targetSpeedKmh": 40, "trackType": "DIRT", "sessionsPerWeek": 4, "maxHeartRate": 170 },
    { "name": "Tăng tốc", "weeks": 2, "intensity": "HIGH", "distanceM": 1600, "sessionsPerWeek": 5, "maxHeartRate": 185 },
    { "name": "Hồi phục", "weeks": 1, "intensity": "LOW", "sessionsPerWeek": 3, "maxHeartRate": 150 }
  ]
}
```

- `type` (tùy chọn, tối đa 50 ký tự): loại giáo án, lưu cột `plan_type` (không cần ghi vào đầu `goal` nữa).
- `phases` (tùy chọn, tối đa 12, lưu bảng `Training_Plan_Phases`): đánh số theo thứ tự gửi lên. `weeks` 1–52; `intensity` `LOW` / `MEDIUM` / `HIGH` (hoặc Thấp / Trung bình / Cao); `trackType` `SYNTHETIC` / `DIRT` / `TURF`; `sessionsPerWeek` 1–14; `maxHeartRate` 40–260. **Tổng số tuần không vượt thời gian giáo án** → vượt thì `400`.
- `GET /training-plans` (6.2) và `GET /training-plans/{planId}` (6.3) trả `type` và `phases[]`, mỗi giai đoạn có thêm `startDate`, `endDate` (tính từ ngày bắt đầu giáo án và số tuần các giai đoạn trước) và `current` (giai đoạn của hôm nay). Mỗi buổi tập trong 6.3 có `phaseNo`.
- **Ngưỡng buổi tập theo giai đoạn:** phân công buổi tập (6.5) không gửi `maxHeartRate` thì lấy `maxHeartRate` của giai đoạn chứa ngày tập (rồi mới tới mặc định 180).
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
| `PAUSED` | Ngựa bị VetLock khi giáo án đang chạy, hoặc có buổi tập dừng & gửi bác sĩ (7.3, `requestVet: true`). **Hủy một buổi tập hoặc dừng không gửi bác sĩ không làm giáo án `PAUSED`.** |
| `COMPLETED` | Không còn buổi chưa kết thúc và đã có ít nhất 1 buổi hoàn thành |
| `CANCELLED` | HLV phụ trách / Club Manager hủy (6.6), hoặc bác sĩ xử lý ca mà chưa có buổi nào hoàn thành |

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
  "assignedToId": 14,
  "maxHeartRate": 180,
  "maxSpeedKmh": 55
}
```

- `maxHeartRate` (60–300 bpm), `maxSpeedKmh` (1–100 km/h): ngưỡng của buổi tập (F22-02). Bỏ trống thì lấy mặc định `app.training.default-max-heart-rate` = 180, `app.training.default-max-speed-kmh` = 60.
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

### 6.6 Hủy / chạy lại giáo án (`Trainer` phụ trách ngựa, `Club Manager`)

**`PUT /api/v1/training-plans/{planId}/cancel`**

```json
{ "reason": "Đổi mục tiêu thi đấu" }
```

- Giáo án `PENDING` / `ACTIVE` / `PAUSED` → `CANCELLED`; các buổi `PENDING` / `CONFIRMED` chưa thực hiện bị hủy (ghi chú "Hủy do giáo án bị hủy: ..."). Ngựa lập được giáo án mới ngay. Nhật ký `CANCEL_TRAINING_PLAN`.
- **Test lỗi:** lý do dưới 5 ký tự → `400`; giáo án đã `COMPLETED` / `CANCELLED` → `400`; còn buổi tập đang `IN_PROGRESS`, `PAUSED_PENDING_TRAINER` hoặc `WAITING_VET` → `400` (xử lý buổi tập / chờ bác sĩ trước); HLV không phụ trách ngựa → `403`; Groom / bác sĩ → `403`.

**`PUT /api/v1/training-plans/{planId}/resume`** (không cần body)

- Chỉ giáo án `PAUSED`. Chuyển `PENDING`, rồi `ACTIVE` ngay nếu đã tới `startDate` và có buổi đã xác nhận. Nhật ký `RESUME_TRAINING_PLAN`.
- **Test lỗi:** giáo án không `PAUSED` → `400`; ngựa đang VetLock → `400` (chạy lại khi bác sĩ mở khóa); giáo án đã quá `endDate` → `400` (hủy rồi lập mới); còn buổi chờ bác sĩ / đang tập → `400`; HLV không phụ trách → `403`.

---

## 7. Training Session — `/api/v1/training-sessions` (`Trainer` hoặc `Groom`)

### 7.1 `GET /api/v1/training-sessions/my-schedule?startDate=2026-10-19&endDate=2026-10-26`

- Login bằng đúng nhân viên được gán ở 6.5.
- **Mong đợi:** `200`, thấy buổi tập vừa tạo. **Ghi lại `sessionId`**.
- Mỗi buổi tập có thêm `notes`, `maxHeartRate`, `maxSpeedKmh`, `startedAt`, `pausedAt`, `pausedReason`, `readings[]` và `recovery[]` (mỗi phần tử `{ readingId, time: "HH:mm", recordedAt, heartRate, speedKmh }`). `GET /training-plans/{planId}` (6.3) trả cùng các trường trong `sessions[]`.
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

### 7.3 `PUT /api/v1/training-sessions/{sessionId}/stop`: dừng buổi tập vì chỉ số sức khỏe vượt ngưỡng (`Trainer` phụ trách ngựa hoặc `Groom` được giao)

**Chỉ dừng được buổi tập đang `PAUSED_PENDING_TRAINER`** (đã tự tạm dừng do chỉ số vượt ngưỡng, 7.6). Buổi `PENDING` / `CONFIRMED` / `IN_PROGRESS` → `400` "Chỉ dừng được buổi tập đang tạm dừng chờ HLV quyết định".

```json
{ "reason": "Nhịp tim vượt ngưỡng 210 bpm, ngựa thở gấp", "requestVet": true, "severity": "HIGH", "heartRate": 210, "speedKmh": 52 }
```

- **Buổi tập kết thúc ngay (`STOPPED`)** trong mọi trường hợp; HLV đánh giá được ngay bằng 7.4A. Giáo án **không** bị tạm dừng.
- `requestVet` (mặc định `true`): hệ thống tạo ca khám nguồn `SESSION_STOP` trong hộp thư bác sĩ (`GET /medical/cases`), `data.caseCode` là mã ca. `requestVet: false`: chỉ kết thúc buổi tập, không tạo ca.
- **Trong lúc ca chưa đóng:** Groom chưa bắt đầu được buổi tập mới của ngựa (7.5 → `400` "Ngựa ... đang chờ bác sĩ khám (ca C-xxxx)...").
- **Bác sĩ quyết định:**
  - **Không khóa:** đóng ca `PUT /medical/cases/{caseId}/close` → ngựa tập tiếp theo giáo án hiện tại.
  - **Khóa:** `POST /medical/vet-lock` → các buổi tập chưa thực hiện bị hủy, giáo án `PAUSED` cho tới khi bác sĩ mở khóa (10.2).
- Dữ liệu cũ còn ở `WAITING_VET` (trước bản sửa) vẫn được chuyển `STOPPED` khi bác sĩ đóng ca / khóa huấn luyện.
- **Test lỗi:** lý do dưới 10 ký tự → `400`; buổi tập không ở `PAUSED_PENDING_TRAINER` → `400`; Trainer không phụ trách ngựa hoặc Groom khác → `403`; `sessionId` sai → `404`.

### 7.4 `PUT /api/v1/training-sessions/{sessionId}/execute`

```json
{ "status": "COMPLETED", "notes": "Ngựa chạy tốt" }
```

- `status` chỉ nhận `COMPLETED` hoặc `CANCELLED`.
- **Hoàn thành (`COMPLETED`) khi buổi đã được xác nhận (`CONFIRMED`) hoặc đang tập (`IN_PROGRESS`) và giáo án đang `ACTIVE`. Buổi đang `PAUSED_PENDING_TRAINER` phải được HLV cho tập tiếp (7.8) hoặc dừng (7.3) trước. Hủy (`CANCELLED`) được mọi buổi chưa kết thúc.**
- `notes` được **nối thêm** vào ghi chú cũ (ngăn bằng ` | `), không ghi đè ghi chú HLV đặt lúc phân công.
- **Mong đợi:** `200`. Nếu không còn buổi chưa kết thúc và đã có buổi hoàn thành, giáo án tự chuyển sang `COMPLETED` (kiểm tra lại bằng 6.2).
- **Hủy một buổi tập không tạm dừng giáo án:** giáo án vẫn `ACTIVE`, các buổi còn lại hoàn thành được. Hủy hết mà chưa có buổi hoàn thành thì giáo án giữ `ACTIVE` để HLV thêm buổi hoặc hủy giáo án (6.6).
- **Phạm vi:** Groom chỉ thao tác buổi được giao; Trainer chỉ buổi tập của ngựa được phân công cho mình (khác → `403`); Club Manager không giới hạn.
- **Test lỗi:**
  - `COMPLETED` khi buổi còn `PENDING` (chưa xác nhận) → `400` "Buổi tập chưa được xác nhận...".
  - `COMPLETED` khi giáo án còn `PENDING` (chưa tới ngày bắt đầu) → `400`.
  - Thực hiện lại cùng buổi → `400` "Không thể cập nhật buổi tập đang ở trạng thái COMPLETED."
  - Nhân viên khác (không được gán) gọi → `403`.
  - `"status": "CANCELLED"` mà không có `notes` → `400` "Phải ghi lý do (notes) khi hủy buổi tập".
  - `"status": "COMPLETED"` khi ngựa đang VetLock (F22-01) → `400`. Chỉ được hủy buổi tập.
  - Trainer gọi buổi tập của ngựa không phụ trách → `403`.

### 7.4A `PUT /api/v1/training-sessions/{sessionId}/evaluate`: đánh giá phong độ (`Trainer` phụ trách ngựa, `Club Manager`)

```json
{ "score": 8, "comment": "Bứt tốc tốt ở 400m cuối" }
```

- Buổi **đã kết thúc**: `COMPLETED` hoặc `STOPPED` (dừng giữa chừng / dừng & gửi bác sĩ), **mỗi buổi đánh giá một lần**. **Mong đợi:** `200`; đánh giá trả về trong `evaluationScore`, `evaluationComment`, `evaluatedBy`, `evaluatedAt` của 6.3 / 7.1.
- **Test lỗi:** buổi chưa kết thúc (chờ xác nhận, đang tập, tạm dừng) → `400`; đánh giá lần 2 → `400` "Buổi tập này đã được đánh giá..."; Trainer không phụ trách ngựa → `403`; Groom → `403`.

### 7.5 `PUT /api/v1/training-sessions/{sessionId}/start`: Groom bắt đầu buổi tập (`Groom` được giao)

Không cần body. `CONFIRMED` → `IN_PROGRESS`, ghi `startedAt`.

- **Test lỗi:** buổi chưa xác nhận hoặc đã bắt đầu → `400`; buổi xếp ngày khác hôm nay → `400` "...chỉ bắt đầu được đúng ngày tập"; ngựa đang VetLock → `400`; giáo án chưa `ACTIVE` → `400`; Groom khác → `403`.

### 7.6 `POST /api/v1/training-sessions/{sessionId}/readings`: nhập chỉ số (`Groom` được giao)

```json
{ "time": "07:15", "heartRate": 165, "speedKmh": 42.5 }
```

- `time`: `HH:mm` (tính theo ngày của buổi tập) hoặc `yyyy-MM-ddTHH:mm`; bỏ trống = bây giờ. `heartRate` 1–300, `speedKmh` 0–100.
- Chỉ nhập khi buổi đang `IN_PROGRESS`. **Mong đợi:** `200`, `data` là buổi tập kèm `readings[]`.
- **Vượt ngưỡng** (`heartRate > maxHeartRate` hoặc `speedKmh > maxSpeedKmh`): vẫn lưu lần đo, buổi tập chuyển **`PAUSED_PENDING_TRAINER`**, có `pausedAt`, `pausedReason` (ví dụ "Nhịp tim 195 bpm vượt ngưỡng 180 bpm"), HLV phụ trách nhận email, nhật ký `PAUSE_SESSION`.
- **Test lỗi:** buổi chưa bắt đầu → `400`; buổi đang tạm dừng → `400` "...không nhập thêm chỉ số. Hãy nhập nhịp tim hồi phục"; `time` sai định dạng → `400`; Groom khác → `403`.

### 7.7 `POST /api/v1/training-sessions/{sessionId}/recovery`: nhịp tim hồi phục (`Groom` được giao)

```json
{ "time": "07:20", "heartRate": 120 }
```

- Chỉ khi buổi đang `PAUSED_PENDING_TRAINER`. **Mong đợi:** `200`, buổi tập kèm `recovery[]`. Buổi khác trạng thái → `400`.

### 7.8 `PUT /api/v1/training-sessions/{sessionId}/resume`: HLV cho tập tiếp (`Trainer` phụ trách ngựa)

```json
{ "intensity": "Nhẹ", "distanceM": 800, "maxHeartRate": 170, "note": "Giảm tốc độ nửa sau" }
```

- Mọi trường đều tùy chọn. `maxHeartRate` là ngưỡng mới cho phần còn lại của buổi. Điều chỉnh được nối vào `notes`.
- Điều kiện: buổi đang `PAUSED_PENDING_TRAINER`, ngựa không bị khóa, **nhịp hồi phục gần nhất dưới ngưỡng đang áp dụng lúc tạm dừng**. **Mong đợi:** `200`, buổi về `IN_PROGRESS`, `pausedAt` / `pausedReason` xóa; nhật ký `RESUME_SESSION`.
- **Test lỗi:** chưa có nhịp hồi phục → `400`; nhịp hồi phục ≥ ngưỡng → `400` "...chưa dưới ngưỡng..."; buổi không tạm dừng → `400`; ngựa đang VetLock → `400`; Groom / Club Manager gọi → `403`; HLV không phụ trách ngựa → `403`.
- Không cho tập tiếp thì HLV dừng & gửi bác sĩ bằng 7.3.

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
- Query `date` (tùy chọn, `yyyy-MM-dd`): mặc định hôm nay; truyền ngày mai để xem trước việc cho ăn đã gửi trước.
- **Mong đợi:** `200`, danh sách việc có `taskDate` là ngày đó, sắp theo `scheduledTime`. **Ghi lại `taskId`**.
- Mỗi việc có `completableFrom` (giờ hẹn trừ 30 phút, cấu hình `CARE_EARLY_COMPLETE_MINUTES`) và `canComplete`. FE dùng `canComplete` để bật / tắt nút hoàn thành.
- Chưa tới `completableFrom` (ví dụ 00:20 xác nhận bữa trưa 11:30, hoặc việc ngày mai) → `PUT /care-tasks/{id}/complete` trả `400` "Chưa tới giờ thực hiện: việc hẹn ..., chỉ được xác nhận hoàn thành từ HH:mm dd/MM."

### 9.3 `GET /api/v1/care-tasks/overdue`: việc trễ hạn (`Club Manager`, BR_05)

- Hệ thống quét **mỗi 5 phút**. Việc chưa xong (`TODO` hoặc `IN_PROGRESS`) quá **45 phút** sau giờ hẹn thì được đánh dấu trễ (`warningNote`, `overdue = true`), và các Club Manager nhận **một email tổng hợp**. Mỗi việc chỉ bị cảnh báo một lần.
- Groom hoàn thành sau giờ hẹn quá 45 phút thì việc cũng được ghi nhận trễ: `warningNote` thêm "Hoàn thành trễ N phút (hẹn HH:mm, xong HH:mm dd/MM)".
- Mọi `CareTaskDTO` có `lateMinutes`: số phút trễ so với giờ hẹn (tới lúc hoàn thành, hoặc tới hiện tại nếu chưa xong), `null` nếu chưa quá 45 phút. FE dùng trường này, không tự tính.
- **Query:**
  - Không tham số (`scope=open`): chỉ việc **đang mở** đã trễ.
  - `scope=today` hoặc `date=yyyy-MM-dd`: **mọi việc trễ của ngày đó**, kể cả đã hoàn thành trễ (trừ `SKIPPED`). Dùng cho thẻ thống kê "Việc trễ SLA > 45 phút".
- **Mong đợi:** `200`, danh sách việc trễ. `GET /care-tasks/my-tasks` của Groom cũng có `overdue`, `lateMinutes` và `warningNote`.
- **Cách test nhanh:** giao việc (9.0) với `taskDate` là hôm nay và `scheduledTime` sớm hơn hiện tại ít nhất 45 phút, chờ tối đa 5 phút rồi gọi API này.

### 9.2 `PUT /api/v1/care-tasks/{taskId}/complete` (`Groom`)

- Hoàn thành xong, **HLV phụ trách ngựa nhận email** "Đã hoàn thành: ..." (giờ hoàn thành theo giờ Việt Nam; việc cho ăn tự sinh từ khẩu phần không gửi email để tránh quá nhiều thư), nhật ký `COMPLETE_CARE_TASK`.
- **`GET /api/v1/care-tasks/assigned?date=&status=`** (`Trainer`, `Club Manager`): HLV xem việc chăm sóc của ngựa mình phụ trách (Club Manager: mọi ngựa) trong ngày `date` (mặc định hôm nay), có `status` và `completedAt`; lọc `status` = `TODO` / `IN_PROGRESS` / `DONE` / `SKIPPED`.

- **Mong đợi:** `200` "Đã hoàn thành công việc!". Task chuyển sang `status = "DONE"` và có `completedAt`.
- Nếu task là `FEED`, tồn kho (`Inventory_Items.current_stock`) của các món trong thực đơn của ngựa bị trừ. Kiểm tra lại bằng SQL.
- Nếu task là `CLEAN`, chuồng của việc chuyển `CLEAN` (xem 4.1).
- **Test lỗi:**
  - Hoàn thành lại cùng task → `400` "...đã được đánh dấu hoàn thành trước đó".
  - Groom khác gọi → `403`.
  - Trainer hoặc Owner gọi → `403`.
  - `taskId` không tồn tại → `404`.

---

## 10. Medical (VetLock) — `/api/v1/medical`

### 10.1 `POST /api/v1/medical/vet-lock` — Khóa huấn luyện (chỉ `Veterinarian`)

Chỉ bác sĩ thú y quyết định khóa. Trainer hoặc Groom phát hiện chấn thương thì **báo sự cố** bằng `POST /api/v1/medical/cases` (10.3) để bác sĩ khám và quyết định. Trainer, Groom, Owner, Club Manager gọi → `403`.

```json
{ "horseId": 1, "reason": "Nghi chấn thương chân trước" }
```

- **Mong đợi:** `200`. Kiểm tra bằng 3.4: ngựa có `isLocked = true`, `healthStatus = INJURED`.
- **Tự động khi khóa (F3-03):** mọi buổi tập chưa thực hiện (`PENDING` và `CONFIRMED`) của ngựa chuyển `CANCELLED` (ghi chú "Hủy tự động do ngựa bị khóa huấn luyện…"), giáo án đang chạy chuyển **`PAUSED`**, HLV phụ trách và chủ ngựa nhận email. Thao tác được ghi Audit Log (`VET_LOCK`).
- **Khi bác sĩ mở khóa (10.2):** giáo án `PAUSED` về **`PENDING`**; HLV phân công lại buổi tập, Groom xác nhận thì giáo án chạy lại. HLV và chủ ngựa nhận email, ghi Audit Log (`VET_UNLOCK`).

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
- **Cấp cứu (BR_17):** thêm `"emergency": true` (nhận cả `isEmergency`) và bắt buộc có `caseId` → ca khám chuyển `severity = CRITICAL` (ghi Audit `MARK_EMERGENCY`), phác đồ trả `emergency = true`: kho cấp thuốc ngay, cấp xong không chuyển `ON_HOLD_PAYMENT` mà vào điều trị, hóa đơn giai đoạn vẫn được lập (12F.8). `emergency: true` mà không có `caseId` → `400`. Bỏ chọn không hạ mức ca khám. Cũng áp dụng cho 12.6 (phiên bản mới).
- **Mỗi ca khám chỉ có 1 phác đồ:** `caseId` đã có phác đồ chưa hủy / chưa bị thay thế → `400` "Ca khám #... đã có phác đồ PD-...". Đổi thuốc / liều thì dùng 12.6 (phiên bản mới). Ngựa có **ca khám mới** (VD: Groom báo sự cố, `POST /medical/cases`) thì lập được phác đồ mới cho ca đó, kể cả khi phác đồ của ca cũ còn đang điều trị.
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

### 12.10 `POST /api/v1/medical-management/protocols/{protocolId}/items` — Thêm thuốc khi phác đồ đang điều trị (`Veterinarian`)

```json
{ "phaseNo": 1, "itemId": 4, "quantity": 2, "dosage": "1 viên/ngày", "reason": "Ngựa còn đau khớp sau 3 ngày" }
```

- Chỉ khi phác đồ `IN_TREATMENT` (phác đồ chưa cấp thuốc thì dùng 12.6 tạo phiên bản mới). Giai đoạn phải chưa kết thúc. Chỉ kê Thuốc / Vaccine / Vật tư y tế.
- **Không tạo phiên bản mới, phác đồ vẫn `IN_TREATMENT`.** Thuốc đã có trong giai đoạn thì **cộng thêm số lượng** vào dòng cũ, chưa có thì thêm dòng mới.
- Kho **giữ ngay** phần bổ sung (`stockAnswer` thêm dòng `HELD`, hoặc `SHORT` nếu kho đang thiếu); DTO có `pendingSupplement = true`.
- Chủ ngựa nhận **hóa đơn thuốc bổ sung** riêng, mã `GD-<protocol_id>-<phaseNo>-BS<n>` (chỉ tính phần thêm), kèm email. Hóa đơn giai đoạn gốc không đổi.
- Tự ghi tiến trình "Bổ sung thuốc GĐ ...: ... Lý do: ..." và nhật ký `ADD_PROTOCOL_ITEM`.
- **Test lỗi:** phác đồ không `IN_TREATMENT` → `400`; `phaseNo` không có → `400`; giai đoạn đã kết thúc → `400`; thức ăn / vật tư chuồng → `400`; thiếu `reason` hoặc dưới 5 ký tự → `400`; role khác bác sĩ → `403`.
- **Kết thúc phác đồ (12.8)** khi thuốc bổ sung chưa cấp: hàng đang giữ được trả lại kho và hóa đơn bổ sung chưa thanh toán bị hủy.

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

- `category` nhận mã (`FEED`, `SUPPLEMENT`, `MEDICINE`, `VACCINE`, `MEDICAL_SUPPLY`, `STABLE_SUPPLY`, `EQUIPMENT`, `OTHER`) hoặc nhãn tiếng Việt (Thức ăn, Thức ăn bổ sung, Thuốc, Vaccine, Vật tư y tế, Vật tư chuồng trại, Dụng cụ, Khác; có dấu hay không đều được). **Luôn lưu thành nhãn tiếng Việt** cho khớp dữ liệu hiện có (ví dụ `MEDICAL_SUPPLY` → "Vật tư y tế"). Lọc `GET /inventory/items?category=` cũng nhận mã hoặc nhãn.
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

- `GET /api/v1/inventory/protocols`: mặc định trả **mọi phác đồ** (trừ bản nháp) để màn hình kho chia tab: chờ kiểm tồn `WAITING_STOCK_CHECK`, đủ / thiếu hàng `STOCK_AVAILABLE` / `STOCK_SHORTAGE`, chờ hàng `WAITING_RESTOCK` / `REQUIRES_VET_REVIEW`, **bác sĩ đã xác nhận - chờ cấp phát `VET_CONFIRMED`** (không phải `READY_FOR_DISPENSING`), đã cấp phát `IN_TREATMENT` (có `pendingSupplement = true` nếu còn thuốc bổ sung chờ cấp, 12.10), tạm dừng chờ thanh toán `ON_HOLD_PAYMENT`, hoàn tất `COMPLETED`, bác sĩ đã đổi / hủy `SUPERSEDED` / `CANCELLED`. Sắp theo ngày cần thuốc. Lọc bằng `?state=VET_CONFIRMED` hoặc nhiều trạng thái `?state=STOCK_SHORTAGE,WAITING_RESTOCK`.
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
- **Xuất theo lô hết hạn trước (FEFO):** mỗi lô một dòng `ISSUE` kèm số lô (lô không có hạn dùng dùng sau cùng; phần vượt số lượng các lô đang ghi nhận ghi dòng không có lô). Dòng `stockAnswer` đã cấp có thêm `issuedAt`, `issuedByName`, `issuedLots` (ví dụ `["LOT-01 x 2 (HSD 30/06/2027)", "LOT-02 x 1 (HSD 31/12/2027)"]`).
- **`GET /api/v1/inventory/protocols/{protocolId}/dispensing`** (`Warehouse Manager`, `Veterinarian`, `Club Manager`): lịch sử cấp phát của phác đồ, mỗi dòng `txCode, holdId, itemId, itemName, unit, quantity, lotNo, expiryDate, issuedAt, issuedByName, note`, theo thời gian. Phác đồ không tồn tại → `404`; role khác → `403`.
- Cấp khi chưa xác nhận → `400`.
- **Phác đồ `IN_TREATMENT` có thuốc bổ sung (12.10):** gọi cùng API này để cấp **phần bổ sung**. Dòng `SHORT` được bù từ tồn kho hiện tại; vẫn thiếu → `400` "Kho chưa đủ ...". Hóa đơn thuốc bổ sung (`...-BS<n>`) phải được chủ ngựa thanh toán trước → nếu chưa: `400` "Chủ ngựa chưa thanh toán hóa đơn thuốc bổ sung ..." (ca cấp cứu `CRITICAL` được cấp trước - BR_17). Cấp xong: phác đồ vẫn `IN_TREATMENT`, `pendingSupplement = false`. Không còn thuốc bổ sung chờ cấp → `400`.

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
   - **Bấm thanh toán lần nữa** khi giao dịch trước còn hạn (`app.vnpay.expire-minutes`, chừa 1 phút) và cùng số tiền: trả lại **đúng giao dịch cũ** (cùng `txnRef`, `transactionId`, `paymentUrl`), `data.reused = true`, không tạo giao dịch mới. Hết hạn hoặc số tiền đã đổi (phát sinh phí trễ hạn) thì tạo giao dịch mới, `reused = false`.
2. Mở `paymentUrl` trên trình duyệt → trang VNPay Sandbox → chọn ngân hàng NCB, nhập thẻ test ở 12F.0.
3. VNPay chuyển về `GET /api/v1/payments/vnpay/return?...`. Server kiểm tra chữ ký rồi **hỏi lại VNPay bằng `querydr`**. Chỉ khi VNPay xác nhận thành công và đúng số tiền thì giao dịch mới thành `VERIFIED` và hóa đơn thành `PAID`.
   - **querydr không khả dụng** (VNPay Sandbox trả mã `99` / `03` / `94`, hoặc lỗi mạng) và `VNPAY_TRUST_SIGNED_RETURN=true` (mặc định): server dùng chính dữ liệu Return đã qua kiểm tra chữ ký HMAC, đối chiếu như IPN (đúng `vnp_TxnRef`, đúng số tiền, `vnp_ResponseCode = 00` và `vnp_TransactionStatus = 00` → `VERIFIED` / `PAID`; mã khác → `FAILED`). Ghi chú giao dịch: "VNPay xác nhận thành công (nguồn Return đã ký, querydr không khả dụng - mã 99)". querydr trả lời được thì kết quả querydr luôn quyết định (ví dụ trạng thái `01` đang xử lý → vẫn chờ). Đặt `VNPAY_TRUST_SIGNED_RETURN=false` để chỉ tin querydr / IPN. Kết quả trả JSON `status = SUCCESS | FAILED | PENDING | INVALID`; nếu đặt `VNPAY_FRONTEND_RESULT_URL` thì chuyển hướng về trang đó kèm `?status=&invoiceId=&txnRef=`.
   - **Tần suất hỏi VNPay (querydr):** VNPay trả mã **94** nếu hỏi lại cùng giao dịch quá sớm. Return URL luôn hỏi ngay; còn tải lại trang hóa đơn và tác vụ định kỳ chỉ hỏi khi giao dịch đã tạo quá `app.vnpay.querydr-interval-seconds` (mặc định 300 giây) **và** lần hỏi trước đã cách đủ khoảng đó. Gặp mã 94 thì giao dịch vẫn `PENDING_VERIFICATION`, ghi chú "VNPay từ chối truy vấn trùng lặp (mã 94), hỏi lại sau 5 phút" và được hỏi lại ở lần sau. Mã lỗi VNPay (91, 94, 99...) không còn bị ghi nhầm là "chữ ký không hợp lệ"; chỉ phản hồi mã 00 mới được kiểm tra chữ ký.
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
- **Giai đoạn đang chờ thanh toán:** phác đồ `ON_HOLD_PAYMENT` trả thêm `awaitingPaymentPhaseNo`, `awaitingPaymentPhaseName`, `awaitingPaymentInvoiceCode` (giai đoạn sớm nhất còn hóa đơn mở). Mới cấp thuốc → giai đoạn 1; bác sĩ hoàn thành giai đoạn 1 của phác đồ nhiều giai đoạn → giai đoạn 2 (hóa đơn `GD-xxxxxx-02`). FE dùng các trường này thay vì ghi cố định "giai đoạn 1".
4. **BR_17 – ca cấp cứu:** phác đồ có `caseId` trỏ tới ca khám `severity = CRITICAL` vẫn được lập hóa đơn nhưng **không bị tạm dừng** vì chưa thanh toán (`emergency = true`).
5. Phác đồ bị hủy hoặc có phiên bản mới → hóa đơn giai đoạn chưa trả chuyển `CANCELLED`. Kết thúc sớm → hủy hóa đơn của các giai đoạn chưa bắt đầu.

- Hóa đơn giai đoạn vẫn hiện trong danh sách hóa đơn của chủ ngựa / Club Manager và trong báo cáo (loại `MEDICATION`). Phí trễ hạn tính từ sau ngày bắt đầu giai đoạn. Mốc đình chỉ (BR_13) tính theo hạn thanh toán nên hóa đơn phát hành sớm không bị tính đủ 25 ngày quá sớm.

> **Lưu ý:** API cũ `POST /api/v1/owner/payments/medical-records/{recordId}/vnpay` vẫn là **giả lập** (không gọi VNPay), thuộc luồng bệnh án cũ.

---

## 13–17. Các API dữ liệu giả (mock) — trừ mục 13, 14, 15 đã dùng dữ liệu thật

Các API dưới đây (trừ mục 13, 14, 15) **không đọc/ghi database**. Dữ liệu được code cứng hoặc nằm trong RAM. Chỉ cần kiểm tra là gọi được và trả đúng format. Tất cả yêu cầu đã login (role nào cũng được).

### 13. Giải đấu, đề xuất thi đấu và khám tiền thi đấu — DỮ LIỆU THẬT (`External_Competitions`, `Competition_Entries`)

Luồng: HLV đề xuất (`WAITING_OWNER`) → chủ ngựa đồng ý (`WAITING_VET`) / từ chối (`OWNER_DECLINED`) → bác sĩ khám đạt (`READY_TO_SUBMIT`) / không đạt (`VET_FAILED`) → HLV gửi BTC (`SUBMITTED`) → BTC từ chối (`REJECTED`) hoặc ghi kết quả (`RESULT_RECORDED`). Mỗi bước có nhật ký; chủ ngựa / HLV nhận email ở các bước liên quan. Trạng thái "chờ bác sĩ khám" trong DB là `WAITING_VET` (FE gọi là WAITING_VET_EXAM); mỗi đề xuất có thêm `statusLabel` tiếng Việt.

**13.1 Giải đấu**

- `GET /api/v1/tournaments?open=false` (mọi role đã login): danh sách giải. `open=true` chỉ lấy giải còn nhận đề xuất (chưa kết thúc, chưa qua ngày đua và hạn đăng ký). Mỗi giải: `tournamentId, code, name, organizer, location, raceDate, registrationDeadline, distanceM, entryFee, entryConditions, finished, openForProposal`.
- `POST /api/v1/tournaments` (`Club Manager`), DB đang chưa có giải nào nên tạo trước:

  ```json
  { "code": "BD-2026", "name": "Giải Mở rộng Bình Dương 2026", "organizer": "CLB Đua ngựa Bình Dương", "location": "Trường đua Đại Nam",
    "raceDate": "2026-11-25", "registrationDeadline": "2026-11-10", "distanceM": 1200, "entryFee": 5000000, "entryConditions": "Ngựa từ 3 tuổi" }
  ```

  → `201`. Lỗi: hạn đăng ký sau ngày đua → `400`; mã giải trùng → `400`; ngày đua ở quá khứ → `400`; role khác → `403`.

**13.2 Đề xuất thi đấu — `/api/v1/race-proposals`**

- `POST` (`Trainer` phụ trách ngựa): `{ "horseId": 21, "tournamentId": 1, "note": "Ngựa đang phong độ tốt" }` → `201`, `status = WAITING_OWNER`, `proposalCode` dạng `DX-0001`, `entryFee` lấy lệ phí của giải. Lỗi: ngựa chưa duyệt / đang VetLock → `400`; giải đã đóng → `400`; ngựa đã có đề xuất cho giải này → `400`; HLV không phụ trách ngựa → `403`.
- `GET ?status=` (Trainer: ngựa được giao; Horse Owner: ngựa của mình; Veterinarian, Club Manager: tất cả), `GET /{id}` (ngoài phạm vi → `403`). Mỗi đề xuất gồm ngựa, chủ ngựa, `tournament`, người đề xuất, `note`, phản hồi chủ ngựa, kết luận bác sĩ, kết quả.
- `PUT /{id}/owner-response` (`Horse Owner` của ngựa): `{ "agree": true, "note": "Đồng ý" }` → `WAITING_VET`; `agree: false` → `OWNER_DECLINED`. Lỗi: không phải chủ → `403`; đã trả lời → `400`.
- `PUT /{id}/submitted` (`Trainer`): chỉ khi `READY_TO_SUBMIT` → `SUBMITTED`. Chưa qua bác sĩ → `400`.
- `PUT /{id}/organizer-rejected` (`Trainer`): `{ "reason": "Thiếu giấy chứng nhận" }`, chỉ khi `SUBMITTED` → `REJECTED`.
- `PUT /{id}/result` (`Trainer`): `{ "rank": 2, "finishTime": "1:12.45", "note": "Về nhì" }` (`finishTime` là giây `72.45` hoặc `phút:giây`), chỉ khi `SUBMITTED` và đã tới ngày đua → `RESULT_RECORDED`, `finishTimeSeconds = 72.450`. Lỗi: giải chưa diễn ra → `400`; `finishTime` sai → `400`; `rank` < 1 → `400`.

**13.3 Khám tiền thi đấu — `/api/v1/prerace-exams`** (đọc / ghi `Competition_Entries`)

1. **`GET /api/v1/prerace-exams?tab=CHO_KHAM`** (`Veterinarian`, `Club Manager`): các đề xuất chủ ngựa đã đồng ý (`WAITING_VET`). `healthStatus` là trạng thái sức khỏe hiện tại của ngựa; `warningTag` báo ngựa đang VetLock hoặc không khỏe.
2. **`GET /api/v1/prerace-exams?tab=DA_CO_KET_QUA`**: các đề xuất bác sĩ đã kết luận; `resultStatus` "ĐẠT" / "KHÔNG ĐẠT", `nextStepStatus` là trạng thái hiện tại của đề xuất.
3. **`POST /api/v1/prerace-exams/{proposalCode}/examine`** (`Veterinarian`), `proposalCode` là `DX-0001` hoặc ID:

   ```json
   { "isPassed": true, "vetNotes": "Tim mạch ổn định" }
   ```

   → `200`. Đạt → đề xuất `READY_TO_SUBMIT`; không đạt → `VET_FAILED`.

4. **Test lỗi:** đề xuất không ở `WAITING_VET` (khám lại) → `400`; mã không tồn tại → `404`; `isPassed` thiếu → `400`; không đạt mà thiếu `vetNotes` → `400`; kết luận đạt khi ngựa đang VetLock → `400`; role khác → `403`.

### 14. Protocols — ĐÃ DÙNG DỮ LIỆU THẬT (không còn là mock)

Danh sách tóm tắt cho mọi vai trò đã đăng nhập, đọc từ `Treatment_Protocols` và `Purchase_Orders`. DB không có phác đồ hoặc đơn mua thì trả `data: []`.

- **`GET /api/v1/protocols?tab=Cần bác sĩ xử lý`**: phác đồ còn hiệu lực. Tab có chữ "đóng" (ví dụ `tab=Đã đóng`) → phác đồ `COMPLETED` / `SUPERSEDED` / `CANCELLED`.
  Mỗi dòng: `protocolCode` (ví dụ `PD-1019 v1`), `horseName`, `diagnosis`, `caseCode`, `requiredDate`, `estimatedCost` (tổng `Protocol_Items`), `status` (nhãn tiếng Việt, gồm cả "Tạm dừng · chờ chủ ngựa thanh toán"), `nextStep`.
- **`GET /api/v1/protocols/shipments`**: đơn mua đang mở (`ORDERED`, `IN_TRANSIT`, `DELAYED`, `PARTIALLY_RECEIVED`) phục vụ phác đồ, sắp theo ETA. `status = "Trễ"` khi đơn `DELAYED` hoặc đã quá ETA.
- **Phạm vi:** Horse Owner chỉ thấy phác đồ / lô hàng của ngựa mình; Trainer chỉ ngựa được phân công; Groom chỉ ngựa trong khu phụ trách; Veterinarian, Club Manager, Warehouse Manager thấy tất cả.
- Để có dữ liệu test: bác sĩ lập phác đồ (12.2), kho kiểm hàng (12E). Thiếu hàng → kho tạo đơn mua cho phác đồ (12B) → xuất hiện ở `/protocols/shipments`.

### 15. Vaccinations — ĐÃ DÙNG DỮ LIỆU THẬT (bảng `Vaccinations`)

- **`GET /api/v1/vaccinations?tab=&horseId=&kind=`** (Veterinarian, Club Manager, Trainer, Groom, Horse Owner): `tab` = `due` (quá hạn hoặc đến hạn trong 7 ngày), `upcoming` (sau 7 ngày), `done`, bỏ trống = tất cả; `kind` = `VACCINE` / `DEWORM`. **Lọc theo quyền xem ngựa:** bác sĩ / quản lý xem tất cả, HLV ngựa được phân công, Groom ngựa trong khu phụ trách, chủ ngựa ngựa của mình. Kho → `403`.
  - Mỗi dòng: `id`, `recordCode` (`VC-0001`), `horseId`, `horseName`, `kind`, `typeName`, `category`, `dueDate`, `expectedDate` (dd/MM/yyyy), `countdown` ("còn 3 ngày" / "hôm nay" / "quá hạn 2 ngày"), `cycleMonths`, `statusCode` (`OVERDUE`, `DUE_SOON`, `SCHEDULED`, `POSTPONED`, `DONE`, `SKIPPED`), `status` (nhãn tiếng Việt), `doneAt`, `doneByName`, `lotNo`, `itemId`, `itemName`, `note`, `requisitionCode` / `requisitionStatus` / `requisitionRejectReason` (yêu cầu cấp thuốc gửi kho), `canExecute`, `executeBlockedReason`.
- **`POST /api/v1/vaccinations/schedule`** (`Veterinarian`):

  ```json
  { "horseIds": [21], "kind": "VACCINE", "medicineName": "Vaccine cúm ngựa", "scheduledDate": "2026-11-01", "cycleMonths": 12, "itemId": 3, "dosePerHorse": 1, "notes": "Mũi nhắc lại" }
  ```

  → `201`, `data` là các mũi vừa tạo. `scheduledDate` nhận `yyyy-MM-dd` hoặc `dd/MM/yyyy`. Lỗi: `horseIds` rỗng, ngày ở quá khứ, chu kỳ vaccine ngoài 1–24 / tẩy giun ngoài 3–6, ngựa chưa duyệt, ngựa đã có lịch cùng tên chưa thực hiện, thiếu `itemId` hoặc `itemId` không phải thuốc / vaccine → `400`; ngựa không tồn tại → `404`; role khác bác sĩ → `403`.
  - **Gửi kho:** mỗi ngựa sinh 1 yêu cầu cấp thuốc `SUBMITTED` (`Material_Requisitions`, số lượng `dosePerHorse` mặc định 1, cần trước ngày dự kiến, lý do bắt đầu bằng mã lịch `VC-0001:`). Kho xử lý ở `GET /api/v1/inventory/supply-requests` → `PUT /inventory/supply-requests/{id}/issue` (cấp, trừ kho) / `wait-restock` / `reject`.
- **`POST /api/v1/vaccinations/{recordCode}/stock-request?itemId=&dose=`** (`Veterinarian`): gửi lại yêu cầu cấp thuốc khi lịch chưa có yêu cầu (lịch tạo trước bản sửa) hoặc yêu cầu trước bị kho từ chối / hủy. Lịch chưa gắn thuốc thì bắt buộc `itemId`. Đang có yêu cầu còn hiệu lực → `400`.
- **`POST /api/v1/vaccinations/{recordCode}/execute`** (`Veterinarian`, mã `VC-0001` hoặc ID), body tùy chọn `{ "doneAt": "2026-10-25", "lotNo": "LOT-01", "note": "..." }` (`doneAt` bỏ trống = hôm nay): mũi chuyển `DONE`, ghi người tiêm. **Có `cycleMonths` thì tự tạo mũi nhắc lại** ngày = ngày tiêm + `cycleMonths` tháng. `data` = [mũi vừa tiêm, mũi nhắc lại]. Lỗi: đã ghi nhận → `400`; **chưa tới ngày dự kiến (lịch tương lai) → `400`**; **kho chưa cấp thuốc (yêu cầu chưa `ISSUED`, hoặc chưa có yêu cầu) → `400`**; `doneAt` sau hôm nay hoặc trước ngày dự kiến → `400`; mã không tồn tại → `404`. Mũi nhắc lại tự tạo kèm yêu cầu cấp thuốc mới.
- **Email nhắc lịch (hằng ngày 07:00, `VACCINATION_REMINDER_CRON`):** lịch `SCHEDULED` có ngày dự kiến là hôm nay → mỗi bác sĩ thú y đang hoạt động nhận 1 email tổng hợp "[HorseTrack] Hôm nay (dd/MM/yyyy) bạn có lịch tiêm phòng / tẩy giun - N ngựa" kèm bảng mã lịch, ngựa, chuồng, loại, vaccine. Ghi `Email_Logs` mục đích `VACCINATION_REMINDER`.

---

## 18. Checklist phân quyền nhanh

Sau khi test chức năng chính, thử gọi mỗi API sau bằng **sai role** và xác nhận đều nhận `403`:

| API | Chỉ cho phép |
|---|---|
| `POST /auth/staff/create` | Club Manager |
| `PUT /manager/medical-records/{id}/approve` | Club Manager |
| `GET /training-plans/{planId}` | Trainer, Club Manager |
| `POST /training-plans`, `GET /training-plans/assignable-staff`, `POST /training-plans/{id}/sessions` | Trainer |
| `POST` / `PUT` / `DELETE /nutrition-plans/**`, `POST /nutrition-plans/{id}/feed-tasks` | Trainer (ngựa khỏe, phụ trách), Veterinarian (ngựa đang khóa / khẩu phần y tế) |
| `GET /nutrition-plans/**`, `GET /horses/{id}/nutrition-plan/today` | Trainer, Veterinarian, Groom, Club Manager, Horse Owner (theo phạm vi) |
| `POST /diet-requests` | Groom (cùng khu), Trainer (phụ trách) |
| `PUT /diet-requests/{id}/decide` | Veterinarian |
| `POST /veterinary/medical-records` | Veterinarian |
| `POST /medical/release-lock/{horseId}` | Veterinarian |
| `/inventory/**` (kho, nhà cung cấp, đơn mua, kiểm kê, xử lý yêu cầu vật tư) | Warehouse Manager |
| `/supply-requests/**` (gửi, xem, hủy yêu cầu vật tư) | Groom |
| `POST /medical-management/protocols` và các thao tác trên phác đồ (confirm, wait-restock, new-version, progress-notes, complete, cancel) | Veterinarian |
| `GET /medical-management/medications`, `GET /medical-management/protocols/**` | Veterinarian, Club Manager (chỉ xem) |
| `POST /medical/vet-lock`, `POST /medical/cases` (Trainer), `POST /nutrition-plans`, `POST /care-tasks` | Trainer chỉ với ngựa được phân công |
| `POST /medical/vet-lock` | Veterinarian (Trainer, Groom báo sự cố qua `POST /medical/cases`) |
| `GET /medical/cases` | Veterinarian (mọi ca), Groom và Trainer (ca của mình) |
| `POST /medical/cases` | Groom, Trainer |
| `POST /medical/cases/action`, `PUT /medical/cases/{caseId}/close` | Veterinarian |
| `POST /horses/register`, `GET /horses/my-horses/{ownerId}` | Horse Owner (chỉ của mình), Club Manager |
| `GET /horses` | Club Manager, Trainer (chỉ ngựa được phân công), Veterinarian, Groom |
| `GET /training-plans`, `GET /training-plans/dashboard-stats` | Club Manager, Trainer (chỉ ngựa được phân công), Groom, Veterinarian |
| `GET /horses/{id}` | Nhân viên, và Horse Owner (chỉ ngựa của mình) |
| `PUT /horses/{id}/approve`, `PUT /horses/{id}/reject`, `PUT` / `DELETE /horses/{id}/assign-trainer`, `GET` / `PUT /users/{id}/barn-zone` | Club Manager |
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
- **L6. Một số API vẫn chưa phân quyền.** Các API protocols, vaccinations vẫn cho **mọi user đã login** gọi (khám tiền thi đấu đã giới hạn Veterinarian / Club Manager). Các API ngựa, `/medical/**` và `/medical-management/**` đã được phân quyền (xem mục 18).
- **L7. Thiếu API tạo dữ liệu.** Đã có API tạo Training Plan (6.0) và Care Task (9.0). Đã có module kho cho nhân viên kho (12A–12D). Đã có kiểm kho và cấp thuốc cho phác đồ (12E). Vẫn chưa có API tạo Stall.
- **L8. `AuditController` và `AudtitController` là class rỗng.** Hai class này không có endpoint nào và tên sau bị gõ sai chính tả.
