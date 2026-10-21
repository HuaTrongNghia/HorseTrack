package com.horsetrack.equinecare.modules.finance.service;

/** Cổng thanh toán chưa sẵn sàng (chưa cấu hình / không kết nối được) → HTTP 503 */
public class PaymentGatewayException extends RuntimeException {
    public PaymentGatewayException(String message) {
        super(message);
    }
}
