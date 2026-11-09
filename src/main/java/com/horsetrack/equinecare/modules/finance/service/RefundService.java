package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.finance.dto.RefundConfirmDTO;
import com.horsetrack.equinecare.modules.finance.dto.RefundDTO;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceRefund;
import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRefundRepository;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Hoàn tiền hóa đơn đã thanh toán: tạo khoản "chờ hoàn" (hóa đơn REFUND_PENDING), Quản lý CLB chuyển tiền ngoài hệ thống
 * rồi xác nhận kèm mã giao dịch, ngày chuyển, người chuyển (hóa đơn REFUNDED). Mọi khoản gom về GET /billing/refunds.
 */
@Service
@RequiredArgsConstructor
public class RefundService {

    public static final String REFUND_LINK = "/manager/refunds";
    private static final DateTimeFormatter DMY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRefundRepository refundRepository;
    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final InvoiceService invoiceService;
    private final AppNotificationService appNotifications;
    private final AuditTrail auditTrail;

    /**
     * Ghi khoản chờ hoàn cho hóa đơn đã thanh toán. Hóa đơn đã có khoản hoàn thì giữ nguyên (gọi lại không tạo thêm).
     * Số tiền = tổng giao dịch thanh toán thành công (kể cả phí trễ hạn), không có giao dịch thì lấy tiền gốc.
     */
    @Transactional
    public InvoiceRefund request(Invoice invoice, String reason) {
        var existing = refundRepository.findFirstByInvoice_InvoiceIdOrderByRefundIdDesc(invoice.getInvoiceId());
        if (existing.isPresent()) {
            return existing.get();
        }
        if (!Invoice.PAID.equals(invoice.getPaymentStatus())) {
            throw new IllegalArgumentException("Chỉ hoàn tiền hóa đơn đã thanh toán (hóa đơn " + invoice.getInvoiceCode()
                    + " đang " + invoice.getPaymentStatus() + ").");
        }
        BigDecimal amount = paidAmount(invoice);
        String why = StringUtils.hasText(reason) ? truncate(reason.trim(), 500) : "Hoàn tiền hóa đơn " + invoice.getInvoiceCode();
        invoice.setPaymentStatus(Invoice.REFUND_PENDING);
        invoiceRepository.save(invoice);
        InvoiceRefund refund = refundRepository.save(InvoiceRefund.builder()
                .invoice(invoice)
                .amount(amount)
                .reason(why)
                .status(InvoiceRefund.PENDING)
                .requestedAt(LocalDateTime.now())
                .build());
        auditTrail.record("REFUND_REQUESTED", "Invoices", "payment_status=PAID",
                "payment_status=REFUND_PENDING | " + invoice.getInvoiceCode() + " | " + InvoiceGenerator.money(amount) + " | " + why);
        String horse = invoice.getHorse() != null ? invoice.getHorse().getHorseName() : "";
        String owner = invoice.getOwner() != null ? invoice.getOwner().getFullName() : "";
        appNotifications.pushAll(userRepository.findByRoleNameInAndIsActiveTrue(List.of("Club Manager")), "REFUND",
                "Cần hoàn " + InvoiceGenerator.money(amount) + " hóa đơn " + invoice.getInvoiceCode(),
                "Chủ ngựa " + owner + (horse.isEmpty() ? "" : " (ngựa " + horse + ")") + ". Lý do: " + why
                        + ". Chuyển tiền xong hãy xác nhận ở màn hình Hoàn tiền.", REFUND_LINK);
        return refund;
    }

    /** Hóa đơn đang chờ hoàn hoặc đã hoàn: không tính là đã thanh toán */
    public static boolean isRefund(Invoice invoice) {
        return invoice != null && (Invoice.REFUND_PENDING.equals(invoice.getPaymentStatus()) || Invoice.REFUNDED.equals(invoice.getPaymentStatus()));
    }

    @Transactional(readOnly = true)
    public List<RefundDTO> list(String status) {
        List<InvoiceRefund> rows;
        if (!StringUtils.hasText(status)) {
            rows = refundRepository.findAllByOrderByRequestedAtDesc();
        } else {
            String st = status.trim().toUpperCase();
            if (!InvoiceRefund.PENDING.equals(st) && !InvoiceRefund.DONE.equals(st)) {
                throw new IllegalArgumentException("status chỉ nhận PENDING (chờ hoàn) hoặc DONE (đã hoàn).");
            }
            rows = refundRepository.findByStatusOrderByRequestedAtAsc(st);
        }
        return rows.stream().map(RefundService::toDto).toList();
    }

