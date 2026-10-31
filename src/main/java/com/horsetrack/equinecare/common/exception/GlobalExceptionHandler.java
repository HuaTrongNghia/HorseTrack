package com.horsetrack.equinecare.common.exception;

import com.horsetrack.equinecare.common.base.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // Bắt lỗi Validation dữ liệu đầu vào (@NotBlank, @Email...)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(
            MethodArgumentNotValidException ex) {
        Map<String, String> errors = new HashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.put(error.getField(), error.getDefaultMessage());
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Map<String, String>>builder()
                        .success(false)
                        .message("Dữ liệu đầu vào không hợp lệ")
                        .data(errors)
                        .build());
    }

    // Bắt lỗi nghiệp vụ logic (Email trùng lặp, sai Role...)
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi không tìm thấy tài nguyên (HTTP 404)
    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi sai tên đăng nhập hoặc mật khẩu (HTTP 401)
    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadCredentials(BadCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi tài khoản bị vô hiệu hóa (HTTP 403)
    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiResponse<Void>> handleDisabledAccount(DisabledException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt các lỗi xác thực còn lại (HTTP 401)
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(ApiResponse.error("Xác thực thất bại: " + ex.getMessage()));
    }

    // Bắt lỗi không có quyền truy cập / vi phạm phân quyền nghiệp vụ (HTTP 403)
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(org.springframework.security.access.AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi gửi email (SMTP sai cấu hình hoặc mất kết nối) (HTTP 503)
    @ExceptionHandler(com.horsetrack.equinecare.modules.finance.service.PaymentGatewayException.class)
    public org.springframework.http.ResponseEntity<com.horsetrack.equinecare.common.base.ApiResponse<Object>> handlePaymentGateway(
            com.horsetrack.equinecare.modules.finance.service.PaymentGatewayException ex) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE)
                .body(com.horsetrack.equinecare.common.base.ApiResponse.error(ex.getMessage()));
    }

    @ExceptionHandler(com.horsetrack.equinecare.modules.notification.exception.EmailSendException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailSend(com.horsetrack.equinecare.modules.notification.exception.EmailSendException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResponse.error(ex.getMessage()));
    }

    // Bắt lỗi nghiệp vụ RuntimeException (ResourceNotFoundException -> 404, BusinessException -> 400)
    // Tệp tải lên vượt giới hạn multipart (spring.servlet.multipart.*)
    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleUploadTooLarge(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("Tệp tải lên quá lớn (giấy tờ tối đa 10 MB, ảnh đại diện tối đa 1 MB)."));
    }

    // QA H3: dòng dữ liệu vừa bị người khác cập nhật (khóa lạc quan @Version)
    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<ApiResponse<Void>> handleConcurrentUpdate(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error("Dữ liệu vừa được người khác cập nhật. Vui lòng tải lại và thử lại."));
    }

    // QA H3: vi phạm chỉ mục duy nhất khi 2 yêu cầu song song cùng ghi
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException ex) {
        String msg = String.valueOf(ex.getMostSpecificCause().getMessage());
        if (msg.contains("UX_plan_open_per_horse")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Ngựa này đang có một giáo án PENDING, ACTIVE hoặc PAUSED. Hãy hoàn thành hoặc hủy giáo án cũ trước."));
        }
        if (msg.contains("UX_CE_owner_active") || msg.contains("UX_CE_horse_active")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.error("Đã có đề xuất còn hiệu lực cho giải này (mỗi chủ ngựa / mỗi ngựa chỉ 1 đề xuất). Vui lòng tải lại."));
        }
        return handleRuntimeException(ex);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(RuntimeException ex) {
        String msg = ex.getMessage();
        if (msg != null && msg.contains("ResourceNotFoundException")) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(msg.replace("ResourceNotFoundException:", "").trim()));
        }
        if (msg != null && msg.contains("BusinessException")) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(ApiResponse.error(msg.replace("BusinessException:", "").trim()));
        }
        return serverError(ex);
    }

    // Bắt lỗi hệ thống ngoài dự kiến
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneralException(Exception ex) {
        return serverError(ex);
    }

    // Lỗi 500: không trả chi tiết kỹ thuật (câu SQL, tên bảng / cột, stack) cho client; ghi log đầy đủ kèm mã lỗi để tra cứu
    private ResponseEntity<ApiResponse<Void>> serverError(Exception ex) {
        String errorId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        log.error("Lỗi máy chủ [{}]: {}", errorId, ex.getMessage(), ex);
        String what = ex instanceof org.springframework.dao.DataAccessException ? "Lỗi truy vấn cơ sở dữ liệu" : "Lỗi máy chủ";
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error(what + " (mã lỗi " + errorId + "). Vui lòng thử lại sau hoặc báo quản trị viên kèm mã lỗi."));
    }
}