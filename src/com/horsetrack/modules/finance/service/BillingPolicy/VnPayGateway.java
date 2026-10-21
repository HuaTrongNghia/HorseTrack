package com.horsetrack.equinecare.modules.finance.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Kết nối cổng VNPay - CHỈ môi trường thử nghiệm Sandbox (sandbox.vnpayment.vn), API phiên bản 2.1.0.
 * Mã website (vnp_TmnCode) và chuỗi bí mật (vnp_HashSecret) lấy từ biến môi trường VNPAY_TMN_CODE / VNPAY_HASH_SECRET,
 * không ghi vào mã nguồn.
 */
@Slf4j
@Component
public class VnPayGateway {

    public static final ZoneId VN_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    public static final DateTimeFormatter VNP_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String VERSION = "2.1.0";

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String apiUrl;
    private final String returnUrl;
    private final int expireMinutes;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public VnPayGateway(@Value("${app.vnpay.tmn-code:}") String tmnCode,
                        @Value("${app.vnpay.hash-secret:}") String hashSecret,
                        @Value("${app.vnpay.pay-url:https://sandbox.vnpayment.vn/paymentv2/vpcpay.html}") String payUrl,
                        @Value("${app.vnpay.api-url:https://sandbox.vnpayment.vn/merchant_webapi/api/transaction}") String apiUrl,
                        @Value("${app.vnpay.return-url:http://localhost:8080/api/v1/payments/vnpay/return}") String returnUrl,
                        @Value("${app.vnpay.expire-minutes:15}") int expireMinutes) {
        this.tmnCode = tmnCode == null ? "" : tmnCode.trim();
        this.hashSecret = hashSecret == null ? "" : hashSecret.trim();
        this.payUrl = payUrl;
        this.apiUrl = apiUrl;
        this.returnUrl = returnUrl;
        this.expireMinutes = expireMinutes;
        // Chỉ cho phép môi trường Sandbox
        if (!isSandbox(payUrl) || !isSandbox(apiUrl)) {
            throw new IllegalStateException("Chỉ được cấu hình VNPay Sandbox (sandbox.vnpayment.vn). pay-url=" + payUrl + ", api-url=" + apiUrl);
        }
        if (!isConfigured()) {
            log.warn("VNPay Sandbox CHƯA cấu hình: đặt biến môi trường VNPAY_TMN_CODE và VNPAY_HASH_SECRET để bật thanh toán.");
        }
    }

