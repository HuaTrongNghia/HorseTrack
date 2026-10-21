package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.auth.entity.HorseOwnerProfile;
import com.horsetrack.equinecare.modules.auth.repository.HorseOwnerProfileRepository;
import com.horsetrack.equinecare.modules.finance.dto.FinanceReportDTO;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.entity.InvoiceLineItem;
import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import com.horsetrack.equinecare.modules.finance.repository.PaymentTransactionRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Báo cáo tài chính cho Club Manager (SRS màn hình 26), tính trên dữ liệu thật trong DB:
 * phát hành và thu theo tháng, tỷ lệ thu, tuổi nợ (BR_12, BR_13, BR_15), doanh thu theo dịch vụ, công nợ theo chủ ngựa,
 * chi phí nhập hàng. Tiền đã thu chỉ gồm giao dịch VNPay đã được server xác minh (VERIFIED).
 */
@Service
@RequiredArgsConstructor
public class FinanceReportService {

    private static final int MAX_MONTHS = 36;

    private final InvoiceRepository invoiceRepository;
    private final PaymentTransactionRepository transactionRepository;
    private final HorseOwnerProfileRepository ownerProfileRepository;
    private final BillingPolicy billingPolicy;
    private final EntityManager entityManager;

    @Transactional(readOnly = true)
    public FinanceReportDTO buildReport(String fromMonth, String toMonth) {
        YearMonth to = parse(toMonth, YearMonth.now(), "toMonth");
        YearMonth from = parse(fromMonth, YearMonth.of(to.getYear(), 1), "fromMonth");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("fromMonth không được sau toMonth.");
        }
        if (from.plusMonths(MAX_MONTHS - 1L).isBefore(to)) {
            throw new IllegalArgumentException("Khoảng báo cáo tối đa " + MAX_MONTHS + " tháng.");
        }
        LocalDate today = LocalDate.now();
        LocalDateTime start = from.atDay(1).atStartOfDay();
        LocalDateTime end = to.atEndOfMonth().plusDays(1).atStartOfDay().minusNanos(1);

        List<Invoice> billed = invoiceRepository.findBilledBetween(from.toString(), to.toString());
        List<PaymentTransaction> verified = transactionRepository.findByPaymentStatusAndVerifiedAtBetween(PaymentTransaction.VERIFIED, start, end);
        Map<String, BigDecimal> purchaseByMonth = purchaseCostByMonth(start, end);

        // ---- Theo tháng ----
        Map<String, List<Invoice>> billedByMonth = billed.stream().collect(Collectors.groupingBy(Invoice::getBillingMonth));
        Map<String, BigDecimal> collectedByMonth = new HashMap<>();
        for (PaymentTransaction t : verified) {
            collectedByMonth.merge(YearMonth.from(t.getVerifiedAt()).toString(), t.getAmountPaid(), BigDecimal::add);
        }
        List<FinanceReportDTO.MonthRow> months = new ArrayList<>();
        for (YearMonth m = from; !m.isAfter(to); m = m.plusMonths(1)) {
            List<Invoice> inv = billedByMonth.getOrDefault(m.toString(), List.of());
            BigDecimal billedAmt = sum(inv, Invoice::getFinalPayableAmount);
            BigDecimal paidPrincipal = sum(inv.stream().filter(i -> Invoice.PAID.equals(i.getPaymentStatus())).toList(), Invoice::getFinalPayableAmount);
            months.add(FinanceReportDTO.MonthRow.builder()
                    .month(m.toString())
                    .billed(billedAmt)
                    .collected(collectedByMonth.getOrDefault(m.toString(), BigDecimal.ZERO))
                    .collectionRate(rate(paidPrincipal, billedAmt))
                    .purchaseCost(purchaseByMonth.getOrDefault(m.toString(), BigDecimal.ZERO))
                    .invoiceCount(inv.size())
                    .build());
        }

