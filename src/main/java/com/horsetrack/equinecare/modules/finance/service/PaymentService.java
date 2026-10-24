package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.HorseOwnerProfile;
import com.horsetrack.equinecare.modules.auth.repository.HorseOwnerProfileRepository;
import com.horsetrack.equinecare.modules.finance.dto.VnPayPaymentDTO;
import com.horsetrack.equinecare.modules.finance.dto.VnPayResultDTO;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.finance.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Thanh toán hóa đơn qua VNPay Sandbox.
 * - Chủ ngựa bấm thanh toán: tạo giao dịch PENDING_VERIFICATION và trả URL sang VNPay.
 * - Hóa đơn chỉ chuyển PAID khi server xác minh: IPN của VNPay (đúng chữ ký, đúng mã giao dịch, đúng số tiền),
 *   hoặc truy vấn querydr khi trình duyệt quay về. Dữ liệu trên Return URL không bao giờ tự đánh dấu đã trả (BR_12).
 * - Xử lý một lần cho mỗi giao dịch (idempotent, khóa dòng giao dịch khi cập nhật).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final String METHOD_VNPAY = "VNPAY";

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final HorseOwnerProfileRepository ownerProfileRepository;
    private final InvoiceService invoiceService;
    private final VnPayGateway vnPay;
    private final AuditTrail auditTrail;
    private final EntityManager entityManager;
    private final ProtocolPhaseBilling protocolPhaseBilling;

    @Value("${app.vnpay.verify-on-return:true}")
    private boolean verifyOnReturn;

    /** Chủ ngựa tạo giao dịch VNPay cho hóa đơn của mình, số tiền = gốc + phí trễ hạn (+ phí kích hoạt lại) tính tới hôm nay */
    @Transactional
    public VnPayPaymentDTO createVnPayPayment(Integer invoiceId, String clientIp) {
        if (!vnPay.isConfigured()) {
            throw new PaymentGatewayException("Cổng VNPay Sandbox chưa được cấu hình (thiếu VNPAY_TMN_CODE / VNPAY_HASH_SECRET).");
        }
        Invoice invoice = invoiceService.findInvoice(invoiceId);
        invoiceService.checkOwner(invoice);
        // QA M4: khóa dòng hóa đơn để 2 lần bấm song song không cùng tạo giao dịch
        invoiceRepository.findByIdForUpdate(invoiceId);
        if (!invoice.isOpen()) {
            throw new IllegalArgumentException("Hóa đơn " + invoice.getInvoiceCode() + " không ở trạng thái chờ thanh toán (hiện tại: " + invoice.getPaymentStatus() + ").");
        }
        BigDecimal amount = BillingPolicy.vnd(invoiceService.amountDue(invoice, LocalDate.now()));
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("Hóa đơn không còn số tiền phải thanh toán.");
        }

        LocalDateTime nowVn = LocalDateTime.now(VnPayGateway.VN_ZONE);
        // QA M4: còn giao dịch đang chờ, chưa hết hạn, cùng số tiền thì trả lại đúng URL đó (cùng vnp_TxnRef, vnp_CreateDate)
        // thay vì tạo giao dịch mới - tránh chủ ngựa trả 2 lần cho 1 hóa đơn
        for (PaymentTransaction pending : transactionRepository.findByInvoice_InvoiceIdAndPaymentStatusOrderByPaidAtDesc(
                invoice.getInvoiceId(), PaymentTransaction.PENDING_VERIFICATION)) {
            LocalDateTime createdVn = createdAtVn(pending.getTransactionRef());
            if (createdVn == null || pending.getAmountPaid() == null || pending.getAmountPaid().compareTo(amount) != 0) {
                continue;
            }
            LocalDateTime expireVn = createdVn.plusMinutes(vnPay.getExpireMinutes());
            // Chừa 1 phút để chủ ngựa kịp thanh toán trước khi URL hết hạn
            if (expireVn.minusMinutes(1).isAfter(nowVn)) {
                return VnPayPaymentDTO.builder()
                        .transactionId(pending.getTransactionId())
                        .txnRef(pending.getTransactionRef())
                        .amount(amount)
                        .paymentUrl(vnPay.buildPaymentUrl(pending.getTransactionRef(), amount, orderInfo(invoice), clientIp, createdVn))
                        .expireAt(LocalDateTime.now().plus(java.time.Duration.between(nowVn, expireVn)))
                        .reused(true)
                        .build();
            }
        }
        // vnp_TxnRef duy nhất: <invoice_id>-<thời điểm tạo theo giờ VN>; phần thời điểm dùng lại cho truy vấn querydr
        String txnRef = invoice.getInvoiceId() + "-" + nowVn.format(VnPayGateway.VNP_TIME);
        PaymentTransaction tx = transactionRepository.save(PaymentTransaction.builder()
                .invoice(invoice)
                .paymentMethod(METHOD_VNPAY)
                .amountPaid(amount)
                .transactionRef(txnRef)
                .paymentStatus(PaymentTransaction.PENDING_VERIFICATION)
                .paidAt(LocalDateTime.now())
                .notes("Đã tạo yêu cầu thanh toán VNPay Sandbox, chờ VNPay xác nhận")
                .build());

        String url = vnPay.buildPaymentUrl(txnRef, amount, orderInfo(invoice), clientIp, nowVn);
        return VnPayPaymentDTO.builder()
                .transactionId(tx.getTransactionId())
                .txnRef(txnRef)
                .amount(amount)
                .paymentUrl(url)
                .expireAt(LocalDateTime.now().plusMinutes(vnPay.getExpireMinutes()))
                .build();
    }

    // vnp_TxnRef = <invoice_id>-<yyyyMMddHHmmss giờ VN lúc tạo>; null nếu không đúng định dạng
    private static LocalDateTime createdAtVn(String txnRef) {
        int dash = txnRef == null ? -1 : txnRef.lastIndexOf('-');
        if (dash < 0) {
            return null;
        }
        try {
            return LocalDateTime.parse(txnRef.substring(dash + 1), VnPayGateway.VNP_TIME);
        } catch (java.time.format.DateTimeParseException ex) {
            return null;
        }
    }

    /** IPN: VNPay gọi server-to-server. Trả về RspCode theo quy ước của VNPay. */
    @Transactional
    public Map<String, String> handleIpn(Map<String, String> params) {
        if (!vnPay.verifySignature(params)) {
            return rsp("97", "Invalid Checksum");
        }
        Optional<PaymentTransaction> found = transactionRepository.findByTransactionRef(params.get("vnp_TxnRef"));
        if (found.isEmpty()) {
            return rsp("01", "Order not found");
        }
        PaymentTransaction tx = lock(found.get());
        BigDecimal amount;
        try {
            amount = VnPayGateway.fromVnpAmount(params.get("vnp_Amount"));
        } catch (Exception e) {
            return rsp("04", "Invalid amount");
        }
        if (amount.compareTo(tx.getAmountPaid()) != 0) {
            return rsp("04", "Invalid amount");
        }
        if (!PaymentTransaction.PENDING_VERIFICATION.equals(tx.getPaymentStatus())) {
            return rsp("02", "Order already confirmed");
        }
        boolean success = "00".equals(params.get("vnp_ResponseCode")) && "00".equals(params.get("vnp_TransactionStatus"));
        finish(tx, success, params.get("vnp_TransactionNo"), params.get("vnp_ResponseCode"), "IPN");
        return rsp("00", "Confirm Success");
    }

    /**
     * Return URL: trình duyệt quay về sau khi thanh toán. Kiểm tra chữ ký, rồi (nếu bật) hỏi VNPay bằng querydr;
     * chỉ kết quả querydr hoặc IPN mới đổi trạng thái hóa đơn.
     */
    @Transactional
    public VnPayResultDTO handleReturn(Map<String, String> params, String clientIp) {
        String txnRef = params.get("vnp_TxnRef");
        String responseCode = params.get("vnp_ResponseCode");
        if (!vnPay.verifySignature(params)) {
            // Ghi lại để tra cứu (thường do sai VNPAY_HASH_SECRET); giao dịch vẫn chờ xác minh lại
            transactionRepository.findByTransactionRef(txnRef).ifPresent(t -> {
                if (PaymentTransaction.PENDING_VERIFICATION.equals(t.getPaymentStatus())) {
                    t.setNotes(truncate(stamp() + " Return: chữ ký không hợp lệ (mã VNPay " + responseCode + ")"));
                    transactionRepository.save(t);
                }
            });
            log.warn("VNPay Return {}: chữ ký không hợp lệ (kiểm tra VNPAY_HASH_SECRET)", txnRef);
            return VnPayResultDTO.builder().status("INVALID").message("Chữ ký dữ liệu trả về không hợp lệ.").txnRef(txnRef).responseCode(responseCode).build();
        }
        Optional<PaymentTransaction> found = transactionRepository.findByTransactionRef(txnRef);
        if (found.isEmpty()) {
            return VnPayResultDTO.builder().status("INVALID").message("Không tìm thấy giao dịch " + txnRef + ".").txnRef(txnRef).responseCode(responseCode).build();
        }
        PaymentTransaction tx = lock(found.get());

        if (PaymentTransaction.PENDING_VERIFICATION.equals(tx.getPaymentStatus()) && verifyOnReturn) {
            verifyWithVnPay(tx, clientIp, "Return");
        }
        return result(tx, responseCode);
    }

    /**
     * Hỏi lại VNPay (querydr) cho giao dịch còn chờ xác minh. Dùng khi chạy localhost (VNPay không gọi được IPN)
     * hoặc khi trình duyệt không quay về Return URL. Kết quả mỗi lần hỏi được ghi vào ghi chú giao dịch.
     */
    private void verifyWithVnPay(PaymentTransaction tx, String clientIp, String source) {
        VnPayGateway.QueryResult q = vnPay.queryTransaction(tx.getTransactionRef(), orderInfo(tx.getInvoice()), txnTime(tx), clientIp);
        if (q.paid() && q.amount() != null && q.amount().compareTo(tx.getAmountPaid()) == 0) {
            finish(tx, true, q.transactionNo(), q.responseCode(), "querydr/" + source);
            return;
        }
        if (q.ok() && "00".equals(q.responseCode()) && q.transactionStatus() != null
                && !"00".equals(q.transactionStatus()) && !"01".equals(q.transactionStatus())) {
            // VNPay xác nhận giao dịch không thành công (01 = đang xử lý thì giữ nguyên)
            finish(tx, false, q.transactionNo(), q.transactionStatus(), "querydr/" + source);
            return;
        }
        boolean expired = tx.getPaidAt() != null && tx.getPaidAt().isBefore(LocalDateTime.now().minusMinutes(vnPay.getExpireMinutes() + 30L));
        if (expired && q.ok() && "91".equals(q.responseCode())) {
            // VNPay không có giao dịch này và đã quá hạn thanh toán: chủ ngựa chưa thanh toán
            finish(tx, false, null, "91", "querydr/" + source + " - quá hạn, VNPay không ghi nhận giao dịch");
            return;
        }
        String detail = q.paid() ? "số tiền VNPay " + q.amount() + " khác số tiền giao dịch " + tx.getAmountPaid()
                : "chưa xác nhận - mã " + q.responseCode() + ", trạng thái " + q.transactionStatus() + (q.message() != null ? ", " + q.message() : "");
        tx.setNotes(truncate(stamp() + " " + source + " querydr: " + detail));
        transactionRepository.save(tx);
        log.warn("VNPay querydr {} ({}): {}", tx.getTransactionRef(), source, detail);
    }

    /** Hỏi lại VNPay các giao dịch còn chờ xác minh của chủ ngựa đang đăng nhập */
    @Transactional
    public int recheckMyPending(Integer ownerId, String clientIp) {
        if (!vnPay.isConfigured()) {
            return 0;
        }
        return recheck(transactionRepository.findByInvoice_Owner_UserIdAndPaymentStatus(ownerId, PaymentTransaction.PENDING_VERIFICATION), clientIp, "Tải lại");
    }

    /** Mỗi 5 phút hỏi lại VNPay các giao dịch còn chờ trong 3 ngày gần nhất */
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString = "${app.vnpay.recheck-ms:300000}", initialDelayString = "${app.vnpay.recheck-initial-delay-ms:60000}")
    @Transactional
    public void recheckRecent() {
        if (!vnPay.isConfigured()) {
            return;
        }
        int n = recheck(transactionRepository.findByPaymentStatusAndPaidAtAfterOrderByPaidAtAsc(PaymentTransaction.PENDING_VERIFICATION,
                LocalDateTime.now().minusDays(3)), "127.0.0.1", "Định kỳ");
        if (n > 0) {
            log.info("VNPay: xác minh lại {} giao dịch", n);
        }
    }

    // Trả về số giao dịch đã kết thúc (VERIFIED / FAILED) sau khi hỏi lại
    private int recheck(java.util.List<PaymentTransaction> pending, String clientIp, String source) {
        int done = 0;
        for (PaymentTransaction t : pending) {
            try {
                PaymentTransaction tx = lock(t);
                if (!PaymentTransaction.PENDING_VERIFICATION.equals(tx.getPaymentStatus())) {
                    continue;
                }
                verifyWithVnPay(tx, clientIp, source);
                if (!PaymentTransaction.PENDING_VERIFICATION.equals(tx.getPaymentStatus())) {
                    done++;
                }
            } catch (Exception ex) {
                log.warn("Không xác minh lại được giao dịch {}: {}", t.getTransactionRef(), ex.getMessage());
            }
        }
        return done;
    }

    private static String stamp() {
        return "[" + LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM HH:mm")) + "]";
    }

    private VnPayResultDTO result(PaymentTransaction tx, String responseCode) {
        Invoice inv = tx.getInvoice();
        String status;
        String message;
        switch (tx.getPaymentStatus()) {
            case PaymentTransaction.VERIFIED -> {
                status = "SUCCESS";
                message = "Thanh toán thành công. Hóa đơn " + inv.getInvoiceCode() + " đã được ghi nhận.";
            }
            case PaymentTransaction.FAILED -> {
                status = "FAILED";
                message = "Thanh toán không thành công. Bạn có thể thử lại.";
            }
            default -> {
                status = "PENDING";
                message = "00".equals(responseCode)
                        ? "VNPay báo thanh toán thành công, hệ thống đang chờ xác minh. Hóa đơn sẽ chuyển Đã thanh toán sau khi xác minh."
                        : "Giao dịch chưa hoàn tất hoặc đã bị hủy trên VNPay.";
            }
        }
        return VnPayResultDTO.builder()
                .status(status).message(message)
                .invoiceId(inv.getInvoiceId()).invoiceCode(inv.getInvoiceCode()).invoiceStatus(inv.getPaymentStatus())
                .txnRef(tx.getTransactionRef()).amount(tx.getAmountPaid()).responseCode(responseCode)
                .build();
    }

    // Kết thúc giao dịch (chỉ gọi khi đang PENDING_VERIFICATION và đã khóa dòng)
    private void finish(PaymentTransaction tx, boolean success, String gatewayNo, String code, String source) {
        tx.setGatewayTransId(gatewayNo);
        tx.setVerifiedAt(LocalDateTime.now());
        Invoice invoice = tx.getInvoice();
        if (!success) {
            tx.setPaymentStatus(PaymentTransaction.FAILED);
            tx.setNotes(truncate("VNPay báo không thành công (mã " + code + ", nguồn " + source + ")"));
            transactionRepository.save(tx);
            return;
        }
        tx.setPaymentStatus(PaymentTransaction.VERIFIED);
        boolean wasOpen = invoice.isOpen();
        tx.setNotes(truncate("VNPay xác nhận thành công (nguồn " + source + ")" + (wasOpen ? "" : " - hóa đơn đã thanh toán trước đó, cần đối soát hoàn tiền")));
        transactionRepository.save(tx);
        if (!wasOpen) {
            return;
        }
        String old = invoice.getPaymentStatus();
        invoice.setPaymentStatus(Invoice.PAID);
        invoiceRepository.saveAndFlush(invoice);

        // BR_14: chủ ngựa đang bị đình chỉ đã trả hết hóa đơn mở -> chờ Club Manager kích hoạt lại
        HorseOwnerProfile profile = ownerProfileRepository.findById(invoice.getOwner().getUserId()).orElse(null);
        boolean noMoreDebt = invoiceRepository.findByOwner_UserIdAndPaymentStatusInOrderByDueDateAsc(invoice.getOwner().getUserId(), Invoice.OPEN_STATUSES).isEmpty();
        if (profile != null && InvoiceService.SERVICE_SUSPENDED.equals(profile.getServiceStatus()) && noMoreDebt) {
            profile.setServiceStatus(InvoiceService.SERVICE_REACTIVATION_PENDING);
            ownerProfileRepository.save(profile);
        }
        // BR_16: hóa đơn giai đoạn phác đồ đã trả thì phác đồ đang tạm dừng được tiếp tục
        protocolPhaseBilling.onInvoicePaid(invoice);
        auditTrail.record("PAYMENT_VERIFIED", "Invoices", "payment_status=" + old,
                "payment_status=PAID | " + invoice.getInvoiceCode() + " | " + tx.getAmountPaid() + " VND | VNPay " + gatewayNo + " | " + source);
        invoiceService.notify(invoice.getOwner(), "[HorseTrack] Đã nhận thanh toán hóa đơn " + invoice.getInvoiceCode(),
                "<p>CLB đã nhận <b>" + InvoiceGenerator.money(tx.getAmountPaid()) + "</b> qua VNPay cho hóa đơn <b>"
                        + invoice.getInvoiceCode() + "</b> (mã giao dịch VNPay " + (gatewayNo == null ? "" : gatewayNo) + ").</p>");
    }

    private PaymentTransaction lock(PaymentTransaction tx) {
        entityManager.refresh(tx, LockModeType.PESSIMISTIC_WRITE);
        return tx;
    }

    // Thời điểm tạo giao dịch (giờ VN) nằm trong vnp_TxnRef
    private static LocalDateTime txnTime(PaymentTransaction tx) {
        String ref = tx.getTransactionRef();
        return LocalDateTime.parse(ref.substring(ref.lastIndexOf('-') + 1), VnPayGateway.VNP_TIME);
    }

    // Nội dung thanh toán: không dấu, không ký tự đặc biệt (yêu cầu của VNPay)
    private static String orderInfo(Invoice invoice) {
        return "Thanh toan hoa don " + invoice.getInvoiceCode();
    }

    private static Map<String, String> rsp(String code, String message) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("RspCode", code);
        m.put("Message", message);
        return m;
    }

    private static String truncate(String s) {
        return s.length() > 255 ? s.substring(0, 255) : s;
    }
}