    private static boolean isSandbox(String url) {
        try {
            return "sandbox.vnpayment.vn".equalsIgnoreCase(URI.create(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isConfigured() {
        return StringUtils.hasText(tmnCode) && StringUtils.hasText(hashSecret);
    }

    public int getExpireMinutes() {
        return expireMinutes;
    }

    /** Tạo URL chuyển sang trang thanh toán VNPay Sandbox */
    public String buildPaymentUrl(String txnRef, BigDecimal amount, String orderInfo, String clientIp, LocalDateTime createdAtVn) {
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", VERSION);
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", toVnpAmount(amount));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_TxnRef", txnRef);
        p.put("vnp_OrderInfo", orderInfo);
        p.put("vnp_OrderType", "other");
        p.put("vnp_Locale", "vn");
        p.put("vnp_ReturnUrl", returnUrl);
        p.put("vnp_IpAddr", StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1");
        p.put("vnp_CreateDate", createdAtVn.format(VNP_TIME));
        p.put("vnp_ExpireDate", createdAtVn.plusMinutes(expireMinutes).format(VNP_TIME));
        String query = encodeSorted(p);
        return payUrl + "?" + query + "&vnp_SecureHash=" + hmacSha512(hashSecret, query);
    }

    /** Kiểm tra chữ ký của dữ liệu VNPay gửi về (IPN / Return URL) */
    public boolean verifySignature(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        if (!StringUtils.hasText(received) || !isConfigured()) {
            return false;
        }
        Map<String, String> fields = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !"vnp_SecureHash".equals(k) && !"vnp_SecureHashType".equals(k) && StringUtils.hasText(v)) {
                fields.put(k, v);
            }
        });
        return hmacSha512(hashSecret, encodeSorted(fields)).equalsIgnoreCase(received);
    }

    /** Kết quả truy vấn giao dịch phía server (querydr) */
    public record QueryResult(boolean ok, String responseCode, String transactionStatus, BigDecimal amount, String transactionNo, String message) {
        public boolean paid() {
            return ok && "00".equals(responseCode) && "00".equals(transactionStatus);
        }
    }

    /**
     * Hỏi VNPay trạng thái thật của giao dịch (server-to-server). Dùng khi trình duyệt quay về Return URL, để xác minh
     * được cả khi chạy trên localhost (VNPay không gọi được IPN vào máy local).
     */
    public QueryResult queryTransaction(String txnRef, String orderInfo, LocalDateTime transactionDateVn, String clientIp) {
        try {
            String requestId = UUID.randomUUID().toString().replace("-", "").substring(0, 20);
            String createDate = LocalDateTime.now(VN_ZONE).format(VNP_TIME);
            String transDate = transactionDateVn.format(VNP_TIME);
            String ip = StringUtils.hasText(clientIp) ? clientIp : "127.0.0.1";
            String data = String.join("|", requestId, VERSION, "querydr", tmnCode, txnRef, transDate, createDate, ip, orderInfo);

            Map<String, String> body = new LinkedHashMap<>();
            body.put("vnp_RequestId", requestId);
            body.put("vnp_Version", VERSION);
            body.put("vnp_Command", "querydr");
            body.put("vnp_TmnCode", tmnCode);
            body.put("vnp_TxnRef", txnRef);
            body.put("vnp_OrderInfo", orderInfo);
            body.put("vnp_TransactionDate", transDate);
            body.put("vnp_CreateDate", createDate);
            body.put("vnp_IpAddr", ip);
            body.put("vnp_SecureHash", hmacSha512(hashSecret, data));

            HttpRequest req = HttpRequest.newBuilder(URI.create(apiUrl))
                    .timeout(Duration.ofSeconds(15))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(body)))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            @SuppressWarnings("unchecked")
            Map<String, Object> r = objectMapper.readValue(res.body(), Map.class);

            String responseCode = str(r.get("vnp_ResponseCode"));
            // Chữ ký phản hồi querydr theo tài liệu VNPay
            String respData = String.join("|", str(r.get("vnp_ResponseId")), str(r.get("vnp_Command")), responseCode,
                    str(r.get("vnp_Message")), str(r.get("vnp_TmnCode")), str(r.get("vnp_TxnRef")), str(r.get("vnp_Amount")),
                    str(r.get("vnp_BankCode")), str(r.get("vnp_PayDate")), str(r.get("vnp_TransactionNo")),
                    str(r.get("vnp_TransactionType")), str(r.get("vnp_TransactionStatus")), str(r.get("vnp_OrderInfo")),
                    str(r.get("vnp_PromotionCode")), str(r.get("vnp_PromotionAmount")));
            boolean signed = hmacSha512(hashSecret, respData).equalsIgnoreCase(str(r.get("vnp_SecureHash")));
            if (!signed) {
                log.warn("querydr {}: chữ ký phản hồi không hợp lệ (mã {}, {})", txnRef, responseCode, r.get("vnp_Message"));
                return new QueryResult(false, responseCode, null, null, null, "Chữ ký phản hồi querydr không hợp lệ");
            }
            BigDecimal amount = StringUtils.hasText(str(r.get("vnp_Amount"))) ? new BigDecimal(str(r.get("vnp_Amount"))).movePointLeft(2) : null;
            return new QueryResult(true, responseCode, str(r.get("vnp_TransactionStatus")), amount,
                    str(r.get("vnp_TransactionNo")), str(r.get("vnp_Message")));
        } catch (Exception ex) {
            log.warn("querydr {} thất bại: {}", txnRef, ex.getMessage());
            return new QueryResult(false, null, null, null, null, ex.getMessage());
        }
    }

    /** VNPay nhận số tiền x100, không có phần thập phân */
    public static String toVnpAmount(BigDecimal amount) {
        return BillingPolicy.vnd(amount).multiply(BigDecimal.valueOf(100)).toBigInteger().toString();
    }

    public static BigDecimal fromVnpAmount(String vnpAmount) {
        return new BigDecimal(vnpAmount).movePointLeft(2);
    }

    // key=value nối bằng &, theo thứ tự tên trường, giá trị URL-encode US-ASCII (chuẩn VNPay 2.1.0)
    static String encodeSorted(Map<String, String> sorted) {
        StringJoiner j = new StringJoiner("&");
        sorted.forEach((k, v) -> {
            if (StringUtils.hasText(v)) {
                j.add(URLEncoder.encode(k, StandardCharsets.US_ASCII) + "=" + URLEncoder.encode(v, StandardCharsets.US_ASCII));
            }
        });
        return j.toString();
    }

    static String hmacSha512(String key, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(raw.length * 2);
            for (byte b : raw) {
                sb.append(String.format("%02x", b & 0xff));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("Không tạo được chữ ký HMAC-SHA512", e);
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }
}
