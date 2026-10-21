package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.entity.StockHold;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.repository.StockHoldRepository;
import com.horsetrack.equinecare.modules.inventory.service.ProtocolStockService;
import com.horsetrack.equinecare.modules.medical.dto.*;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolItem;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolPhase;
import com.horsetrack.equinecare.modules.medical.entity.ProtocolProgressNote;
import com.horsetrack.equinecare.modules.medical.entity.TreatmentProtocol;
import com.horsetrack.equinecare.modules.medical.repository.MedicalRecordRepository;
import com.horsetrack.equinecare.modules.medical.repository.ProtocolProgressNoteRepository;
import com.horsetrack.equinecare.modules.medical.repository.TreatmentProtocolRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Phác đồ điều trị của bác sĩ thú y (SRS màn hình 61–65, BR_07).
 * Bác sĩ lập phác đồ -> kho kiểm hàng -> bác sĩ xác nhận / chờ hàng / đổi phiên bản -> kho cấp thuốc -> điều trị -> kết thúc.
 */
@Service
@RequiredArgsConstructor
public class MedicalManagementService {

    // Nhóm vật tư bác sĩ được kê trong phác đồ (danh mục thuốc)
    private static final Set<String> MEDICAL_CATEGORIES = Set.of(
            "MEDICINE", "VACCINE", "MEDICAL_SUPPLY",
            "Thuốc", "Vaccine", "Vật tư y tế",
            "Thuoc", "Vat tu y te"
    );
    // Trạng thái trước khi cấp thuốc: còn sửa (phiên bản mới) hoặc hủy được
    private static final Set<String> REVISABLE_STATES = Set.of(
            "WAITING_STOCK_CHECK", "STOCK_AVAILABLE", "STOCK_SHORTAGE", "WAITING_RESTOCK", "REQUIRES_VET_REVIEW", "VET_CONFIRMED", "ON_HOLD_PAYMENT");

    private final TreatmentProtocolRepository protocolRepository;
    private final ProtocolProgressNoteRepository noteRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final InventoryItemRepository itemRepository;
    private final StockHoldRepository holdRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final ProtocolStockService protocolStockService;
    private final TreatmentProtocolMapper mapper;
    private final EntityManager entityManager;
    private final com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling phaseBilling;

    // ==========================================
    // DANH MỤC THUỐC (chỉ đọc)
    // ==========================================

    @Transactional(readOnly = true)
    public List<MedicineCatalogDTO> getMedicineCatalog() {
        return itemRepository.findByCategoryInOrderByItemNameAsc(MEDICAL_CATEGORIES).stream()
                .map(i -> MedicineCatalogDTO.builder()
                        .itemId(i.getItemId())
                        .itemCode(i.getItemCode())
                        .itemName(i.getItemName())
                        .category(i.getCategory())
                        .itemGroup(i.getItemGroup())
                        .unit(i.getUnit())
                        .unitPrice(i.getUnitPrice())
                        .availableStock(BigDecimal.valueOf(i.getAvailableStock()).setScale(2, RoundingMode.HALF_UP).doubleValue())
                        .route(i.getRoute())
                        .isControlled(i.getIsControlled())
                        .withdrawalHours(i.getWithdrawalHours())
                        .doseReference(i.getDoseReference())
                        .build())
                .collect(Collectors.toList());
    }

    // ==========================================
    // LẬP VÀ XEM PHÁC ĐỒ
    // ==========================================

    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO createProtocol(ProtocolCreateRequestDTO request) {
        TreatmentProtocol protocol = buildProtocol(request, null);
        return mapper.toDTO(saveAndReload(protocol), true);
    }

    @Transactional(readOnly = true)
    public List<TreatmentProtocolDTO> getProtocols(String state, Integer horseId) {
        String st = StringUtils.hasText(state) ? state.trim().toUpperCase() : null;
        return mapper.toSummaryList(protocolRepository.search(st, horseId));
    }

