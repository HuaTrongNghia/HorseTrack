package com.horsetrack.equinecare.modules.finance.repository;

import com.horsetrack.equinecare.modules.finance.entity.InvoiceRefund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface InvoiceRefundRepository extends JpaRepository<InvoiceRefund, Integer> {

    Optional<InvoiceRefund> findFirstByInvoice_InvoiceIdOrderByRefundIdDesc(Integer invoiceId);

    List<InvoiceRefund> findByStatusOrderByRequestedAtAsc(String status);

    List<InvoiceRefund> findAllByOrderByRequestedAtDesc();

    long countByStatus(String status);
}
