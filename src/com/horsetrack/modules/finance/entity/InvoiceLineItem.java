package com.horsetrack.equinecare.modules.finance.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Một dòng phí trên hóa đơn (bảng Invoice_Line_Items). Cột amount do DB tính = unit_price * quantity. */
@Entity
@Table(name = "Invoice_Line_Items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InvoiceLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "line_item_id")
    private Integer lineItemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // BOARDING, DAILY_CARE, MEDICATION, MEDICAL, TRAINING, DIET_EXTRA, COMPETITION, OTHER
    @Column(name = "expense_category", nullable = false, length = 30)
    private String expenseCategory;

    // Id bản ghi nguồn (stall_id, item_id, protocol_item_id, entry_id...)
    @Column(name = "reference_id")
    private Integer referenceId;

    @Column(name = "item_description", nullable = false, length = 255)
    private String itemDescription;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "quantity", nullable = false, precision = 8, scale = 2)
    private BigDecimal quantity;

    @Column(name = "service_date", nullable = false)
    private LocalDateTime serviceDate;

    /** Thành tiền (giống cột tính toán amount trong DB) */
    public BigDecimal getAmount() {
        return unitPrice.multiply(quantity);
    }
}