        // ---- Theo dịch vụ ----
        Map<String, BigDecimal[]> byCat = new TreeMap<>();
        for (Invoice i : billed) {
            boolean paid = Invoice.PAID.equals(i.getPaymentStatus());
            for (InvoiceLineItem l : i.getLines()) {
                BigDecimal[] acc = byCat.computeIfAbsent(l.getExpenseCategory(), k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
                acc[0] = acc[0].add(l.getAmount());
                if (paid) {
                    acc[1] = acc[1].add(l.getAmount());
                }
            }
        }
        List<FinanceReportDTO.CategoryRow> categories = byCat.entrySet().stream()
                .map(e -> FinanceReportDTO.CategoryRow.builder()
                        .category(e.getKey())
                        .label(InvoiceService.CATEGORY_LABELS.getOrDefault(e.getKey(), e.getKey()))
                        .billed(BillingPolicy.vnd(e.getValue()[0]))
                        .collected(BillingPolicy.vnd(e.getValue()[1]))
                        .build())
                .sorted(Comparator.comparing(FinanceReportDTO.CategoryRow::getBilled).reversed())
                .collect(Collectors.toList());

        // ---- Công nợ hiện tại (mọi hóa đơn đang mở, tính tới hôm nay) ----
        List<Invoice> open = invoiceRepository.findByPaymentStatusInOrderByDueDateAsc(Invoice.OPEN_STATUSES);
        Map<String, FinanceReportDTO.AgeingBucket> ageing = new LinkedHashMap<>();
        ageing.put("NOT_DUE", bucket("NOT_DUE", "Chưa đến hạn"));
        ageing.put("OVERDUE_1_15", bucket("OVERDUE_1_15", "Quá hạn 1–15 ngày (tính phí trễ hạn, BR_12)"));
        ageing.put("OVERDUE_16_60", bucket("OVERDUE_16_60", "Quá hạn 16–60 ngày (đủ điều kiện đình chỉ, BR_13)"));
        ageing.put("OVERDUE_OVER_60", bucket("OVERDUE_OVER_60", "Quá hạn trên 60 ngày (chấm dứt hợp đồng, BR_15)"));
        BigDecimal outstandingPrincipal = BigDecimal.ZERO;
        BigDecimal outstandingLate = BigDecimal.ZERO;
        Map<Integer, FinanceReportDTO.OwnerDebtRow> owners = new LinkedHashMap<>();
        Map<Integer, String> serviceStatus = ownerProfileRepository.findAllById(open.stream().map(i -> i.getOwner().getUserId()).distinct().toList())
                .stream().collect(Collectors.toMap(HorseOwnerProfile::getUserId, HorseOwnerProfile::getServiceStatus));
        for (Invoice i : open) {
            long overdue = billingPolicy.daysOverdue(i, today);
            BigDecimal late = billingPolicy.lateFee(i, today);
            outstandingPrincipal = outstandingPrincipal.add(i.getFinalPayableAmount());
            outstandingLate = outstandingLate.add(late);

            String key = overdue == 0 ? "NOT_DUE" : overdue <= 15 ? "OVERDUE_1_15" : overdue <= billingPolicy.getTerminationDays() ? "OVERDUE_16_60" : "OVERDUE_OVER_60";
            FinanceReportDTO.AgeingBucket b = ageing.get(key);
            b.setInvoiceCount(b.getInvoiceCount() + 1);
            b.setPrincipal(b.getPrincipal().add(i.getFinalPayableAmount()));
            b.setLateFees(b.getLateFees().add(late));

            Integer ownerId = i.getOwner().getUserId();
            FinanceReportDTO.OwnerDebtRow row = owners.computeIfAbsent(ownerId, id -> FinanceReportDTO.OwnerDebtRow.builder()
                    .ownerId(id).ownerName(i.getOwner().getFullName()).email(i.getOwner().getEmail())
                    .serviceStatus(serviceStatus.get(id))
                    .principal(BigDecimal.ZERO).lateFees(BigDecimal.ZERO).totalDue(BigDecimal.ZERO).build());
            row.setOpenInvoices(row.getOpenInvoices() + 1);
            row.setPrincipal(row.getPrincipal().add(i.getFinalPayableAmount()));
            row.setLateFees(row.getLateFees().add(late));
            row.setTotalDue(row.getPrincipal().add(row.getLateFees()));
            row.setMaxDaysOverdue(Math.max(row.getMaxDaysOverdue(), overdue));
            row.setSuspensionEligible(row.isSuspensionEligible() || billingPolicy.suspensionEligible(i, today));
            row.setTerminationDue(row.isTerminationDue() || billingPolicy.terminationDue(i, today));
        }
        List<FinanceReportDTO.OwnerDebtRow> debtByOwner = owners.values().stream()
                .sorted(Comparator.comparing(FinanceReportDTO.OwnerDebtRow::getTotalDue).reversed())
                .collect(Collectors.toList());

        // ---- Tổng ----
        BigDecimal totalBilled = sum(billed, Invoice::getFinalPayableAmount);
        BigDecimal totalCollected = verified.stream().map(PaymentTransaction::getAmountPaid).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal paidPrincipalInRange = sum(billed.stream().filter(i -> Invoice.PAID.equals(i.getPaymentStatus())).toList(), Invoice::getFinalPayableAmount);
        BigDecimal lateCollected = verified.stream()
                .map(t -> t.getAmountPaid().subtract(t.getInvoice().getFinalPayableAmount()).max(BigDecimal.ZERO))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal purchase = purchaseByMonth.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        FinanceReportDTO.Totals totals = FinanceReportDTO.Totals.builder()
                .billed(totalBilled)
                .collected(totalCollected)
                .collectionRate(rate(paidPrincipalInRange, totalBilled))
                .lateFeesCollected(lateCollected)
                .outstandingPrincipal(outstandingPrincipal)
                .outstandingLateFees(outstandingLate)
                .purchaseCost(purchase)
                .netCashFlow(totalCollected.subtract(purchase))
                .invoicesIssued(billed.size())
                .invoicesPaid(billed.stream().filter(i -> Invoice.PAID.equals(i.getPaymentStatus())).count())
                .invoicesOverdue(billed.stream().filter(i -> Invoice.OVERDUE.equals(i.getPaymentStatus())).count())
                .build();

        return FinanceReportDTO.builder()
                .fromMonth(from.toString()).toMonth(to.toString()).generatedAt(LocalDateTime.now())
                .totals(totals).months(months).revenueByService(categories)
                .debtAgeing(new ArrayList<>(ageing.values())).debtByOwner(debtByOwner)
                .build();
    }

