package com.horsetrack.equinecare.modules.finance.repository;

import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {

    // Giao dịch VNPay đang chờ của một hóa đơn, mới nhất trước (để dùng lại khi chủ ngựa bấm thanh toán lần nữa)
    List<PaymentTransaction> findByInvoice_InvoiceIdAndPaymentStatusOrderByPaidAtDesc(Integer invoiceId, String paymentStatus);

    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);

    List<PaymentTransaction> findByInvoice_Owner_UserIdAndPaymentStatusInOrderByPaidAtDesc(Integer ownerId, Collection<String> statuses);

    // Tiền đã thu: chỉ giao dịch đã được server xác minh
    List<PaymentTransaction> findByPaymentStatusAndVerifiedAtBetween(String status, LocalDateTime from, LocalDateTime to);

    // Giao dịch còn chờ xác minh (để hỏi lại VNPay)
    List<PaymentTransaction> findByPaymentStatusAndPaidAtAfterOrderByPaidAtAsc(String status, LocalDateTime after);

    List<PaymentTransaction> findByInvoice_Owner_UserIdAndPaymentStatus(Integer ownerId, String status);
}
