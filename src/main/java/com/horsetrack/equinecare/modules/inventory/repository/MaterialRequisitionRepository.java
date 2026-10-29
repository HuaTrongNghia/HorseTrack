package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MaterialRequisitionRepository extends JpaRepository<MaterialRequisition, Integer> {

    List<MaterialRequisition> findByRequestedBy_UserIdOrderByRequestedAtDesc(Integer userId);

    // Yêu cầu cấp thuốc gắn với lịch tiêm (lý do bắt đầu bằng "VC-0001:"), mới nhất trước
    java.util.Optional<MaterialRequisition> findFirstByReasonStartingWithOrderByRequisitionIdDesc(String prefix);

    // Danh sách cho kho, mới nhất trước; tham số null thì bỏ qua
    @Query("SELECT r FROM MaterialRequisition r " +
            "WHERE (:status IS NULL OR r.status = :status) " +
            "AND (:barnZone IS NULL OR r.barnZone = :barnZone) " +
            "ORDER BY r.requestedAt DESC, r.requisitionId DESC")
    List<MaterialRequisition> search(@Param("status") String status, @Param("barnZone") String barnZone);
}
