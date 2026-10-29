package com.horsetrack.equinecare.modules.medical.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.GroomProfileRepository;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.inventory.entity.InventoryItem;
import com.horsetrack.equinecare.modules.inventory.repository.InventoryItemRepository;
import com.horsetrack.equinecare.modules.inventory.service.ItemCategories;
import com.horsetrack.equinecare.modules.medical.dto.CreateScheduleRequestDTO;
import com.horsetrack.equinecare.modules.medical.dto.VaccinationExecuteDTO;
import com.horsetrack.equinecare.modules.medical.dto.VaccinationRecordDTO;
import com.horsetrack.equinecare.modules.medical.entity.Vaccination;
import com.horsetrack.equinecare.modules.medical.repository.VaccinationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

/** Lịch tiêm phòng / tẩy giun: bác sĩ lên lịch, ghi nhận đã tiêm (tự tạo mũi nhắc lại theo chu kỳ); xem theo quyền với ngựa */
@Service
@RequiredArgsConstructor
public class VaccinationService {

    private static final DateTimeFormatter VN_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final List<String> OPEN = List.of(Vaccination.SCHEDULED, Vaccination.POSTPONED);
    private static final int DUE_SOON_DAYS = 7;

    private final VaccinationRepository vaccinationRepository;
    private final HorseRepository horseRepository;
    private final InventoryItemRepository itemRepository;
    private final UserRepository userRepository;
    private final GroomProfileRepository groomProfileRepository;
    private final AuditTrail auditTrail;
    private final com.horsetrack.equinecare.modules.inventory.repository.MaterialRequisitionRepository requisitionRepository;
    private final jakarta.persistence.EntityManager entityManager;

    // Kho đã cấp thuốc cho lịch tiêm
    private static final List<String> STOCK_ISSUED = List.of("ISSUED", "CLOSED");
    // Yêu cầu không còn hiệu lực: được gửi lại
    private static final List<String> STOCK_DEAD = List.of("REJECTED", "CANCELLED");

