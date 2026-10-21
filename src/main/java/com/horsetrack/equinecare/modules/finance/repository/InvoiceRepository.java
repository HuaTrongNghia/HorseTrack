package com.horsetrack.equinecare.modules.finance.repository;

import com.horsetrack.equinecare.modules.finance.entity.Invoice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface InvoiceRepository extends JpaRepository<Invoice, Integer> {

    boolean existsByInvoiceCode(String invoiceCode);

    // Mỗi ngựa chỉ có một hóa đơn tháng (mã HD-..., chưa hủy) cho mỗi kỳ; hóa đơn giai đoạn phác đồ có mã GD-...
    boolean existsByHorse_HorseIdAndBillingMonthAndPaymentStatusNotAndInvoiceCodeStartingWith(Integer horseId, String billingMonth, String status, String codePrefix);

    List<Invoice> findByInvoiceCodeStartingWithAndPaymentStatusNotOrderByInvoiceIdDesc(String codePrefix, String status);

    List<Invoice> findByOwner_UserIdAndPaymentStatusInOrderByDueDateAsc(Integer ownerId, Collection<String> statuses);

    List<Invoice> findByOwner_UserIdOrderByIssuedAtDesc(Integer ownerId);

    List<Invoice> findByPaymentStatusInOrderByDueDateAsc(Collection<String> statuses);

    // Hóa đơn chưa thanh toán đã quá hạn (để chuyển OVERDUE)
    List<Invoice> findByPaymentStatusAndDueDateBefore(String status, LocalDate date);

    // Danh sách cho Club Manager, lọc tùy chọn theo trạng thái / kỳ / chủ ngựa
    @Query("SELECT i FROM Invoice i WHERE (:status IS NULL OR i.paymentStatus = :status) " +
            "AND (:billingMonth IS NULL OR i.billingMonth = :billingMonth) " +
            "AND (:ownerId IS NULL OR i.owner.userId = :ownerId) ORDER BY i.issuedAt DESC, i.invoiceId DESC")
    Page<Invoice> search(@Param("status") String status, @Param("billingMonth") String billingMonth,
                         @Param("ownerId") Integer ownerId, Pageable pageable);

    // Hóa đơn (chưa hủy) có kỳ trong khoảng [from, to], định dạng yyyy-MM so sánh được theo chuỗi
    @Query("SELECT i FROM Invoice i WHERE i.paymentStatus <> 'CANCELLED' AND i.billingMonth >= :from AND i.billingMonth <= :to")
    List<Invoice> findBilledBetween(@Param("from") String fromMonth, @Param("to") String toMonth);
}
