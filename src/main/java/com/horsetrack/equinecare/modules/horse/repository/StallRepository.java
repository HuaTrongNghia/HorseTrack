package com.horsetrack.equinecare.modules.horse.repository;

import com.horsetrack.equinecare.modules.horse.entity.Stall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface StallRepository extends JpaRepository<Stall, Integer> {

    @Query("SELECT CASE WHEN COUNT(h) > 0 THEN true ELSE false END FROM Horse h WHERE h.stall.stallId = :stallId")
    boolean existsByIdAndHorseIsNotNull(@Param("stallId") Integer stallId);

    @Query("SELECT s FROM Stall s LEFT JOIN FETCH s.horse")
    java.util.List<Stall> findAllWithHorse();

    // Danh sách khu chuồng đang có (để kiểm tra khu khi phân công Groom)
    @Query("SELECT DISTINCT s.barnZone FROM Stall s ORDER BY s.barnZone")
    java.util.List<String> findDistinctBarnZones();
}