    /**
     * tab: due (quá hạn hoặc đến hạn trong 7 ngày), upcoming (sau 7 ngày), done (đã thực hiện), bỏ trống = tất cả.
     * Bác sĩ, Quản lý CLB xem mọi ngựa; HLV ngựa được phân công; Groom ngựa trong khu phụ trách; chủ ngựa ngựa của mình.
     */
    @Transactional(readOnly = true)
    public List<VaccinationRecordDTO> list(String tab, Integer horseId, String kind) {
        Predicate<Horse> canView = viewScope(currentUser());
        List<Vaccination> rows = horseId != null
                ? vaccinationRepository.findByHorse_HorseIdOrderByDueDateAscVaccinationIdAsc(horseId)
                : vaccinationRepository.findAllByOrderByDueDateAscVaccinationIdAsc();
        LocalDate today = LocalDate.now();
        String t = StringUtils.hasText(tab) ? tab.trim().toLowerCase() : "all";
        String k = StringUtils.hasText(kind) ? kind.trim().toUpperCase() : null;
        return rows.stream()
                .filter(v -> canView.test(v.getHorse()))
                .filter(v -> k == null || k.equals(v.getKind()))
                .filter(v -> switch (t) {
                    case "due" -> OPEN.contains(v.getStatus()) && !v.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS));
                    case "upcoming" -> OPEN.contains(v.getStatus()) && v.getDueDate().isAfter(today.plusDays(DUE_SOON_DAYS));
                    case "done" -> Vaccination.DONE.equals(v.getStatus());
                    case "all" -> true;
                    default -> throw new IllegalArgumentException("tab chỉ nhận due, upcoming, done hoặc bỏ trống.");
                })
                .map(this::dto)
                .toList();
    }

    @Transactional
    public List<VaccinationRecordDTO> schedule(CreateScheduleRequestDTO request) {
        String kind = StringUtils.hasText(request.getKind()) ? request.getKind().trim().toUpperCase() : "VACCINE";
        String name = request.getMedicineName().trim();
        LocalDate due = parseDate(request.getScheduledDate(), "Ngày dự kiến");
        if (due.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Ngày dự kiến không được ở quá khứ.");
        }
        Integer cycle = request.getCycleMonths();
        if (cycle != null) {
            int min = "DEWORM".equals(kind) ? 3 : 1, max = "DEWORM".equals(kind) ? 6 : 24;
            if (cycle < min || cycle > max) {
                throw new IllegalArgumentException(("DEWORM".equals(kind) ? "Chu kỳ tẩy giun" : "Chu kỳ tiêm nhắc") + " từ " + min + " đến " + max + " tháng.");
            }
        }
        if (request.getItemId() == null) {
            throw new IllegalArgumentException("Phải chọn thuốc / vaccine trong kho (itemId) để gửi yêu cầu cấp thuốc tới kho.");
        }
        InventoryItem item = medicalItem(request.getItemId());
        double dose = request.getDosePerHorse() != null ? request.getDosePerHorse() : 1.0;
        List<Vaccination> created = new ArrayList<>();
        for (Integer horseId : new LinkedHashSet<>(request.getHorseIds())) {
            Horse horse = horseRepository.findById(horseId)
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + horseId));
            if (horse.getStall() == null) {
                throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " chưa được duyệt vào CLB.");
            }
            if (vaccinationRepository.existsByHorse_HorseIdAndVaccineNameIgnoreCaseAndStatusIn(horseId, name, OPEN)) {
                throw new IllegalArgumentException("Ngựa " + horse.getHorseName() + " đã có lịch \"" + name + "\" chưa thực hiện.");
            }
            created.add(vaccinationRepository.saveAndFlush(Vaccination.builder()
                    .horse(horse)
                    .kind(kind)
                    .vaccineName(name)
                    .item(item)
                    .dueDate(due)
                    .cycleMonths(cycle)
                    .status(Vaccination.SCHEDULED)
                    .note(StringUtils.hasText(request.getNotes()) ? request.getNotes().trim() : null)
                    .build()));
        }
        User vet = currentUser();
        List<String> rqs = new ArrayList<>();
        for (Vaccination v : created) {
            rqs.add(requestStock(v, item, dose, vet).getRequisitionCode());
        }
        auditTrail.record("DEWORM".equals(kind) ? "SCHEDULE_DEWORM" : "SCHEDULE_VACCINE", "Vaccinations", null,
                name + " | " + due.format(VN_DATE) + (cycle != null ? " | chu kỳ " + cycle + " tháng" : "") + " | "
                        + created.stream().map(v -> v.getVaccinationCode() + " " + v.getHorse().getHorseName()).reduce((a, b) -> a + ", " + b).orElse("")
                        + " | gửi kho " + String.join(", ", rqs));
        return created.stream().map(this::dto).toList();
    }

    /** Ghi nhận đã tiêm; có chu kỳ thì tạo mũi nhắc lại = ngày tiêm + cycleMonths. Trả về [mũi vừa tiêm, mũi nhắc lại?] */
    @Transactional
    public List<VaccinationRecordDTO> execute(String codeOrId, VaccinationExecuteDTO request) {
        Vaccination v = find(codeOrId);
        if (!OPEN.contains(v.getStatus())) {
            throw new IllegalArgumentException("Mũi " + v.getVaccinationCode() + " đã " + (Vaccination.DONE.equals(v.getStatus()) ? "được ghi nhận" : "bỏ qua") + ".");
        }
        LocalDate today = LocalDate.now();
        String blocked = executeBlockedReason(v, latestRequisition(v).orElse(null), today);
        if (blocked != null) {
            throw new IllegalArgumentException(blocked);
        }
        LocalDate doneDay = request != null && StringUtils.hasText(request.getDoneAt()) ? parseDate(request.getDoneAt(), "Ngày thực hiện") : today;
        if (doneDay.isAfter(today)) {
            throw new IllegalArgumentException("Ngày thực hiện không được sau hôm nay.");
        }
        if (doneDay.isBefore(v.getDueDate())) {
            throw new IllegalArgumentException("Ngày thực hiện không được trước ngày dự kiến " + v.getDueDate().format(VN_DATE) + ".");
        }
        User vet = currentUser();
        v.setStatus(Vaccination.DONE);
        v.setDoneAt(doneDay.equals(today) ? LocalDateTime.now().withNano(0) : doneDay.atTime(LocalTime.of(8, 0)));
        v.setDoneBy(vet);
        if (request != null && StringUtils.hasText(request.getLotNo())) {
            v.setLotNo(request.getLotNo().trim());
        }
        if (request != null && StringUtils.hasText(request.getNote())) {
            v.setNote(StringUtils.hasText(v.getNote()) ? v.getNote() + " | " + request.getNote().trim() : request.getNote().trim());
        }
        vaccinationRepository.save(v);

        List<VaccinationRecordDTO> result = new ArrayList<>(List.of(dto(v)));
        Vaccination next = null;
        if (v.getCycleMonths() != null) {
            next = vaccinationRepository.saveAndFlush(Vaccination.builder()
                    .horse(v.getHorse())
                    .kind(v.getKind())
                    .vaccineName(v.getVaccineName())
                    .item(v.getItem())
                    .dueDate(doneDay.plusMonths(v.getCycleMonths()))
                    .cycleMonths(v.getCycleMonths())
                    .status(Vaccination.SCHEDULED)
                    .note("Nhắc lại sau " + v.getCycleMonths() + " tháng (từ " + v.getVaccinationCode() + ")")
                    .build());
            // Mũi nhắc lại cũng phải chờ kho cấp thuốc
            if (next.getItem() != null) {
                double dose = latestRequisition(v).map(r -> r.getQuantity()).orElse(1.0);
                requestStock(next, next.getItem(), dose, vet);
            }
            result.add(dto(next));
        }
        auditTrail.record("RECORD_VACCINATION", "Vaccinations", "status=SCHEDULED",
                "status=DONE | " + v.getVaccinationCode() + " | ngựa " + v.getHorse().getHorseName() + " | " + v.getVaccineName()
                        + (next != null ? " | nhắc lại " + next.getVaccinationCode() + " ngày " + next.getDueDate().format(VN_DATE) : ""));
        return result;
    }

    /**
     * Gửi lại yêu cầu cấp thuốc cho lịch tiêm (lịch cũ chưa có yêu cầu, hoặc yêu cầu trước bị kho từ chối / hủy).
     * Lịch chưa gắn thuốc thì phải gửi itemId.
     */
    @Transactional
    public VaccinationRecordDTO resendStockRequest(String codeOrId, Integer itemId, Double dose) {
        Vaccination v = find(codeOrId);
        if (!OPEN.contains(v.getStatus())) {
            throw new IllegalArgumentException("Lịch " + v.getVaccinationCode() + " đã " + (Vaccination.DONE.equals(v.getStatus()) ? "thực hiện" : "bỏ qua") + ".");
        }
        var last = latestRequisition(v);
        if (last.isPresent() && !STOCK_DEAD.contains(last.get().getStatus())) {
            throw new IllegalArgumentException("Lịch " + v.getVaccinationCode() + " đã có yêu cầu cấp thuốc " + last.get().getRequisitionCode()
                    + " (" + last.get().getStatus() + "), không gửi lại.");
        }
        if (itemId != null) {
            v.setItem(medicalItem(itemId));
            vaccinationRepository.save(v);
        }
        if (v.getItem() == null) {
            throw new IllegalArgumentException("Lịch " + v.getVaccinationCode() + " chưa gắn thuốc / vaccine trong kho: gửi itemId.");
        }
        double qty = dose != null && dose > 0 ? dose : last.map(r -> r.getQuantity()).orElse(1.0);
        var req = requestStock(v, v.getItem(), qty, currentUser());
        auditTrail.record("VACCINATION_STOCK_REQUEST", "Material_Requisitions", null,
                req.getRequisitionCode() + " | " + v.getVaccinationCode() + " | ngựa " + v.getHorse().getHorseName() + " | " + v.getItem().getItemName());
        return dto(v);
    }

    // Yêu cầu cấp thuốc tới kho cho một lịch tiêm; lý do bắt đầu bằng "VC-0001:" để tra lại
    private com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition requestStock(Vaccination v, InventoryItem item, double qty, User vet) {
        String reason = marker(v) + " " + ("DEWORM".equals(v.getKind()) ? "Tẩy giun" : "Tiêm phòng") + " " + v.getVaccineName()
                + " cho ngựa " + v.getHorse().getHorseName() + " ngày " + v.getDueDate().format(VN_DATE);
        var saved = requisitionRepository.saveAndFlush(com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition.builder()
                .requestedBy(vet)
                .barnZone(v.getHorse().getStall() != null ? v.getHorse().getStall().getBarnZone() : null)
                .item(item)
                .quantity(Math.round(qty * 100.0) / 100.0)
                .reason(reason.length() > 500 ? reason.substring(0, 500) : reason)
                .requestedAt(LocalDateTime.now())
                .neededBy(v.getDueDate())
                .status("SUBMITTED")
                .build());
        entityManager.refresh(saved);   // đọc requisition_code do DB sinh
        return saved;
    }

    private Optional<com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition> latestRequisition(Vaccination v) {
        return v.getVaccinationCode() == null ? Optional.empty()
                : requisitionRepository.findFirstByReasonStartingWithOrderByRequisitionIdDesc(marker(v));
    }

    private static String marker(Vaccination v) {
        return v.getVaccinationCode() + ":";   // không dùng [ ] vì là ký tự đặc biệt của LIKE
    }

    // Lý do chưa xác nhận tiêm được; null = được tiêm
    private static String executeBlockedReason(Vaccination v, com.horsetrack.equinecare.modules.inventory.entity.MaterialRequisition req, LocalDate today) {
        if (!OPEN.contains(v.getStatus())) {
            return null;
        }
        if (v.getDueDate().isAfter(today)) {
            return "Chưa tới ngày dự kiến " + v.getDueDate().format(VN_DATE) + ": không xác nhận được lịch " + v.getVaccinationCode() + " trong tương lai.";
        }
        if (req == null) {
            return "Lịch " + v.getVaccinationCode() + " chưa có yêu cầu cấp thuốc tới kho. Gửi yêu cầu (POST /api/v1/vaccinations/" + v.getVaccinationCode() + "/stock-request) và chờ kho cấp.";
        }
        if (!STOCK_ISSUED.contains(req.getStatus())) {
            return "Kho chưa cấp thuốc cho lịch " + v.getVaccinationCode() + " (yêu cầu " + req.getRequisitionCode() + " đang " + req.getStatus()
                    + (req.getRejectReason() != null ? ": " + req.getRejectReason() : "") + ")."
                    + (STOCK_DEAD.contains(req.getStatus()) ? " Gửi lại yêu cầu cấp thuốc." : " Chờ kho cấp rồi mới tiêm.");
        }
        return null;
    }

    private InventoryItem medicalItem(Integer itemId) {
        InventoryItem item = itemRepository.findById(itemId)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + itemId));
        if (!ItemCategories.isMedical(item.getCategory())) {
            throw new IllegalArgumentException("'" + item.getItemName() + "' (nhóm " + item.getCategory() + ") không phải thuốc / vaccine.");
        }
        return item;
    }

    // DTO kèm trạng thái yêu cầu cấp thuốc và quyền xác nhận tiêm
    private VaccinationRecordDTO dto(Vaccination v) {
        VaccinationRecordDTO d = toDto(v);
        var req = latestRequisition(v).orElse(null);
        if (req != null) {
            d.setRequisitionCode(req.getRequisitionCode());
            d.setRequisitionStatus(req.getStatus());
            d.setRequisitionRejectReason(req.getRejectReason());
        }
        if (OPEN.contains(v.getStatus())) {
            String blocked = executeBlockedReason(v, req, LocalDate.now());
            d.setCanExecute(blocked == null);
            d.setExecuteBlockedReason(blocked);
        } else {
            d.setCanExecute(false);
        }
        return d;
    }

    // ---------- hỗ trợ ----------

    private Predicate<Horse> viewScope(User user) {
        return switch (user.getRoleName()) {
            case "Veterinarian", "Club Manager" -> h -> true;
            case "Trainer" -> h -> h.getAssignedTrainer() != null && h.getAssignedTrainer().getUserId().equals(user.getUserId());
            case "Horse Owner" -> h -> h.getOwner() != null && h.getOwner().getUserId().equals(user.getUserId());
            case "Groom" -> {
                String zone = groomProfileRepository.findById(user.getUserId()).map(gp -> gp.getAssignedBarnZone()).orElse(null);
                yield h -> zone != null && h.getStall() != null && h.getStall().getBarnZone() != null
                        && zone.trim().equalsIgnoreCase(h.getStall().getBarnZone().trim());
            }
            default -> throw new AccessDeniedException("Bạn không có quyền xem lịch tiêm phòng");
        };
    }

    static VaccinationRecordDTO toDto(Vaccination v) {
        LocalDate today = LocalDate.now();
        long days = ChronoUnit.DAYS.between(today, v.getDueDate());
        boolean open = OPEN.contains(v.getStatus());
        String code;
        String label;
        if (Vaccination.DONE.equals(v.getStatus())) {
            code = "DONE";
            label = "Đã thực hiện";
        } else if (Vaccination.SKIPPED.equals(v.getStatus())) {
            code = "SKIPPED";
            label = "Bỏ qua";
        } else if (days < 0) {
            code = "OVERDUE";
            label = "Quá hạn";
        } else if (days <= DUE_SOON_DAYS) {
            code = "DUE_SOON";
            label = "Sắp đến hạn";
        } else if (Vaccination.POSTPONED.equals(v.getStatus())) {
            code = "POSTPONED";
            label = "Hoãn";
        } else {
            code = "SCHEDULED";
            label = "Đã lên lịch";
        }
        return VaccinationRecordDTO.builder()
                .id(v.getVaccinationId())
                .recordCode(v.getVaccinationCode())
                .horseId(v.getHorse().getHorseId())
                .horseName(v.getHorse().getHorseName())
                .kind(v.getKind())
                .typeName(v.getVaccineName())
                .category("DEWORM".equals(v.getKind()) ? "Tẩy giun" : "Vaccine")
                .dueDate(v.getDueDate())
                .expectedDate(v.getDueDate().format(VN_DATE))
                .countdown(!open ? null : days == 0 ? "hôm nay" : days > 0 ? "còn " + days + " ngày" : "quá hạn " + (-days) + " ngày")
                .cycleMonths(v.getCycleMonths())
                .statusCode(code)
                .status(label)
                .doneAt(v.getDoneAt())
                .doneByName(v.getDoneBy() != null ? v.getDoneBy().getFullName() : null)
                .lotNo(v.getLotNo())
                .itemId(v.getItem() != null ? v.getItem().getItemId() : null)
                .itemName(v.getItem() != null ? v.getItem().getItemName() : null)
                .note(v.getNote())
                .build();
    }

    private static LocalDate parseDate(String value, String label) {
        String s = value.trim();
        try {
            return s.contains("/") ? LocalDate.parse(s, VN_DATE) : LocalDate.parse(s.length() > 10 ? s.substring(0, 10) : s);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(label + " '" + value + "' không hợp lệ (yyyy-MM-dd hoặc dd/MM/yyyy).");
        }
    }

    private Vaccination find(String codeOrId) {
        String ref = codeOrId.trim();
        return (ref.matches("\\d+") ? vaccinationRepository.findById(Integer.valueOf(ref)) : vaccinationRepository.findByVaccinationCode(ref.toUpperCase()))
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy mũi tiêm " + codeOrId));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }
}
