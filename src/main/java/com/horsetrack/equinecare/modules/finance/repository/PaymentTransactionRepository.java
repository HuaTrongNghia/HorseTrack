package com.horsetrack.equinecare.modules.finance.repository;

import com.horsetrack.equinecare.modules.finance.entity.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, Integer> {

    Optional<PaymentTransaction> findByTransactionRef(String transactionRef);

    List<PaymentTransaction> findByInvoice_Owner_UserIdAndPaymentStatusInOrderByPaidAtDesc(Integer ownerId, Collection<String> statuses);

    // Tiền đã thu: chỉ giao dịch đã được server xác minh
    List<PaymentTransaction> findByPaymentStatusAndVerifiedAtBetween(String status, LocalDateTime from, LocalDateTime to);
}
