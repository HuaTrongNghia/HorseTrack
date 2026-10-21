package com.horsetrack.equinecare.modules.notification.exception;

/** Gửi email thất bại (sai cấu hình SMTP, mất kết nối, máy chủ mail từ chối...). */
public class EmailSendException extends RuntimeException {
    public EmailSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
