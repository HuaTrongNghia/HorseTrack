package com.horsetrack.equinecare.modules.finance.service;

import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import com.horsetrack.equinecare.modules.finance.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.util.HtmlUtils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Tác vụ hằng ngày cho hóa đơn (SRS - Overdue Invoice Scheduler):
 * - Hóa đơn UNPAID quá hạn → OVERDUE, báo chủ ngựa phí trễ hạn bắt đầu tính (BR_12).
 * - Ngày thứ 20 kể từ phát hành: cảnh báo chủ ngựa sắp bị đình chỉ.
 * - Ngày thứ 25: báo Club Manager danh sách hóa đơn đủ điều kiện đình chỉ dịch vụ (BR_13).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OverdueInvoiceScheduler {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final InvoiceRepository invoiceRepository;
    private final UserRepository userRepository;
    private final BillingPolicy billingPolicy;
    private final InvoiceService invoiceService;

    @Scheduled(cron = "${app.billing.overdue-cron:0 15 0 * * *}")
    @Transactional
    public void runDaily() {
        int n = run(LocalDate.now());
        if (n > 0) {
            log.info("Overdue scheduler: {} hóa đơn chuyển OVERDUE", n);
        }
    }

    /** Trả về số hóa đơn vừa chuyển sang OVERDUE */
    @Transactional
    public int run(LocalDate today) {
        List<Invoice> newlyOverdue = invoiceRepository.findByPaymentStatusAndDueDateBefore(Invoice.UNPAID, today);
        for (Invoice i : newlyOverdue) {
            i.setPaymentStatus(Invoice.OVERDUE);
            invoiceRepository.save(i);
            invoiceService.notify(i.getOwner(), "[HorseTrack] Hóa đơn " + i.getInvoiceCode() + " đã quá hạn",
                    "<p>Hóa đơn <b>" + i.getInvoiceCode() + "</b> (ngựa " + HtmlUtils.htmlEscape(i.getHorse().getHorseName())
                            + ") đã quá hạn thanh toán ngày " + i.getDueDate().format(DATE) + ".</p>"
                            + "<p>Phí trễ hạn " + billingPolicy.getLateFeeRatePerDay().movePointRight(2).stripTrailingZeros().toPlainString()
                            + "% tiền gốc mỗi ngày đang được tính (BR_12). Vui lòng thanh toán qua VNPay sớm nhất.</p>");
        }

        List<Invoice> open = invoiceRepository.findByPaymentStatusInOrderByDueDateAsc(Invoice.OPEN_STATUSES);
        for (Invoice i : open) {
            if (billingPolicy.nominalDaysSinceIssue(i, today) == billingPolicy.getWarningDays()) {
                invoiceService.notify(i.getOwner(), "[HorseTrack] Cảnh báo: sắp đình chỉ dịch vụ",
                        "<p>Hóa đơn <b>" + i.getInvoiceCode() + "</b> đã " + billingPolicy.getWarningDays() + " ngày chưa thanh toán. "
                                + "Đến ngày thứ " + billingPolicy.getSuspensionDays() + " kể từ ngày phát hành, dịch vụ có thể bị đình chỉ (BR_13) "
                                + "và phải trả thêm phí kích hoạt lại " + InvoiceGenerator.money(billingPolicy.getReactivationFee()) + " (BR_14).</p>");
            }
        }

        List<Invoice> reachedSuspension = open.stream()
                .filter(i -> billingPolicy.nominalDaysSinceIssue(i, today) == billingPolicy.getSuspensionDays())
                .collect(Collectors.toList());
        if (!reachedSuspension.isEmpty()) {
            String rows = reachedSuspension.stream()
                    .map(i -> "<li><b>" + i.getInvoiceCode() + "</b> · " + HtmlUtils.htmlEscape(i.getOwner().getFullName())
                            + " · " + InvoiceGenerator.money(i.getFinalPayableAmount()) + "</li>")
                    .collect(Collectors.joining());
            for (User m : userRepository.findByRoleNameInAndIsActiveTrue(List.of("Club Manager"))) {
                invoiceService.notify(m, "[HorseTrack] " + reachedSuspension.size() + " hóa đơn đủ điều kiện đình chỉ dịch vụ",
                        "<p>Các hóa đơn sau đã " + billingPolicy.getSuspensionDays() + " ngày chưa thanh toán (BR_13):</p><ul>" + rows + "</ul>"
                                + "<p>Xem tại GET /api/v1/billing/invoices?status=OVERDUE và đình chỉ bằng PUT /api/v1/billing/owners/{ownerId}/suspend.</p>");
            }
        }
        return newlyOverdue.size();
    }
}
