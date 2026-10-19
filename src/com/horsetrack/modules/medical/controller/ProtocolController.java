package com.horsetrack.controller;

import com.horsetrack.dto.ProtocolDTO;
import com.horsetrack.dto.ShipmentLotDTO;
import com.horsetrack.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/protocols")
@CrossOrigin("*")
public class ProtocolController {

    // Trả về danh sách phác đồ theo Tab (Cần bác sĩ xử lý / Chờ kho / Đã đóng)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProtocolDTO>>> getProtocols(@RequestParam(defaultValue = "Cần bác sĩ xử lý") String tab) {

        ProtocolDTO p1 = new ProtocolDTO();
        p1.setProtocolCode("PD-0013 v1"); p1.setHorseName("Comet"); p1.setDiagnosis("Viêm móng nhẹ chân sau trái");
        p1.setCaseCode("#C-0011"); p1.setRequiredDate("04/10/2026"); p1.setEstimatedCost("4.970.000 ₫");
        p1.setStatus("Thiếu vật tư — chờ BS quyết định"); p1.setNextStep("Thiếu Vitamin + Selen...");

        ProtocolDTO p2 = new ProtocolDTO();
        p2.setProtocolCode("PD-0012 v1"); p2.setHorseName("Bolt"); p2.setDiagnosis("Viêm khớp nhẹ");
        p2.setCaseCode("#C-0009"); p2.setRequiredDate("04/10/2026"); p2.setEstimatedCost("1.600.000 ₫");
        p2.setStatus("Kho đủ hàng — chờ BS xác nhận"); p2.setNextStep("Kho đủ hàng...");

        return ResponseEntity.ok(new ApiResponse<>(200, "Thành công", Arrays.asList(p1, p2)));
    }

    // Trả về danh sách lô hàng liên quan
    @GetMapping("/shipments")
    public ResponseEntity<ApiResponse<List<ShipmentLotDTO>>> getRelatedShipments() {
        ShipmentLotDTO s1 = new ShipmentLotDTO();
        s1.setLotCode("SHIP-0024"); s1.setItemName("Bông gạc"); s1.setQuantity(50); s1.setUnit("cuộn");
        s1.setEta("10/10"); s1.setTargetProtocol("PD-0011"); s1.setStatus("Trễ");

        return ResponseEntity.ok(new ApiResponse<>(200, "Thành công", Arrays.asList(s1)));
    }
}