    // Chi phí nhập hàng: số lượng đã nhận x đơn giá của các đơn mua đã nhận hàng, theo tháng nhận
    @SuppressWarnings("unchecked")
    private Map<String, BigDecimal> purchaseCostByMonth(LocalDateTime start, LocalDateTime end) {
        List<Object[]> rows = entityManager.createNativeQuery("""
                SELECT CONVERT(char(7), po.received_at, 126) AS ym, SUM(pol.quantity_received * pol.unit_price)
                FROM Purchase_Orders po JOIN Purchase_Order_Lines pol ON pol.order_id = po.order_id
                WHERE po.status IN ('RECEIVED', 'PARTIALLY_RECEIVED') AND po.received_at BETWEEN ?1 AND ?2
                GROUP BY CONVERT(char(7), po.received_at, 126)""")
                .setParameter(1, java.sql.Timestamp.valueOf(start))
                .setParameter(2, java.sql.Timestamp.valueOf(end))
                .getResultList();
        Map<String, BigDecimal> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(r[0].toString().trim(), BillingPolicy.vnd(r[1] instanceof BigDecimal b ? b : new BigDecimal(String.valueOf(r[1]))));
        }
        return m;
    }

    private static FinanceReportDTO.AgeingBucket bucket(String code, String label) {
        return FinanceReportDTO.AgeingBucket.builder().bucket(code).label(label).principal(BigDecimal.ZERO).lateFees(BigDecimal.ZERO).build();
    }

    private static BigDecimal sum(List<Invoice> list, java.util.function.Function<Invoice, BigDecimal> f) {
        return list.stream().map(f).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Tỷ lệ % làm tròn 2 chữ số; 0 khi không có phát hành */
    private static BigDecimal rate(BigDecimal part, BigDecimal total) {
        if (total == null || total.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
    }

    private static YearMonth parse(String s, YearMonth fallback, String field) {
        if (!StringUtils.hasText(s)) {
            return fallback;
        }
        try {
            return YearMonth.parse(s.trim());
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(field + " phải có dạng yyyy-MM (ví dụ 2026-10).");
        }
    }
}
