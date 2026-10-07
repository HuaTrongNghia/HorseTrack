package com.horsetrack.equinecare.modules.horse.controller;

import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.service.HorseService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/horses")
public class HorseController {

    private final HorseService horseService;

    // Khởi tạo biến qua Constructor thuần cho Spring tiêm phụ thuộc
    public HorseController(HorseService horseService) {
        this.horseService = horseService;
    }

    @GetMapping("/owner/{ownerId}")
    public ResponseEntity<List<Horse>> getHorsesByOwner(@PathVariable Integer ownerId) {
        return ResponseEntity.ok(horseService.getHorsesByOwner(ownerId));
    }
}