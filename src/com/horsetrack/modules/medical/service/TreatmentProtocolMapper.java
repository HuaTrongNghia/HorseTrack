package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.modules.inventory.entity.StockHold;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.medical.dto.TreatmentProtocolDTO;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.ProtocolProgressNoteRepository;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

/** Chuyển phác đồ sang DTO; dùng chung cho API của bác sĩ và của kho. */


        
      

    // Tự tính thay vì đọc cột line_total (cột tính toán chỉ có sau khi đọc lại từ DB)
   
}
