package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceLineItem;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.ProtocolProgressNoteRepository;
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
 * - Sau khi bác sĩ xác nhận và kho đã cấp thuốc: nếu giai đoạn 1 chưa thanh toán, phác đồ ở ON_HOLD_PAYMENT
 *   (chờ thanh toán), chưa sang IN_TREATMENT. Chủ ngựa trả xong thì mới sang điều trị.
 * - Giai đoạn sau tới ngày mà chưa trả cũng chuyển ON_HOLD_PAYMENT (BR_16).
 * - Ngoại lệ BR_17: ca cấp cứu (Vet_Cases.severity = CRITICAL) hoặc ngựa ở trạng thái EMERGENCY
 *   sang điều trị và được cấp thuốc, vật tư y tế mà không chờ thanh toán.
 * - Thanh toán xong (VNPay đã xác minh) và đã cấp thuốc: IN_TREATMENT. Chưa cấp thuốc thì vẫn VET_CONFIRMED.
 * - Thuốc bổ sung khi đang điều trị: hóa đơn riêng mã GD-&lt;protocol_id&gt;-&lt;phase_no&gt;-BS&lt;n&gt;; chưa thanh toán thì kho chưa cấp
 *   phần bổ sung (trừ ca cấp cứu) nhưng phác đồ không bị tạm dừng.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProtocolPhaseBilling {

    public static final String ON_HOLD = "ON_HOLD_PAYMENT";
    /** Trạng thái phác đồ còn hiệu lực có thể bị tạm dừng vì chưa thanh toán */
    public static final List<String> PAYABLE_STATES = List.of("VET_CONFIRMED", "READY_FOR_DISPENSING", "IN_TREATMENT");
    private static final Pattern CODE = Pattern.compile("^GD-(\\d{6})-(\\d{2})");
    /** Đoạn mã của hóa đơn thuốc bổ sung */
    public static final String SUPPLEMENT_MARK = "-BS";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository invoiceRepository;
    private final TreatmentProtocolRepository protocolRepository;
    private final VetCaseRepository vetCaseRepository;
    private final StockHoldRepository holdRepository;
    private final ProtocolProgressNoteRepository noteRepository;
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
            for (Invoice inv : phaseInvoices(protocol.getProtocolId(), phase.getPhaseNo(), true)) {
                if (cancelIfUnpaid(inv, reason)) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Thuốc bổ sung khi phác đồ đang điều trị: lập hóa đơn riêng cho phần thêm (số lượng thêm x đơn giá).
     * Trả về hóa đơn, hoặc rỗng nếu thuốc không có giá.
     */
    @Transactional
    public Optional<Invoice> issueSupplementInvoice(TreatmentProtocol protocol, ProtocolPhase phase, ProtocolItem line, BigDecimal addedQty, String reason) {
        if (line.getUnitPrice() == null || line.getUnitPrice().signum() <= 0 || addedQty.signum() <= 0) {
            return Optional.empty();
        }
        LocalDate today = LocalDate.now();
        String name = line.getItem() != null ? line.getItem().getItemName() : "Thuốc";
        String desc = "Thuốc bổ sung - phác đồ " + protocol.getProtocolCode() + " - GĐ " + phase.getPhaseNo() + ": " + name;
        InvoiceLineItem l = InvoiceLineItem.builder()
                .expenseCategory("MEDICATION")
                .referenceId(line.getProtocolItemId())
                .itemDescription(desc.length() > 255 ? desc.substring(0, 255) : desc)
                .unitPrice(line.getUnitPrice())
                .quantity(addedQty)
                .serviceDate(LocalDateTime.now())
                .build();
        BigDecimal total = BillingPolicy.vnd(l.getAmount());
        String base = baseCode(protocol.getProtocolId(), phase.getPhaseNo()) + SUPPLEMENT_MARK;
        int no = 1;
        while (invoiceRepository.existsByInvoiceCode(base + no)) {
            no++;
        }
        Invoice invoice = Invoice.builder()
                .invoiceCode(base + no)
                .horse(protocol.getHorse())
                .owner(protocol.getHorse().getOwner())
                .billingMonth(YearMonth.from(today).toString())
                .subtotalAmount(total)
                .prizeDeductionAmount(BigDecimal.ZERO)
                .finalPayableAmount(total)
                .paymentStatus(Invoice.UNPAID)
                .issuedAt(LocalDateTime.now())
                .dueDate(phase.getStartDate().isAfter(today) ? phase.getStartDate() : today)
                .build();
        l.setInvoice(invoice);
        invoice.getLines().add(l);
        invoiceRepository.saveAndFlush(invoice);
        auditTrail.record("ISSUE_SUPPLEMENT_INVOICE", "Invoices", null, invoice.getInvoiceCode() + " | phác đồ " + protocol.getProtocolCode()
                + " | GĐ " + phase.getPhaseNo() + " | " + name + " x " + addedQty.stripTrailingZeros().toPlainString() + " | " + InvoiceGenerator.money(total));
        invoiceService.notify(protocol.getHorse().getOwner(), "[HorseTrack] Hóa đơn thuốc bổ sung cho ngựa " + protocol.getHorse().getHorseName(),
                "<p>Bác sĩ bổ sung <b>" + HtmlUtils.htmlEscape(name) + "</b> (số lượng " + addedQty.stripTrailingZeros().toPlainString()
                        + ") vào phác đồ <b>" + protocol.getProtocolCode() + "</b> của ngựa <b>" + HtmlUtils.htmlEscape(protocol.getHorse().getHorseName())
                        + "</b>.</p><p>Lý do: " + HtmlUtils.htmlEscape(reason) + "</p><p>Hóa đơn <b>" + invoice.getInvoiceCode() + "</b>: "
                        + InvoiceGenerator.money(total) + ". Vui lòng thanh toán qua VNPay để kho cấp thuốc bổ sung.</p>");
        return Optional.of(invoice);
    }

    /** Hóa đơn thuốc bổ sung còn mở của phác đồ */
    @Transactional(readOnly = true)
    public List<Invoice> openSupplementInvoices(Integer protocolId) {
        return invoiceRepository.findByInvoiceCodeStartingWithAndPaymentStatusNotOrderByInvoiceIdDesc(String.format("GD-%06d-", protocolId), Invoice.CANCELLED)
                .stream().filter(i -> i.getInvoiceCode().contains(SUPPLEMENT_MARK) && i.isOpen()).toList();
    }

    /** Hủy hóa đơn thuốc bổ sung chưa thanh toán của một vật tư (thuốc bổ sung chưa cấp mà phác đồ kết thúc). */
    @Transactional
    public boolean cancelSupplementInvoice(Integer protocolId, Integer itemId, java.util.function.Function<Integer, Integer> itemOfProtocolLine, String reason) {
        for (Invoice inv : openSupplementInvoices(protocolId)) {
            boolean forItem = inv.getLines().stream().anyMatch(l -> l.getReferenceId() != null && itemId.equals(itemOfProtocolLine.apply(l.getReferenceId())));
            if (forItem && cancelIfUnpaid(inv, reason)) {
                return true;
            }
        }
        return false;
    }

    private boolean cancelIfUnpaid(Invoice inv, String reason) {
        if (!inv.isOpen() || inv.getTransactions().stream()
                .anyMatch(t -> "VERIFIED".equals(t.getPaymentStatus()) || "PENDING_VERIFICATION".equals(t.getPaymentStatus()))) {
            return false;
        }
        inv.setPaymentStatus(Invoice.CANCELLED);
        invoiceRepository.save(inv);
        auditTrail.record("CANCEL_INVOICE", "Invoices", "payment_status=OPEN", "payment_status=CANCELLED | " + inv.getInvoiceCode() + " | " + reason);
        return true;
    }

    // ==========================================
    // TẠM DỪNG / TIẾP TỤC (BR_16, BR_17)
    // ==========================================

    /** Ca cấp cứu (BR_17): ngựa đang EMERGENCY, hoặc phác đồ gắn với ca khám mức CRITICAL. */
    public boolean isEmergency(TreatmentProtocol protocol) {
        if (protocol.getHorse() != null && isEmergencyStatus(protocol.getHorse().getHealthStatus())) {
            return true;
        }
        if (protocol.getCaseId() == null) {
            return false;
        }
        return vetCaseRepository.findById(protocol.getCaseId())
                .map(c -> "CRITICAL".equalsIgnoreCase(c.getSeverity()))
                .orElse(false);
    }

    public static boolean isEmergencyStatus(String status) {
        return status != null && "EMERGENCY".equalsIgnoreCase(status.trim());
    }

    /** Ngựa vừa chuyển cấp cứu: phác đồ đang chờ tiền được tiếp tục để kho cấp phát ngay. */
    @Transactional
    public void releaseForEmergencyHorse(Integer horseId) {
        for (TreatmentProtocol protocol : protocolRepository.search(ON_HOLD, horseId)) {
            evaluate(protocol, LocalDate.now());
        }
    }

    /** Bác sĩ bắt đầu giai đoạn sớm: hạn hóa đơn chưa trả kéo về ngày bắt đầu mới. */
    @Transactional
    public void bringPhaseDueForward(TreatmentProtocol protocol, int phaseNo, LocalDate date) {
        findPhaseInvoice(protocol.getProtocolId(), phaseNo).filter(Invoice::isOpen).ifPresent(inv -> {
            if (inv.getDueDate() != null && inv.getDueDate().isAfter(date)) {
                inv.setDueDate(date);
                invoiceRepository.save(inv);
            }
        });
    }

    /** Giai đoạn đầu tiên đã tới ngày bắt đầu nhưng hóa đơn chưa thanh toán (rỗng nếu không có hoặc là ca cấp cứu) */
    @Transactional(readOnly = true)
    public Optional<ProtocolPhase> blockingPhase(TreatmentProtocol protocol, LocalDate today) {
        if (isEmergency(protocol)) {
            return Optional.empty();
        }
        return protocol.getPhases().stream()
                .filter(ph -> !ph.getStartDate().isAfter(today))
                .filter(ph -> phaseInvoices(protocol.getProtocolId(), ph.getPhaseNo(), false).stream().anyMatch(Invoice::isOpen))
                .min(Comparator.comparing(ProtocolPhase::getPhaseNo));
    }

    /** Giai đoạn 1 còn hóa đơn mở. Ca cấp cứu không bị chặn. */
    @Transactional(readOnly = true)
    public boolean phase1Unpaid(TreatmentProtocol protocol) {
        if (isEmergency(protocol)) {
            return false;
        }
        return protocol.getPhases().stream()
                .min(Comparator.comparing(ProtocolPhase::getPhaseNo))
                .flatMap(ph -> findPhaseInvoice(protocol.getProtocolId(), ph.getPhaseNo()))
                .filter(Invoice::isOpen)
                .isPresent();
    }

    /** Kho đã cấp thuốc nhưng giai đoạn 1 chưa trả: chờ thanh toán, chưa điều trị. */
    @Transactional
    public void holdUntilPhase1Paid(TreatmentProtocol protocol) {
        ProtocolPhase phase = protocol.getPhases().stream()
                .min(Comparator.comparing(ProtocolPhase::getPhaseNo))
                .orElseThrow(() -> new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " không có giai đoạn để chờ thanh toán."));
        hold(protocol, phase);
    }

    /** Đưa phác đồ về đúng trạng thái theo tình hình thanh toán. Trả về true nếu trạng thái thay đổi. */
    @Transactional
    public boolean evaluate(TreatmentProtocol protocol, LocalDate today) {
        boolean dispensed = !dispensedPhases(protocol).isEmpty();
        Optional<ProtocolPhase> blocking = blockingPhase(protocol, today);
        boolean waitPay = dispensed && (phase1Unpaid(protocol) || blocking.isPresent());
        if (PAYABLE_STATES.contains(protocol.getState()) && waitPay) {
            hold(protocol, blocking.orElseGet(() -> protocol.getPhases().stream().min(Comparator.comparing(ProtocolPhase::getPhaseNo)).orElseThrow()));
            return true;
        }
        if (ON_HOLD.equals(protocol.getState()) && !waitPay) {
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
        Invoice inv = phaseInvoices(protocol.getProtocolId(), phase.getPhaseNo(), false).stream().filter(Invoice::isOpen).findFirst().orElseThrow();
        protocol.setState(ON_HOLD);
        protocol.setNote(appendNote(protocol.getNote(), "Tạm dừng " + LocalDate.now().format(DATE) + ": chưa thanh toán giai đoạn "
                + phase.getPhaseNo() + " (" + inv.getInvoiceCode() + ") - BR_16"));
        protocolRepository.save(protocol);
        auditTrail.record("PROTOCOL_PAYMENT_HOLD", "Treatment_Protocols", "state=" + old,
                "state=ON_HOLD_PAYMENT | " + protocol.getProtocolCode() + " | giai đoạn " + phase.getPhaseNo() + " | hóa đơn " + inv.getInvoiceCode());
        String body = "<p>Phác đồ <b>" + protocol.getProtocolCode() + "</b> của ngựa <b>" + HtmlUtils.htmlEscape(protocol.getHorse().getHorseName())
                + "</b> đang <b>chờ thanh toán</b> giai đoạn " + phase.getPhaseNo() + " (bắt đầu " + phase.getStartDate().format(DATE)
                + ") — hóa đơn <b>" + inv.getInvoiceCode() + "</b>.</p>"
                + "<p>Phác đồ chỉ chuyển sang <b>điều trị</b> sau khi thanh toán qua VNPay được xác minh.</p>";
        invoiceService.notify(protocol.getHorse().getOwner(), "[HorseTrack] Tạm dừng điều trị do chưa thanh toán", body);
        invoiceService.notify(protocol.getVet(), "[HorseTrack] Phác đồ " + protocol.getProtocolCode() + " tạm dừng do chưa thanh toán", body);
    }

    private void resume(TreatmentProtocol protocol) {
        boolean dispensed = !dispensedPhases(protocol).isEmpty();
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

    /** Chủ ngựa trả giai đoạn theo số thứ tự tăng dần. Hóa đơn thuốc bổ sung không chặn giai đoạn. */
    @Transactional(readOnly = true)
    public void assertPhaseOrder(Invoice invoice) {
        if (invoice.getInvoiceCode() == null || invoice.getInvoiceCode().contains(SUPPLEMENT_MARK)) {
            return;
        }
        Matcher m = CODE.matcher(invoice.getInvoiceCode());
        if (!m.find()) {
            return;
        }
        int protocolId = Integer.parseInt(m.group(1));
        int phaseNo = Integer.parseInt(m.group(2));
        for (int earlier = 1; earlier < phaseNo; earlier++) {
            Optional<Invoice> open = findPhaseInvoice(protocolId, earlier).filter(Invoice::isOpen);
            if (open.isPresent()) {
                throw new IllegalArgumentException("Thanh toán theo thứ tự giai đoạn. Hãy trả hóa đơn " + open.get().getInvoiceCode()
                        + " (giai đoạn " + earlier + ") trước giai đoạn " + phaseNo + ".");
            }
        }
    }

    /** Phác đồ bị hủy sau khi chủ ngựa đã trả: ghi hoàn tiền và gửi email lý do. */
    @Transactional
    public int refundPaidPhaseInvoices(TreatmentProtocol protocol, String reason) {
        String prefix = String.format("GD-%06d-", protocol.getProtocolId());
        int n = 0;
        StringBuilder rows = new StringBuilder();
        java.math.BigDecimal total = java.math.BigDecimal.ZERO;
        for (Invoice inv : invoiceRepository.findByInvoiceCodeStartingWithAndPaymentStatusNotOrderByInvoiceIdDesc(prefix, Invoice.CANCELLED)) {
            if (!Invoice.PAID.equals(inv.getPaymentStatus())) {
                continue;
            }
            inv.setPaymentStatus(Invoice.REFUNDED);
            invoiceRepository.save(inv);
            total = total.add(inv.getFinalPayableAmount() == null ? java.math.BigDecimal.ZERO : inv.getFinalPayableAmount());
            rows.append("<li>").append(inv.getInvoiceCode()).append(" — ").append(InvoiceGenerator.money(inv.getFinalPayableAmount())).append("</li>");
            auditTrail.record("REFUND_INVOICE", "Invoices", "payment_status=PAID",
                    "payment_status=REFUNDED | " + inv.getInvoiceCode() + " | " + reason);
            n++;
        }
        if (n > 0 && protocol.getHorse() != null && protocol.getHorse().getOwner() != null) {
            invoiceService.notify(protocol.getHorse().getOwner(),
                    "[HorseTrack] Hoàn tiền phác đồ " + protocol.getProtocolCode(),
                    "<p>Bác sĩ đã hủy phác đồ <b>" + protocol.getProtocolCode() + "</b> của ngựa <b>"
                            + HtmlUtils.htmlEscape(protocol.getHorse().getHorseName()) + "</b> sau khi bạn đã thanh toán.</p>"
                            + "<p>Lý do: " + HtmlUtils.htmlEscape(reason) + "</p>"
                            + "<p>Số tiền được hoàn:</p><ul>" + rows + "</ul><p>Tổng <b>" + InvoiceGenerator.money(total) + "</b>.</p>");
        }
        return n;
    }

    /** Giai đoạn đã được kho cấp thuốc. Phác đồ cũ cấp một lần (mọi giữ hàng đã ISSUED, không ghi từng giai đoạn) thì tính là đã cấp hết. */
    @Transactional(readOnly = true)
    public Set<Integer> dispensedPhases(TreatmentProtocol protocol) {
        Set<Integer> marked = readDispensed(protocol.getNote());
        noteRepository.findByProtocol_ProtocolIdOrderByWrittenAtAsc(protocol.getProtocolId())
                .forEach(n -> marked.addAll(readDispensed(n.getContent())));
        if (!marked.isEmpty()) {
            return marked;
        }
        if (!holdRepository.findByProtocolIdAndStatusIn(protocol.getProtocolId(), List.of("ISSUED")).isEmpty()) {
            return protocol.getPhases().stream().map(ProtocolPhase::getPhaseNo).collect(java.util.stream.Collectors.toSet());
        }
        return Set.of();
    }

    /** Hoàn thành giai đoạn chỉ khi kho đã cấp thuốc giai đoạn đó và chủ ngựa đã trả hóa đơn giai đoạn (ca cấp cứu không chờ tiền). */
    public void assertCanFinishPhase(TreatmentProtocol protocol, int phaseNo) {
        ProtocolPhase phase = protocol.getPhases().stream()
                .filter(ph -> ph.getPhaseNo() != null && ph.getPhaseNo() == phaseNo)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Phác đồ " + protocol.getProtocolCode() + " không có giai đoạn " + phaseNo + "."));
        if (!dispensedPhases(protocol).contains(phaseNo)) {
            throw new IllegalArgumentException("Chưa cấp thuốc giai đoạn " + phaseNo + " (" + phase.getPhaseName()
                    + "). Kho phải cấp thuốc giai đoạn này trước khi hoàn thành.");
        }
        if (isEmergency(protocol)) {
            return;
        }
        findPhaseInvoice(protocol.getProtocolId(), phaseNo).filter(Invoice::isOpen).ifPresent(inv -> {
            throw new IllegalArgumentException("Chủ ngựa chưa thanh toán hóa đơn " + inv.getInvoiceCode() + " của giai đoạn " + phaseNo
                    + " (" + phase.getPhaseName() + "). Chưa thể kết thúc giai đoạn.");
        });
        String prefix = baseCode(protocol.getProtocolId(), phaseNo);
        openSupplementInvoices(protocol.getProtocolId()).stream()
                .filter(inv -> inv.getInvoiceCode() != null && inv.getInvoiceCode().startsWith(prefix))
                .findFirst()
                .ifPresent(inv -> {
                    throw new IllegalArgumentException("Chủ ngựa chưa thanh toán hóa đơn thuốc bổ sung " + inv.getInvoiceCode()
                            + " của giai đoạn " + phaseNo + ". Chưa thể kết thúc giai đoạn.");
                });
    }

    public static Set<Integer> readDispensed(String text) {
        Set<Integer> nos = new HashSet<>();
        if (text == null) {
            return nos;
        }
        Matcher matcher = Pattern.compile("Đã cấp thuốc giai đoạn (\\d+)").matcher(text);
        while (matcher.find()) {
            nos.add(Integer.parseInt(matcher.group(1)));
        }
        return nos;
    }

    /** Hóa đơn (chưa hủy) mới nhất của một giai đoạn (không tính hóa đơn thuốc bổ sung) */
    @Transactional(readOnly = true)
    public Optional<Invoice> findPhaseInvoice(Integer protocolId, Integer phaseNo) {
        return phaseInvoices(protocolId, phaseNo, false).stream().findFirst();
    }

    // Hóa đơn chưa hủy của giai đoạn, mới nhất trước; withSupplements = kèm hóa đơn thuốc bổ sung
    private List<Invoice> phaseInvoices(Integer protocolId, Integer phaseNo, boolean withSupplements) {
        return invoiceRepository.findByInvoiceCodeStartingWithAndPaymentStatusNotOrderByInvoiceIdDesc(baseCode(protocolId, phaseNo), Invoice.CANCELLED)
                .stream().filter(i -> withSupplements || !i.getInvoiceCode().contains(SUPPLEMENT_MARK)).toList();
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
