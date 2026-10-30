package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.finance.dto.InvoiceGenerateResultDTO;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceLineItem;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.notification.service.AppNotificationService;
import com.horsetrack.equinecare.modules.notification.service.EmailService;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.util.HtmlUtils;

import java.math.BigDecimal;
import java.sql.Date;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lập hóa đơn tháng cho từng con ngựa đang ở CLB, lấy chi phí thật đã ghi trong DB của kỳ đó:
 * - BOARDING: phí lưu chuồng Stalls.monthly_base_fee
 * - DAILY_CARE: vật tư dùng cho việc chăm sóc đã hoàn thành (Task_Material_Usage x Daily_Tasks)
 * Thuốc theo phác đồ KHÔNG nằm ở đây: được thu theo từng giai đoạn bằng hóa đơn riêng (ProtocolPhaseBilling, BR_16).
 * - COMPETITION: phí vận chuyển của các cuộc đua trong kỳ (Competition_Entries); lệ phí đăng ký đã thu riêng qua hóa đơn TD-
 * Ngựa không phát sinh chi phí (tổng 0 đồng) thì không lập hóa đơn. Mỗi ngựa chỉ có một hóa đơn (chưa hủy) mỗi kỳ.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceGenerator {

    private static final DateTimeFormatter MONTH_VN = DateTimeFormatter.ofPattern("MM/yyyy");

    private final InvoiceRepository invoiceRepository;
    private final HorseRepository horseRepository;
    private final EntityManager entityManager;
    private final BillingPolicy billingPolicy;
    private final EmailService emailService;
    private final AppNotificationService appNotifications;

    /** Ngày 1 hằng tháng (00:30) phát hành hóa đơn cho kỳ tháng trước (BR_12) */
    @Scheduled(cron = "${app.billing.generate-cron:0 30 0 1 * *}")
    @Transactional
    public void generateForPreviousMonth() {
        InvoiceGenerateResultDTO r = generate(YearMonth.now().minusMonths(1), LocalDate.now());
        log.info("Phát hành hóa đơn kỳ {}: tạo {}, bỏ qua {} đã có, {} không phát sinh phí",
                r.getBillingMonth(), r.getCreated(), r.getSkippedExisting(), r.getSkippedZero());
    }

    @Transactional
    public InvoiceGenerateResultDTO generate(YearMonth period, LocalDate issueDate) {
        if (period.isAfter(YearMonth.from(issueDate))) {
            throw new IllegalArgumentException("Không thể lập hóa đơn cho kỳ " + period + " chưa tới.");
        }
        String billingMonth = period.toString();
        int created = 0, existing = 0, zero = 0;
        List<String> codes = new ArrayList<>();

        for (Horse horse : horseRepository.findAll()) {
            // Chỉ ngựa đã được duyệt (đang có chuồng) và có chủ
            if (horse.getStall() == null || horse.getOwner() == null) {
                continue;
            }
            if (invoiceRepository.existsByHorse_HorseIdAndBillingMonthAndPaymentStatusNotAndInvoiceCodeStartingWith(horse.getHorseId(), billingMonth, Invoice.CANCELLED, "HD-")) {
                existing++;
                continue;
            }
            List<InvoiceLineItem> lines = collectLines(horse, period);
            BigDecimal subtotal = lines.stream().map(InvoiceLineItem::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (lines.isEmpty() || subtotal.signum() <= 0) {
                zero++;
                continue;
            }
            subtotal = BillingPolicy.vnd(subtotal);
            Invoice invoice = Invoice.builder()
                    .invoiceCode(nextCode(period, horse))
                    .horse(horse)
                    .owner(horse.getOwner())
                    .billingMonth(billingMonth)
                    .subtotalAmount(subtotal)
                    // Khấu trừ tiền thưởng theo hợp đồng (BR_11) chưa áp dụng
                    .prizeDeductionAmount(BigDecimal.ZERO)
                    .finalPayableAmount(subtotal)
                    .paymentStatus(Invoice.UNPAID)
                    .issuedAt(issueDate.equals(LocalDate.now()) ? LocalDateTime.now() : issueDate.atStartOfDay())
                    .dueDate(billingPolicy.dueDate(issueDate))
                    .build();
            lines.forEach(l -> l.setInvoice(invoice));
            invoice.getLines().addAll(lines);
            invoiceRepository.save(invoice);
            created++;
            codes.add(invoice.getInvoiceCode());
            notifyOwner(invoice, period);
        }
        invoiceRepository.flush();
        return InvoiceGenerateResultDTO.builder()
                .billingMonth(billingMonth).created(created).skippedExisting(existing).skippedZero(zero).invoiceCodes(codes)
                .build();
    }

    private List<InvoiceLineItem> collectLines(Horse horse, YearMonth period) {
        List<InvoiceLineItem> lines = new ArrayList<>();
        LocalDate from = period.atDay(1);
        LocalDate to = period.atEndOfMonth();
        LocalDateTime endOfPeriod = to.atTime(23, 59);

        // 1. Phí lưu chuồng
        BigDecimal fee = horse.getStall().getMonthlyBaseFee();
        if (fee != null && fee.signum() > 0) {
            lines.add(line("BOARDING", horse.getStall().getStallId(),
                    "Phí lưu chuồng " + horse.getStall().getStallCode() + " tháng " + period.format(MONTH_VN), fee, BigDecimal.ONE, endOfPeriod));
        }

        // 2. Vật tư chăm sóc hằng ngày đã dùng (việc DONE trong kỳ), gộp theo vật tư và đơn giá
        List<Object[]> care = native_("""
                SELECT i.item_id, i.item_name, u.unit_price_at_use, SUM(u.quantity_used), MAX(t.task_date)
                FROM Task_Material_Usage u
                JOIN Daily_Tasks t ON t.task_id = u.task_id
                JOIN Inventory_Items i ON i.item_id = u.item_id
                WHERE t.horse_id = ?1 AND t.status = 'DONE' AND t.task_date BETWEEN ?2 AND ?3
                GROUP BY i.item_id, i.item_name, u.unit_price_at_use""", horse.getHorseId(), from, to);
        for (Object[] r : care) {
            lines.add(line("DAILY_CARE", toInt(r[0]), "Vật tư chăm sóc: " + r[1], dec(r[2]), dec(r[3]), toDateTime(r[4], endOfPeriod)));
        }

        // 3. Thi đấu: phí vận chuyển của các cuộc đua diễn ra trong kỳ (lệ phí đăng ký đã thu qua hóa đơn TD- khi khám đạt)
        List<Object[]> races = native_("""
                SELECT ce.entry_id, ec.competition_name, ce.entry_fee_charged, ce.transport_fee, ec.start_date
                FROM Competition_Entries ce
                JOIN External_Competitions ec ON ec.competition_id = ce.competition_id
                WHERE ce.horse_id = ?1 AND ce.registration_status IN ('REGISTERED', 'RESULT_RECORDED')
                  AND ec.start_date BETWEEN ?2 AND ?3""", horse.getHorseId(), from, to);
        for (Object[] r : races) {
            LocalDateTime when = toDateTime(r[4], endOfPeriod);
            if (dec(r[3]).signum() > 0) {
                lines.add(line("COMPETITION", toInt(r[0]), "Phí vận chuyển giải " + r[1], dec(r[3]), BigDecimal.ONE, when));
            }
        }
        return lines;
    }

    // Mã hóa đơn: HD-yyyyMM-<horse_id>, thêm hậu tố nếu kỳ đó từng có hóa đơn bị hủy
    private String nextCode(YearMonth period, Horse horse) {
        String base = "HD-" + period.format(DateTimeFormatter.ofPattern("yyyyMM")) + "-" + String.format("%04d", horse.getHorseId());
        String code = base;
        for (int i = 2; invoiceRepository.existsByInvoiceCode(code); i++) {
            code = base + "-" + i;
        }
        return code;
    }

    private void notifyOwner(Invoice invoice, YearMonth period) {
        String title = "Hóa đơn " + invoice.getInvoiceCode() + " kỳ " + period.format(MONTH_VN);
        String plain = "Hóa đơn " + invoice.getInvoiceCode() + " cho ngựa " + invoice.getHorse().getHorseName()
                + ", số tiền " + money(invoice.getFinalPayableAmount())
                + ", hạn " + invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + ".";
        appNotifications.push(invoice.getOwner(), "INVOICE", title, plain, "/owner");
        if (!StringUtils.hasText(invoice.getOwner().getEmail())) {
            return;
        }
        try {
            emailService.sendAsync("INVOICE", invoice.getOwner().getEmail(),
                    "[HorseTrack] " + title,
                    "<p>Xin chào <b>" + HtmlUtils.htmlEscape(invoice.getOwner().getFullName()) + "</b>,</p>"
                            + "<p>CLB đã phát hành hóa đơn <b>" + invoice.getInvoiceCode() + "</b> cho ngựa <b>"
                            + HtmlUtils.htmlEscape(invoice.getHorse().getHorseName()) + "</b>, kỳ " + period.format(MONTH_VN) + ".</p>"
                            + "<p>Số tiền: <b>" + money(invoice.getFinalPayableAmount()) + "</b>. Hạn thanh toán: <b>"
                            + invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) + "</b>.</p>"
                            + "<p>Quá hạn sẽ tính phí trễ hạn " + billingPolicy.getLateFeeRatePerDay().movePointRight(2).stripTrailingZeros().toPlainString()
                            + "%/ngày trên số tiền gốc. Thanh toán qua VNPay trong mục Hóa đơn.</p>");
        } catch (Exception ex) {
            log.warn("Không gửi được email hóa đơn {}: {}", invoice.getInvoiceCode(), ex.getMessage());
        }
    }

    static String money(BigDecimal v) {
        return NumberFormat.getInstance(new Locale("vi", "VN")).format(v.setScale(0, java.math.RoundingMode.HALF_UP)) + " VND";
    }

    @SuppressWarnings("unchecked")
    private List<Object[]> native_(String sql, Object... params) {
        var q = entityManager.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            Object p = params[i];
            q.setParameter(i + 1, p instanceof LocalDate d ? Date.valueOf(d) : p);
        }
        return q.getResultList();
    }

    private static InvoiceLineItem line(String category, Integer ref, String desc, BigDecimal unitPrice, BigDecimal qty, LocalDateTime when) {
        return InvoiceLineItem.builder()
                .expenseCategory(category)
                .referenceId(ref)
                .itemDescription(desc.length() > 255 ? desc.substring(0, 255) : desc)
                .unitPrice(unitPrice)
                .quantity(qty)
                .serviceDate(when)
                .build();
    }

    private static Integer toInt(Object o) {
        return o == null ? null : ((Number) o).intValue();
    }

    private static BigDecimal dec(Object o) {
        if (o == null) {
            return BigDecimal.ZERO;
        }
        return o instanceof BigDecimal b ? b : new BigDecimal(o.toString());
    }

    private static LocalDateTime toDateTime(Object o, LocalDateTime fallback) {
        if (o instanceof java.sql.Date d) {
            return d.toLocalDate().atTime(12, 0);
        }
        if (o instanceof java.sql.Timestamp ts) {
            return ts.toLocalDateTime();
        }
        if (o instanceof LocalDate d) {
            return d.atTime(12, 0);
        }
        if (o instanceof LocalDateTime dt) {
            return dt;
        }
        return fallback;
    }
}
