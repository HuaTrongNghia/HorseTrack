package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceLineItem;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import com.horsetrack.equinecare.modules.medical.repository.VetCaseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Thanh toán phác đồ điều trị theo từng giai đoạn (BR_16, BR_17 / F3-07, F3-08).
 * - Khi bác sĩ xác nhận phác đồ: mỗi giai đoạn có thuốc được lập một hóa đơn riêng (mã GD-&lt;protocol_id&gt;-&lt;phase_no&gt;),
 *   dòng MEDICATION lấy từ Protocol_Items, hạn thanh toán là ngày bắt đầu giai đoạn.
 * - Tới ngày bắt đầu một giai đoạn mà hóa đơn của giai đoạn đó chưa thanh toán: phác đồ chuyển ON_HOLD_PAYMENT,
 *   kho không được cấp thuốc, bác sĩ không ghi tiến trình (BR_16).
 * - Ngoại lệ BR_17: phác đồ của ca cấp cứu (Vet_Cases.severity = CRITICAL) không bị tạm dừng vì chưa thanh toán.
 * - Thanh toán xong (giao dịch VNPay đã xác minh) thì phác đồ tự tiếp tục: IN_TREATMENT nếu đã cấp thuốc, ngược lại VET_CONFIRMED.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolPhaseBilling {

    public static final String ON_HOLD = "ON_HOLD_PAYMENT";
    /** Trạng thái phác đồ còn hiệu lực có thể bị tạm dừng vì chưa thanh toán */
    public static final List<String> PAYABLE_STATES = List.of("VET_CONFIRMED", "READY_FOR_DISPENSING", "IN_TREATMENT");
    private static final Pattern CODE = Pattern.compile("^GD-(\\d{6})-(\\d{2})");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository invoiceRepository;
    private final TreatmentProtocolRepository protocolRepository;
    private final VetCaseRepository vetCaseRepository;
    private final StockHoldRepository holdRepository;
    private final InvoiceService invoiceService;
    private final AuditTrail auditTrail;

    // ==========================================
    // LẬP / HỦY HÓA ĐƠN GIAI ĐOẠN
    // ==========================================

    /** Gọi khi bác sĩ xác nhận phác đồ. Trả về số hóa đơn vừa lập. */
    @Transactional
    public int issuePhaseInvoices(TreatmentProtocol protocol) {
        LocalDate today = LocalDate.now();
        int created = 0;
        StringBuilder rows = new StringBuilder();
        for (ProtocolPhase phase : protocol.getPhases()) {
            if (findPhaseInvoice(protocol.getProtocolId(), phase.getPhaseNo()).isPresent()) {
                continue;
            }
            List<InvoiceLineItem> lines = new ArrayList<>();
            for (ProtocolItem it : phase.getItems()) {
                BigDecimal qty = BigDecimal.valueOf(it.getQuantity() == null ? 0 : it.getQuantity());
                if (it.getUnitPrice() == null || it.getUnitPrice().signum() <= 0 || qty.signum() <= 0) {
                    continue;
                }
                String name = it.getItem() != null ? it.getItem().getItemName() : "Thuốc";
                String desc = "Phác đồ " + protocol.getProtocolCode() + " - GĐ " + phase.getPhaseNo() + " (" + phase.getPhaseName() + "): " + name;
                lines.add(InvoiceLineItem.builder()
                        .expenseCategory("MEDICATION")
                        .referenceId(it.getProtocolItemId())
                        .itemDescription(desc.length() > 255 ? desc.substring(0, 255) : desc)
                        .unitPrice(it.getUnitPrice())
                        .quantity(qty)
                        .serviceDate(phase.getStartDate().atTime(8, 0))
                        .build());
            }
            BigDecimal total = BillingPolicy.vnd(lines.stream().map(InvoiceLineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
            if (lines.isEmpty() || total.signum() <= 0) {
                continue;
            }
            Invoice invoice = Invoice.builder()
                    .invoiceCode(nextCode(protocol.getProtocolId(), phase.getPhaseNo()))
                    .horse(protocol.getHorse())
                    .owner(protocol.getHorse().getOwner())
                    .billingMonth(YearMonth.from(phase.getStartDate()).toString())
                    .subtotalAmount(total)
                    .prizeDeductionAmount(BigDecimal.ZERO)
                    .finalPayableAmount(total)
                    .paymentStatus(Invoice.UNPAID)
                    .issuedAt(LocalDateTime.now())
                    // Phải thanh toán trước khi giai đoạn bắt đầu; giai đoạn đã bắt đầu thì hạn là hôm nay
                    .dueDate(phase.getStartDate().isBefore(today) ? today : phase.getStartDate())
                    .build();
            lines.forEach(l -> l.setInvoice(invoice));
            invoice.getLines().addAll(lines);
            invoiceRepository.save(invoice);
            created++;
            rows.append("<li>Giai đoạn ").append(phase.getPhaseNo()).append(" (").append(HtmlUtils.htmlEscape(phase.getPhaseName()))
                    .append(") - bắt đầu ").append(phase.getStartDate().format(DATE)).append(": <b>").append(invoice.getInvoiceCode())
                    .append("</b>, ").append(InvoiceGenerator.money(total)).append("</li>");
        }
        if (created > 0) {
            invoiceRepository.flush();
            auditTrail.record("ISSUE_PHASE_INVOICE", "Invoices", null,
                    "Phác đồ " + protocol.getProtocolCode() + " (protocol_id=" + protocol.getProtocolId() + "): lập " + created + " hóa đơn giai đoạn");
            invoiceService.notify(protocol.getHorse().getOwner(), "[HorseTrack] Hóa đơn điều trị cho ngựa " + protocol.getHorse().getHorseName(),
                    "<p>Phác đồ điều trị <b>" + protocol.getProtocolCode() + "</b> của ngựa <b>" + HtmlUtils.htmlEscape(protocol.getHorse().getHorseName())
                            + "</b> đã được bác sĩ xác nhận. Chi phí thuốc được thu theo từng giai đoạn:</p><ul>" + rows + "</ul>"
                            + "<p>Vui lòng thanh toán qua VNPay <b>trước ngày bắt đầu</b> mỗi giai đoạn. Giai đoạn chưa thanh toán sẽ bị "
                            + "<b>tạm dừng điều trị</b> (BR_16), trừ trường hợp cấp cứu.</p>");
        }
        return created;
    }

    /** Phác đồ bị thay thế / hủy / kết thúc sớm: hủy hóa đơn giai đoạn chưa thanh toán (không có giao dịch). */
    @Transactional
    public int cancelOpenPhaseInvoices(TreatmentProtocol protocol, boolean onlyNotStarted, String reason) {
        LocalDate today = LocalDate.now();
        int n = 0;
        for (ProtocolPhase phase : protocol.getPhases()) {
            if (onlyNotStarted && !phase.getStartDate().isAfter(today)) {
                continue;
            }
            Optional<Invoice> inv = findPhaseInvoice(protocol.getProtocolId(), phase.getPhaseNo());
            if (inv.isPresent() && inv.get().isOpen() && inv.get().getTransactions().stream()
                    .noneMatch(t -> "VERIFIED".equals(t.getPaymentStatus()) || "PENDING_VERIFICATION".equals(t.getPaymentStatus()))) {
                inv.get().setPaymentStatus(Invoice.CANCELLED);
                invoiceRepository.save(inv.get());
                auditTrail.record("CANCEL_INVOICE", "Invoices", "payment_status=OPEN",
                        "payment_status=CANCELLED | " + inv.get().getInvoiceCode() + " | " + reason);
                n++;
            }
        }
        return n;
    }

    // ==========================================
    // TẠM DỪNG / TIẾP TỤC (BR_16, BR_17)
    // ==========================================

    /** Ca cấp cứu (BR_17): phác đồ gắn với ca khám mức CRITICAL */
    public boolean isEmergency(TreatmentProtocol protocol) {
        if (protocol.getCaseId() == null) {
            return false;
        }
        return vetCaseRepository.findById(protocol.getCaseId())
                .map(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity()))
                .orElse(false);
    }

    /** Giai đoạn đầu tiên đã tới ngày bắt đầu nhưng hóa đơn chưa thanh toán (rỗng nếu không có hoặc là ca cấp cứu) */
    @Transactional(readOnly = true)
    public Optional<ProtocolPhase> blockingPhase(TreatmentProtocol protocol, LocalDate today) {
        if (isEmergency(protocol)) {
            return Optional.empty();
        }
        return protocol.getPhases().stream()
                .filter(ph -> !ph.getStartDate().isAfter(today))
                .filter(ph -> findPhaseInvoice(protocol.getProtocolId(), ph.getPhaseNo()).map(Invoice::isOpen).orElse(false))
                .min(Comparator.comparing(ProtocolPhase::getPhaseNo));
    }

    /** Đưa phác đồ về đúng trạng thái theo tình hình thanh toán. Trả về true nếu trạng thái thay đổi. */
    @Transactional
    public boolean evaluate(TreatmentProtocol protocol, LocalDate today) {
        Optional<ProtocolPhase> blocking = blockingPhase(protocol, today);
        if (PAYABLE_STATES.contains(protocol.getState()) && blocking.isPresent()) {
            hold(protocol, blocking.get());
            return true;
        }
        if (ON_HOLD.equals(protocol.getState()) && blocking.isEmpty()) {
            resume(protocol);
            return true;
        }
        return false;
    }

    /** Hằng ngày 00:20: kiểm tra mọi phác đồ đang hiệu lực hoặc đang tạm dừng */
    @Scheduled(cron = "${app.billing.protocol-hold-cron:0 20 0 * * *}")
    @Transactional
    public void runDaily() {
        int n = run(LocalDate.now());
        if (n > 0) {
            log.info("BR_16: {} phác đồ đổi trạng thái theo thanh toán giai đoạn", n);
        }
    }

    @Transactional
    public int run(LocalDate today) {
        List<String> states = new ArrayList<>(PAYABLE_STATES);
        states.add(ON_HOLD);
        int changed = 0;
        for (TreatmentProtocol p : protocolRepository.findByStateInOrderByRequiredByAscCreatedAtAsc(states)) {
            if (evaluate(p, today)) {
                changed++;
            }
        }
        return changed;
    }

    /** Gọi khi một hóa đơn được thanh toán (giao dịch VNPay đã xác minh) */
    @Transactional
    public void onInvoicePaid(Invoice invoice) {
        Matcher m = CODE.matcher(invoice.getInvoiceCode());
        if (!m.find()) {
            return;
        }
        protocolRepository.findById(Integer.parseInt(m.group(1))).ifPresent(p -> evaluate(p, LocalDate.now()));
    }

    private void hold(TreatmentProtocol protocol, ProtocolPhase phase) {
        String old = protocol.getState();
        Invoice inv = findPhaseInvoice(protocol.getProtocolId(), phase.getPhaseNo()).orElseThrow();
        protocol.setState(ON_HOLD);
        protocol.setNote(appendNote(protocol.getNote(), "Tạm dừng " + LocalDate.now().format(DATE) + ": chưa thanh toán giai đoạn "
                + phase.getPhaseNo() + " (" + inv.getInvoiceCode() + ") - BR_16"));
        protocolRepository.save(protocol);
        auditTrail.record("PROTOCOL_PAYMENT_HOLD", "Treatment_Protocols", "state=" + old,
                "state=ON_HOLD_PAYMENT | " + protocol.getProtocolCode() + " | giai đoạn " + phase.getPhaseNo() + " | hóa đơn " + inv.getInvoiceCode());
        String body = "<p>Phác đồ <b>" + protocol.getProtocolCode() + "</b> của ngựa <b>" + HtmlUtils.htmlEscape(protocol.getHorse().getHorseName())
                + "</b> đã <b>tạm dừng</b> vì giai đoạn " + phase.getPhaseNo() + " (bắt đầu " + phase.getStartDate().format(DATE)
                + ") chưa được thanh toán - hóa đơn <b>" + inv.getInvoiceCode() + "</b> (BR_16).</p>"
                + "<p>Phác đồ tự tiếp tục ngay khi thanh toán qua VNPay được xác minh.</p>";
        invoiceService.notify(protocol.getHorse().getOwner(), "[HorseTrack] Tạm dừng điều trị do chưa thanh toán", body);
        invoiceService.notify(protocol.getVet(), "[HorseTrack] Phác đồ " + protocol.getProtocolCode() + " tạm dừng do chưa thanh toán", body);
    }

    private void resume(TreatmentProtocol protocol) {
        boolean dispensed = !holdRepository.findByProtocolIdAndStatusIn(protocol.getProtocolId(), List.of("ISSUED")).isEmpty();
        String next = dispensed ? "IN_TREATMENT" : "VET_CONFIRMED";
        protocol.setState(next);
        protocol.setNote(appendNote(protocol.getNote(), "Tiếp tục " + LocalDate.now().format(DATE) + ": đã thanh toán đủ các giai đoạn tới hạn"));
        protocolRepository.save(protocol);
        auditTrail.record("PROTOCOL_PAYMENT_RESUME", "Treatment_Protocols", "state=ON_HOLD_PAYMENT", "state=" + next + " | " + protocol.getProtocolCode());
        invoiceService.notify(protocol.getVet(), "[HorseTrack] Phác đồ " + protocol.getProtocolCode() + " được tiếp tục",
                "<p>Chủ ngựa đã thanh toán giai đoạn tới hạn. Phác đồ <b>" + protocol.getProtocolCode() + "</b> chuyển lại trạng thái <b>" + next + "</b>.</p>");
    }

    // ==========================================
    // TRA CỨU
    // ==========================================

    /** Hóa đơn (chưa hủy) mới nhất của một giai đoạn */
    @Transactional(readOnly = true)
    public Optional<Invoice> findPhaseInvoice(Integer protocolId, Integer phaseNo) {
        return invoiceRepository.findByInvoiceCodeStartingWithAndPaymentStatusNotOrderByInvoiceIdDesc(baseCode(protocolId, phaseNo), Invoice.CANCELLED)
                .stream().findFirst();
    }

    private String nextCode(Integer protocolId, Integer phaseNo) {
        String base = baseCode(protocolId, phaseNo);
        String code = base;
        for (int i = 2; invoiceRepository.existsByInvoiceCode(code); i++) {
            code = base + "-R" + i;
        }
        return code;
    }

    static String baseCode(Integer protocolId, Integer phaseNo) {
        return String.format("GD-%06d-%02d", protocolId, phaseNo);
    }

    private static String appendNote(String note, String add) {
        String s = (note == null || note.isBlank()) ? add : note + "\n" + add;
        return s.length() > 1000 ? s.substring(s.length() - 1000) : s;
    }
}
