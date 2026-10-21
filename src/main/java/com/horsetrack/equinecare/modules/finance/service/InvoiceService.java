package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.HorseOwnerProfile;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.HorseOwnerProfileRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.finance.dto.*;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceLineItem;
import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.finance.repository.PaymentTransactionRepository;
import com.horsetrack.equinecare.modules.horse.entity.Stall;
import com.horsetrack.equinecare.modules.horse.repository.StallRepository;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceService {

    public static final String SERVICE_ACTIVE = "ACTIVE";
    public static final String SERVICE_SUSPENDED = "SUSPENDED";
    public static final String SERVICE_REACTIVATION_PENDING = "REACTIVATION_PENDING";

    static final Map<String, String> CATEGORY_LABELS = Map.of(
            "BOARDING", "Lưu chuồng",
            "DAILY_CARE", "Chăm sóc hằng ngày",
            "MEDICATION", "Thuốc điều trị",
            "MEDICAL", "Khám chữa bệnh",
            "TRAINING", "Huấn luyện",
            "DIET_EXTRA", "Dinh dưỡng bổ sung",
            "COMPETITION", "Thi đấu",
            "OTHER", "Khác");

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final HorseOwnerProfileRepository ownerProfileRepository;
    private final UserRepository userRepository;
    private final StallRepository stallRepository;
    private final BillingPolicy billingPolicy;
    private final AuditTrail auditTrail;
    private final EmailService emailService;

    // ==========================================
    // CHỦ NGỰA
    // ==========================================

    /** Hóa đơn của chủ ngựa đang đăng nhập; mặc định chỉ hóa đơn còn phải trả (SRS màn hình 14) */
    @Transactional(readOnly = true)
    public List<InvoiceDTO> getMyInvoices(boolean includeClosed) {
        User owner = currentUser();
        List<Invoice> invoices = includeClosed
                ? invoiceRepository.findByOwner_UserIdOrderByIssuedAtDesc(owner.getUserId())
                : invoiceRepository.findByOwner_UserIdAndPaymentStatusInOrderByDueDateAsc(owner.getUserId(), Invoice.OPEN_STATUSES);
        return toDTOs(invoices, false);
    }

    @Transactional(readOnly = true)
    public InvoiceDTO getMyInvoice(Integer invoiceId) {
        Invoice invoice = findInvoice(invoiceId);
        checkOwner(invoice);
        return toDTO(invoice, true);
    }

    /** Lịch sử giao dịch đã kết thúc (thành công / thất bại) của chủ ngựa (SRS màn hình 17) */
    @Transactional(readOnly = true)
    public List<PaymentTransactionDTO> getMyPaymentHistory() {
        User owner = currentUser();
        return transactionRepository.findByInvoice_Owner_UserIdAndPaymentStatusInOrderByPaidAtDesc(owner.getUserId(),
                        List.of(PaymentTransaction.VERIFIED, PaymentTransaction.FAILED))
                .stream().map(InvoiceService::toTxDTO).collect(Collectors.toList());
    }

    // ==========================================
    // CLUB MANAGER
    // ==========================================

    @Transactional(readOnly = true)
    public Page<InvoiceDTO> searchInvoices(String status, String billingMonth, Integer ownerId, int page, int size) {
        String st = StringUtils.hasText(status) ? status.trim().toUpperCase() : null;
        String month = StringUtils.hasText(billingMonth) ? billingMonth.trim() : null;
        Page<Invoice> result = invoiceRepository.search(st, month, ownerId, PageRequest.of(page, size));
        Map<Integer, HorseOwnerProfile> profiles = profilesOf(result.getContent());
        LocalDate today = LocalDate.now();
        return result.map(i -> map(i, today, false, profiles));
    }

    @Transactional(readOnly = true)
    public InvoiceDTO getInvoice(Integer invoiceId) {
        return toDTO(findInvoice(invoiceId), true);
    }

    @Transactional
    public InvoiceDTO cancelInvoice(Integer invoiceId, String reason) {
        Invoice invoice = findInvoice(invoiceId);
        if (!invoice.isOpen()) {
            throw new IllegalArgumentException("Chỉ hủy được hóa đơn đang chờ thanh toán (hiện tại: " + invoice.getPaymentStatus() + ").");
        }
        boolean hasPayment = invoice.getTransactions().stream()
                .anyMatch(t -> PaymentTransaction.VERIFIED.equals(t.getPaymentStatus()) || PaymentTransaction.PENDING_VERIFICATION.equals(t.getPaymentStatus()));
        if (hasPayment) {
            throw new IllegalArgumentException("Hóa đơn đã có giao dịch thanh toán, không thể hủy.");
        }
        String old = invoice.getPaymentStatus();
        invoice.setPaymentStatus(Invoice.CANCELLED);
        invoiceRepository.save(invoice);
        auditTrail.record("CANCEL_INVOICE", "Invoices", "payment_status=" + old,
                "payment_status=CANCELLED | " + invoice.getInvoiceCode() + " | Lý do: " + reason.trim());
        return toDTO(invoice, true);
    }

    /** BR_13: Club Manager đình chỉ dịch vụ khi chủ ngựa có hóa đơn chưa trả từ 25 ngày kể từ ngày phát hành */
    @Transactional
    public OwnerServiceDTO suspendService(Integer ownerId, String reason) {
        HorseOwnerProfile profile = findProfile(ownerId);
        if (SERVICE_SUSPENDED.equals(profile.getServiceStatus())) {
            throw new IllegalArgumentException("Dịch vụ của chủ ngựa này đang bị đình chỉ.");
        }
        LocalDate today = LocalDate.now();
        boolean eligible = invoiceRepository.findByOwner_UserIdAndPaymentStatusInOrderByDueDateAsc(ownerId, Invoice.OPEN_STATUSES)
                .stream().anyMatch(i -> billingPolicy.suspensionEligible(i, today));
        if (!eligible) {
            throw new IllegalArgumentException("Chỉ đình chỉ được khi có hóa đơn chưa thanh toán từ " + billingPolicy.getSuspensionDays()
                    + " ngày trở lên kể từ ngày phát hành (BR_13).");
        }
        String old = profile.getServiceStatus();
        profile.setServiceStatus(SERVICE_SUSPENDED);
        profile.setSuspendedSince(today);
        profile.setSuspensionReason(reason.trim());
        ownerProfileRepository.save(profile);
        auditTrail.record("SUSPEND_SERVICE", "Horse_Owner_Profiles", "service_status=" + old,
                "service_status=SUSPENDED | owner_id=" + ownerId + " | Lý do: " + reason.trim());

        notify(profile.getUser(), "[HorseTrack] Dịch vụ của bạn bị tạm đình chỉ",
                "<p>Dịch vụ lưu trú và chăm sóc ngựa của bạn bị <b>tạm đình chỉ</b> do hóa đơn quá hạn.</p>"
                        + "<p>Lý do: " + HtmlUtils.htmlEscape(reason.trim()) + "</p>"
                        + "<p>Để khôi phục, hãy thanh toán toàn bộ hóa đơn quá hạn qua VNPay. Số tiền gồm tiền gốc, phí trễ hạn "
                        + "và phí kích hoạt lại " + InvoiceGenerator.money(billingPolicy.getReactivationFee()) + " (BR_14).</p>");
        return toOwnerDTO(profile);
    }

    /** BR_14: kích hoạt lại sau khi chủ ngựa đã trả đủ 100% và giao dịch được xác minh (trạng thái REACTIVATION_PENDING) */
    @Transactional
    public OwnerServiceDTO reactivateService(Integer ownerId) {
        HorseOwnerProfile profile = findProfile(ownerId);
        if (!SERVICE_REACTIVATION_PENDING.equals(profile.getServiceStatus())) {
            throw new IllegalArgumentException("Chỉ kích hoạt lại được khi chủ ngựa đã thanh toán đủ và giao dịch đã được xác minh "
                    + "(trạng thái REACTIVATION_PENDING). Hiện tại: " + profile.getServiceStatus() + ".");
        }
        profile.setServiceStatus(SERVICE_ACTIVE);
        profile.setSuspendedSince(null);
        profile.setSuspensionReason(null);
        ownerProfileRepository.save(profile);
        auditTrail.record("REACTIVATE_SERVICE", "Horse_Owner_Profiles", "service_status=REACTIVATION_PENDING",
                "service_status=ACTIVE | owner_id=" + ownerId);
        notify(profile.getUser(), "[HorseTrack] Dịch vụ của bạn đã được khôi phục",
                "<p>CLB đã kích hoạt lại dịch vụ lưu trú và chăm sóc ngựa của bạn. Cảm ơn bạn đã thanh toán.</p>");
        return toOwnerDTO(profile);
    }

    @Transactional(readOnly = true)
    public OwnerServiceDTO getOwnerService(Integer ownerId) {
        return toOwnerDTO(findProfile(ownerId));
    }

    /** Club Manager đặt phí lưu chuồng hằng tháng, dùng cho dòng BOARDING của hóa đơn */
    @Transactional
    public BigDecimal updateStallFee(Integer stallId, BigDecimal fee) {
        Stall stall = stallRepository.findById(stallId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy chuồng với ID: " + stallId));
        BigDecimal old = stall.getMonthlyBaseFee();
        stall.setMonthlyBaseFee(BillingPolicy.vnd(fee));
        stall.setUpdatedAt(LocalDateTime.now());
        stallRepository.save(stall);
        auditTrail.record("UPDATE_STALL_FEE", "Stalls", "monthly_base_fee=" + old,
                "monthly_base_fee=" + stall.getMonthlyBaseFee() + " | chuồng " + stall.getStallCode());
        return stall.getMonthlyBaseFee();
    }

    // ==========================================
    // TÍNH TIỀN / MAPPING
    // ==========================================

    /** Phí kích hoạt lại chỉ tính một lần cho chủ ngựa đang bị đình chỉ: gắn vào hóa đơn mở có hạn sớm nhất */
    BigDecimal reactivationFee(Invoice invoice, HorseOwnerProfile profile) {
        if (profile == null || !SERVICE_SUSPENDED.equals(profile.getServiceStatus()) || !invoice.isOpen()) {
            return BigDecimal.ZERO;
        }
        Invoice first = invoiceRepository.findByOwner_UserIdAndPaymentStatusInOrderByDueDateAsc(invoice.getOwner().getUserId(), Invoice.OPEN_STATUSES)
                .stream().min(Comparator.comparing(Invoice::getDueDate).thenComparing(Invoice::getInvoiceId)).orElse(null);
        return first != null && first.getInvoiceId().equals(invoice.getInvoiceId()) ? billingPolicy.getReactivationFee() : BigDecimal.ZERO;
    }

    /** Số tiền phải trả nếu thanh toán hôm nay = gốc + phí trễ hạn (+ phí kích hoạt lại) */
    BigDecimal amountDue(Invoice invoice, LocalDate today) {
        HorseOwnerProfile profile = ownerProfileRepository.findById(invoice.getOwner().getUserId()).orElse(null);
        return invoice.getFinalPayableAmount().add(billingPolicy.lateFee(invoice, today)).add(reactivationFee(invoice, profile));
    }

    InvoiceDTO toDTO(Invoice invoice, boolean detail) {
        return map(invoice, LocalDate.now(), detail, profilesOf(List.of(invoice)));
    }

    private List<InvoiceDTO> toDTOs(List<Invoice> invoices, boolean detail) {
        Map<Integer, HorseOwnerProfile> profiles = profilesOf(invoices);
        LocalDate today = LocalDate.now();
        return invoices.stream().map(i -> map(i, today, detail, profiles)).collect(Collectors.toList());
    }

    private Map<Integer, HorseOwnerProfile> profilesOf(List<Invoice> invoices) {
        List<Integer> ids = invoices.stream().map(i -> i.getOwner().getUserId()).distinct().collect(Collectors.toList());
        return ownerProfileRepository.findAllById(ids).stream().collect(Collectors.toMap(HorseOwnerProfile::getUserId, p -> p));
    }

    private InvoiceDTO map(Invoice i, LocalDate today, boolean detail, Map<Integer, HorseOwnerProfile> profiles) {
        HorseOwnerProfile profile = profiles.get(i.getOwner().getUserId());
        BigDecimal lateFee = billingPolicy.lateFee(i, today);
        BigDecimal reactivation = reactivationFee(i, profile);
        BigDecimal paid = i.getTransactions().stream().filter(t -> PaymentTransaction.VERIFIED.equals(t.getPaymentStatus()))
                .map(PaymentTransaction::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        return InvoiceDTO.builder()
                .invoiceId(i.getInvoiceId())
                .invoiceCode(i.getInvoiceCode())
                .horseId(i.getHorse().getHorseId())
                .horseName(i.getHorse().getHorseName())
                .ownerId(i.getOwner().getUserId())
                .ownerName(i.getOwner().getFullName())
                .ownerEmail(i.getOwner().getEmail())
                .ownerServiceStatus(profile != null ? profile.getServiceStatus() : null)
                .billingMonth(i.getBillingMonth())
                .issuedAt(i.getIssuedAt())
                .dueDate(i.getDueDate())
                .status(i.getPaymentStatus())
                .subtotal(i.getSubtotalAmount())
                .prizeDeduction(i.getPrizeDeductionAmount())
                .principal(i.getFinalPayableAmount())
                .lateFee(lateFee)
                .reactivationFee(reactivation)
                .amountDue(i.isOpen() ? i.getFinalPayableAmount().add(lateFee).add(reactivation) : BigDecimal.ZERO)
                .amountPaid(paid)
                .daysOverdue(billingPolicy.daysOverdue(i, today))
                .daysSinceIssue(billingPolicy.daysSinceIssue(i, today))
                .suspensionEligible(billingPolicy.suspensionEligible(i, today))
                .terminationDue(billingPolicy.terminationDue(i, today))
                .lines(detail ? i.getLines().stream().map(InvoiceService::toLineDTO).collect(Collectors.toList()) : null)
                .transactions(detail ? i.getTransactions().stream().map(InvoiceService::toTxDTO).collect(Collectors.toList()) : null)
                .build();
    }

    static InvoiceLineDTO toLineDTO(InvoiceLineItem l) {
        return InvoiceLineDTO.builder()
                .lineItemId(l.getLineItemId())
                .category(l.getExpenseCategory())
                .categoryLabel(CATEGORY_LABELS.getOrDefault(l.getExpenseCategory(), l.getExpenseCategory()))
                .referenceId(l.getReferenceId())
                .description(l.getItemDescription())
                .unitPrice(l.getUnitPrice())
                .quantity(l.getQuantity())
                .amount(BillingPolicy.vnd(l.getAmount()))
                .serviceDate(l.getServiceDate())
                .build();
    }

    static PaymentTransactionDTO toTxDTO(PaymentTransaction t) {
        return PaymentTransactionDTO.builder()
                .transactionId(t.getTransactionId())
                .invoiceId(t.getInvoice().getInvoiceId())
                .invoiceCode(t.getInvoice().getInvoiceCode())
                .paymentMethod(t.getPaymentMethod())
                .amount(t.getAmountPaid())
                .transactionRef(t.getTransactionRef())
                .gatewayTransId(t.getGatewayTransId())
                .status(t.getPaymentStatus())
                .createdAt(t.getPaidAt())
                .verifiedAt(t.getVerifiedAt())
                .notes(t.getNotes())
                .build();
    }

    private OwnerServiceDTO toOwnerDTO(HorseOwnerProfile p) {
        return OwnerServiceDTO.builder()
                .ownerId(p.getUserId())
                .fullName(p.getUser() != null ? p.getUser().getFullName() : null)
                .email(p.getUser() != null ? p.getUser().getEmail() : null)
                .serviceStatus(p.getServiceStatus())
                .suspendedSince(p.getSuspendedSince())
                .suspensionReason(p.getSuspensionReason())
                .build();
    }

    Invoice findInvoice(Integer invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hóa đơn với ID: " + invoiceId));
    }

    private HorseOwnerProfile findProfile(Integer ownerId) {
        return ownerProfileRepository.findById(ownerId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy hồ sơ chủ ngựa với ID: " + ownerId));
    }

    void checkOwner(Invoice invoice) {
        if (!invoice.getOwner().getUserId().equals(currentUser().getUserId())) {
            throw new AccessDeniedException("Bạn không phải chủ của hóa đơn này");
        }
    }

    public User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    void notify(User user, String subject, String body) {
        if (user == null || !StringUtils.hasText(user.getEmail())) {
            return;
        }
        try {
            emailService.sendAsync(user.getEmail(), subject, "<p>Xin chào <b>" + HtmlUtils.htmlEscape(user.getFullName()) + "</b>,</p>" + body);
        } catch (Exception ex) {
            log.warn("Không gửi được email tới {}: {}", user.getEmail(), ex.getMessage());
        }
    }
}