    @Transactional(readOnly = true)
    public TreatmentProtocolDTO getProtocolDetail(Integer protocolId) {
        return mapper.toDTO(findProtocol(protocolId), true);
    }

    // ==========================================
    // QUYẾT ĐỊNH CỦA BÁC SĨ SAU KHI KHO TRẢ LỜI (BR_07)
    // ==========================================

    /** Kho đã giữ đủ hàng: bác sĩ xác nhận để kho được cấp thuốc. */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO confirm(Integer protocolId) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"STOCK_AVAILABLE".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ xác nhận được khi kho đã giữ đủ hàng (STOCK_AVAILABLE). Hiện tại: "
                    + protocol.getState() + ". Nếu thiếu hàng, hãy chọn chờ hàng hoặc tạo phiên bản mới.");
        }
        protocol.setState("VET_CONFIRMED");
        protocol.setConfirmedAt(LocalDateTime.now());
        protocolRepository.save(protocol);
        // BR_16: lập hóa đơn từng giai đoạn; giai đoạn đã tới ngày mà chưa trả thì tạm dừng ngay (trừ ca cấp cứu - BR_17)
        phaseBilling.issuePhaseInvoices(protocol);
        phaseBilling.evaluate(protocol, LocalDate.now());
        return mapper.toDTO(protocol, true);
    }

    /** Kho báo thiếu hoặc hàng về trễ: bác sĩ chấp nhận chờ hàng, giữ nguyên phác đồ. */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO waitForRestock(Integer protocolId, ProtocolWaitRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"STOCK_SHORTAGE".equals(protocol.getState()) && !"REQUIRES_VET_REVIEW".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ chọn chờ hàng khi kho báo thiếu (STOCK_SHORTAGE) hoặc hàng về trễ (REQUIRES_VET_REVIEW). Hiện tại: "
                    + protocol.getState() + ".");
        }
        LocalDate eta = request != null && request.getWaitEta() != null ? request.getWaitEta() : latestShortageEta(protocolId);
        protocol.setState("WAITING_RESTOCK");
        protocol.setWaitEta(eta);
        protocol.setWaitNote(request != null && StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null);
        protocol.setWaitDecidedAt(LocalDateTime.now());
        protocol.setIsDelayed(eta != null && protocol.getRequiredBy() != null && eta.isAfter(protocol.getRequiredBy()));
        return mapper.toDTO(protocolRepository.save(protocol), true);
    }

    /** Đổi phác đồ (thuốc thiếu, hàng trễ...): bản cũ SUPERSEDED, trả hàng đã giữ, bản mới gửi kho kiểm lại. */
    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO createNewVersion(Integer protocolId, ProtocolCreateRequestDTO request) {
        TreatmentProtocol old = findProtocol(protocolId);
        if (!REVISABLE_STATES.contains(old.getState()) || alreadyDispensed(old)) {
            throw new IllegalArgumentException("Không thể tạo phiên bản mới cho phác đồ đang ở trạng thái " + old.getState() + ".");
        }
        if (!old.getHorse().getHorseId().equals(request.getHorseId())) {
            throw new IllegalArgumentException("Phiên bản mới phải cùng ngựa với phác đồ cũ (horseId = " + old.getHorse().getHorseId() + ").");
        }

        protocolStockService.releaseHolds(old.getProtocolId(), "Trả hàng do phác đồ " + old.getProtocolCode() + " có phiên bản mới");
        phaseBilling.cancelOpenPhaseInvoices(old, false, "Phác đồ " + old.getProtocolCode() + " có phiên bản mới");
        old.setState("SUPERSEDED");
        old.setClosedAt(LocalDateTime.now());
        protocolRepository.saveAndFlush(old);

        TreatmentProtocol next = buildProtocol(request, old);
        return mapper.toDTO(saveAndReload(next), true);
    }

    // Phác đồ tạm dừng vì chưa thanh toán nhưng đã cấp thuốc thì không còn sửa / hủy được
    private boolean alreadyDispensed(TreatmentProtocol protocol) {
        return com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())
                && !holdRepository.findByProtocolIdAndStatusIn(protocol.getProtocolId(), List.of("ISSUED")).isEmpty();
    }

    // ==========================================
    // ĐIỀU TRỊ & KẾT THÚC
    // ==========================================

    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO addProgressNote(Integer protocolId, ProtocolTextRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!"IN_TREATMENT".equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ ghi tiến trình khi phác đồ đang điều trị (IN_TREATMENT). Hiện tại: " + protocol.getState() + ".");
        }
        noteRepository.save(ProtocolProgressNote.builder()
                .protocol(protocol)
                .writtenBy(currentUser())
                .writtenAt(LocalDateTime.now())
                .content(request.getContent().trim())
                .build());
        return mapper.toDTO(protocol, true);
    }

    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO complete(Integer protocolId, ProtocolTextRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        // Phác đồ đang tạm dừng vì chưa thanh toán (đã cấp thuốc) vẫn được bác sĩ kết thúc
        if (!"IN_TREATMENT".equals(protocol.getState()) && !com.horsetrack.equinecare.modules.finance.service.ProtocolPhaseBilling.ON_HOLD.equals(protocol.getState())) {
            throw new IllegalArgumentException("Chỉ kết thúc được phác đồ đang điều trị (IN_TREATMENT) hoặc đang tạm dừng do chưa thanh toán. Hiện tại: " + protocol.getState() + ".");
        }
        // Giai đoạn chưa bắt đầu thì không thu tiền nữa
        phaseBilling.cancelOpenPhaseInvoices(protocol, true, "Phác đồ " + protocol.getProtocolCode() + " kết thúc sớm");
        protocol.setState("COMPLETED");
        protocol.setOutcome(request.getContent().trim());
        protocol.setClosedAt(LocalDateTime.now());
        return mapper.toDTO(protocolRepository.save(protocol), true);
    }

    @Transactional(rollbackFor = Exception.class)
    public TreatmentProtocolDTO cancel(Integer protocolId, ProtocolTextRequestDTO request) {
        TreatmentProtocol protocol = findProtocol(protocolId);
        if (!REVISABLE_STATES.contains(protocol.getState()) || alreadyDispensed(protocol)) {
            throw new IllegalArgumentException("Chỉ hủy được phác đồ chưa cấp thuốc. Hiện tại: " + protocol.getState() + ".");
        }
        protocolStockService.releaseHolds(protocolId, "Trả hàng do hủy phác đồ " + protocol.getProtocolCode());
        phaseBilling.cancelOpenPhaseInvoices(protocol, false, "Hủy phác đồ " + protocol.getProtocolCode());
        protocol.setState("CANCELLED");
        protocol.setClosedAt(LocalDateTime.now());
        protocol.setNote(appendNote(protocol.getNote(), "Hủy: " + request.getContent().trim()));
        return mapper.toDTO(protocolRepository.save(protocol), true);
    }

    // ==========================================
    // HÀM HỖ TRỢ
    // ==========================================

    private TreatmentProtocol buildProtocol(ProtocolCreateRequestDTO request, TreatmentProtocol previous) {
        Horse horse = horseRepository.findById(request.getHorseId())
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + request.getHorseId()));

        Integer caseId = request.getCaseId() != null ? request.getCaseId() : (previous != null ? previous.getCaseId() : null);
        Integer recordId = request.getRecordId() != null ? request.getRecordId() : (previous != null ? previous.getRecordId() : null);
        if (caseId != null && !vetCaseExists(caseId)) {
            throw new ResourceNotFoundException("Không tìm thấy ca khám với ID: " + caseId);
        }
        if (recordId != null && !medicalRecordRepository.existsById(recordId)) {
            throw new ResourceNotFoundException("Không tìm thấy bệnh án với ID: " + recordId);
        }

        TreatmentProtocol protocol = TreatmentProtocol.builder()
                .horse(horse)
                .vet(currentUser())
                .caseId(caseId)
                .recordId(recordId)
                .versionNo(previous != null ? previous.getVersionNo() + 1 : 1)
                .replacesProtocol(previous)
                .diagnosis(request.getDiagnosis().trim())
                .state("WAITING_STOCK_CHECK") // Gửi kho kiểm hàng ngay khi lập
                .requiredBy(request.getRequiredBy())
                .isDelayed(false)
                .trainingLocked(Boolean.TRUE.equals(horse.getIsLocked()))
                .note(StringUtils.hasText(request.getNote()) ? request.getNote().trim() : null)
                .createdAt(LocalDateTime.now())
                .build();

        int phaseNo = 1;
        for (ProtocolCreateRequestDTO.Phase p : request.getPhases()) {
            ProtocolPhase phase = ProtocolPhase.builder()
                    .phaseNo(phaseNo++)
                    .phaseName(p.getPhaseName().trim())
                    .startDate(p.getStartDate())
                    .durationDays(p.getDurationDays())
                    .goal(p.getGoal())
                    .build();
            Set<Integer> seen = new HashSet<>();
            for (ProtocolCreateRequestDTO.Item i : p.getItems()) {
                if (!seen.add(i.getItemId())) {
                    throw new IllegalArgumentException("Thuốc ID " + i.getItemId() + " bị lặp trong giai đoạn '" + p.getPhaseName() + "'. Hãy gộp thành một dòng.");
                }
                InventoryItem item = itemRepository.findById(i.getItemId())
                        .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy thuốc/vật tư với ID: " + i.getItemId()));
                if (!MEDICAL_CATEGORIES.contains(item.getCategory())) {
                    throw new IllegalArgumentException("'" + item.getItemName() + "' (nhóm " + item.getCategory()
                            + ") không thuộc danh mục thuốc. Chỉ kê được MEDICINE, VACCINE hoặc MEDICAL_SUPPLY.");
                }
                phase.addItem(ProtocolItem.builder()
                        .item(item)
                        .quantity(BigDecimal.valueOf(i.getQuantity()).setScale(2, RoundingMode.HALF_UP).doubleValue())
                        .unitPrice(item.getUnitPrice() != null ? item.getUnitPrice() : BigDecimal.ZERO)
                        .dosage(StringUtils.hasText(i.getDosage()) ? i.getDosage().trim() : null)
                        .build());
            }
            protocol.addPhase(phase);
        }
        return protocol;
    }

    private TreatmentProtocol saveAndReload(TreatmentProtocol protocol) {
        TreatmentProtocol saved = protocolRepository.saveAndFlush(protocol);
        // Đọc lại để lấy protocol_code và line_total do DB tự sinh
        entityManager.refresh(saved);
        return saved;
    }

    private boolean vetCaseExists(Integer caseId) {
        Number count = (Number) entityManager.createNativeQuery("SELECT COUNT(*) FROM Vet_Cases WHERE case_id = :id")
                .setParameter("id", caseId)
                .getSingleResult();
        return count.intValue() > 0;
    }

    private LocalDate latestShortageEta(Integer protocolId) {
        return holdRepository.findByProtocolIdAndStatusIn(protocolId, List.of("SHORT")).stream()
                .map(StockHold::getEta)
                .filter(Objects::nonNull)
                .max(LocalDate::compareTo)
                .orElse(null);
    }

    private TreatmentProtocol findProtocol(Integer protocolId) {
        return protocolRepository.findById(protocolId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy phác đồ với ID: " + protocolId));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }

    private static String appendNote(String current, String addition) {
        String merged = StringUtils.hasText(current) ? current + " | " + addition : addition;
        return merged.length() > 1000 ? merged.substring(merged.length() - 1000) : merged;
    }
}
