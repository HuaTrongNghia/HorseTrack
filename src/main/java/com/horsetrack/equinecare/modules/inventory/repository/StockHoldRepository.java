package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.StockHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface StockHoldRepository extends JpaRepository<StockHold, Integer> {

    List<StockHold> findByProtocolIdOrderByHoldIdAsc(Integer protocolId);

    // Các dòng còn hiệu lực (HELD/SHORT) của một phác đồ
    List<StockHold> findByProtocolIdAndStatusIn(Integer protocolId, Collection<String> statuses);

    @org.springframework.data.jpa.repository.Query("SELECT DISTINCT h.protocolId FROM StockHold h WHERE h.status IN :statuses")
    List<Integer> findProtocolIdsByStatusIn(@org.springframework.data.repository.query.Param("statuses") Collection<String> statuses);

    List<StockHold> findByPurchaseOrderIdAndStatus(Integer purchaseOrderId, String status);
}