    @Transactional
    public RefundDTO confirm(Integer refundId, RefundConfirmDTO request) {
        InvoiceRefund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy khoản hoàn tiền với ID: " + refundId));
        if (!InvoiceRefund.PENDING.equals(refund.getStatus())) {
            throw new IllegalArgumentException("Khoản hoàn này đã được xác nhận ngày " + fmt(refund.getConfirmedAt()) + ".");
        }
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        User manager = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
        refund.setStatus(InvoiceRefund.DONE);
        refund.setTransactionRef(request.getTransactionRef().trim());
        refund.setTransferredOn(request.getTransferredOn());
        refund.setTransferredBy(request.getTransferredBy().trim());
        refund.setNote(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null);
        refund.setConfirmedBy(manager);
        refund.setConfirmedAt(LocalDateTime.now());
        refundRepository.save(refund);
        Invoice invoice = refund.getInvoice();
        invoice.setPaymentStatus(Invoice.REFUNDED);
        invoiceRepository.save(invoice);
        auditTrail.record("REFUND_CONFIRMED", "Invoices", "payment_status=REFUND_PENDING",
                "payment_status=REFUNDED | " + invoice.getInvoiceCode() + " | " + InvoiceGenerator.money(refund.getAmount())
                        + " | mã GD " + refund.getTransactionRef() + " | ngày " + refund.getTransferredOn().format(DMY)
                        + " | người chuyển " + refund.getTransferredBy());
        if (invoice.getOwner() != null) {
            invoiceService.notify(invoice.getOwner(), "[HorseTrack] Đã hoàn tiền hóa đơn " + invoice.getInvoiceCode(),
                    "<p>CLB đã chuyển hoàn <b>" + InvoiceGenerator.money(refund.getAmount()) + "</b> cho hóa đơn <b>" + invoice.getInvoiceCode() + "</b>.</p>"
                            + "<p>Mã giao dịch: <b>" + HtmlUtils.htmlEscape(refund.getTransactionRef()) + "</b>, ngày chuyển "
                            + refund.getTransferredOn().format(DMY) + ".</p><p>Lý do hoàn: " + HtmlUtils.htmlEscape(refund.getReason()) + "</p>");
        }
        return toDto(refund);
    }

    private static BigDecimal paidAmount(Invoice invoice) {
        BigDecimal paid = invoice.getTransactions().stream()
                .filter(t -> PaymentTransaction.VERIFIED.equals(t.getPaymentStatus()) && t.getAmountPaid() != null)
                .map(PaymentTransaction::getAmountPaid)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (paid.signum() > 0) {
            return paid;
        }
        return invoice.getFinalPayableAmount() == null ? BigDecimal.ZERO : invoice.getFinalPayableAmount();
    }

    private static RefundDTO toDto(InvoiceRefund r) {
        Invoice inv = r.getInvoice();
        User owner = inv.getOwner();
        boolean pending = InvoiceRefund.PENDING.equals(r.getStatus());
        return RefundDTO.builder()
                .refundId(r.getRefundId())
                .invoiceId(inv.getInvoiceId())
                .invoiceCode(inv.getInvoiceCode())
                .horseName(inv.getHorse() != null ? inv.getHorse().getHorseName() : null)
                .ownerId(owner != null ? owner.getUserId() : null)
                .ownerName(owner != null ? owner.getFullName() : null)
                .ownerEmail(owner != null ? owner.getEmail() : null)
                .ownerPhone(owner != null ? owner.getPhone() : null)
                .amount(r.getAmount())
                .reason(r.getReason())
                .status(r.getStatus())
                .statusLabel(pending ? "Chờ hoàn" : "Đã hoàn")
                .requestedAt(r.getRequestedAt())
                .transactionRef(r.getTransactionRef())
                .transferredOn(r.getTransferredOn())
                .transferredBy(r.getTransferredBy())
                .confirmedByName(r.getConfirmedBy() != null ? r.getConfirmedBy().getFullName() : null)
                .confirmedAt(r.getConfirmedAt())
                .note(r.getNote())
                .canConfirm(pending)
                .build();
    }

    private static String fmt(LocalDateTime t) {
        return t == null ? "" : t.format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
    }

    private static String truncate(String s, int max) {
        return s.length() > max ? s.substring(0, max) : s;
    }
}
