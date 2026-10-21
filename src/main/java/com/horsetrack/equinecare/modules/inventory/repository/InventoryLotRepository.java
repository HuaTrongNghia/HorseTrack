package com.horsetrack.equinecare.modules.inventory.repository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryLot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryLotRepository extends JpaRepository<InventoryLot, Integer> {

    Optional<InventoryLot> findByItem_ItemIdAndLotNo(Integer itemId, String lotNo);

    // Danh sách lô theo hạn dùng tăng dần
    List<InventoryLot> findByItem_ItemIdOrderByExpiryDateAscLotIdAsc(Integer itemId);
}
