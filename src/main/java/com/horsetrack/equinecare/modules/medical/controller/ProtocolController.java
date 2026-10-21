package com.horsetrack.equinecare.modules.medical.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.medical.dto.ProtocolDTO;
import com.horsetrack.equinecare.modules.medical.dto.ShipmentLotDTO;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping({"/api/v1/protocols", "/api/protocols"})
@CrossOrigin("*")
public class ProtocolController {

    // Trả về danh sách phác đồ theo Tab (Cần bác sĩ xử lý / Chờ kho / Đã đóng)
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProtocolDTO>>> getProtocols(@RequestParam(defaultValue = "Cần bác sĩ xử lý") String tab) {
        ProtocolDTO p1 = ProtocolDTO.builder()
                .protocolCode("PD-0013 v1")
                .horseName("Comet")
                .diagnosis("Viêm móng nhẹ chân sau trái")
                .caseCode("#C-0011")
                .requiredDate("04/10/2026")
                .estimatedCost("4.970.000 ₫")
                .status("Thiếu vật tư — chờ BS quyết định")
                .nextStep("Thiếu Vitamin + Selen...")
                .build();

        ProtocolDTO p2 = ProtocolDTO.builder()
                .protocolCode("PD-0012 v1")
                .horseName("Bolt")
                .diagnosis("Viêm khớp nhẹ")
                .caseCode("#C-0009")
                .requiredDate("04/10/2026")
                .estimatedCost("1.600.000 ₫")
                .status("Kho đủ hàng — chờ BS xác nhận")
                .nextStep("Kho đủ hàng...")
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Thành công", Arrays.asList(p1, p2)));
    }

    // Trả về danh sách lô hàng liên quan
    @GetMapping("/shipments")
    public ResponseEntity<ApiResponse<List<ShipmentLotDTO>>> getRelatedShipments() {
        ShipmentLotDTO s1 = ShipmentLotDTO.builder()
                .lotCode("SHIP-0024")
                .itemName("Bông gạc")
                .quantity(50)
                .unit("cuộn")
                .eta("10/10")
                .targetProtocol("PD-0011")
                .status("Trễ")
                .build();

        return ResponseEntity.ok(ApiResponse.ok("Thành công", Arrays.asList(s1)));
    }
}
