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
                .map(VaccinationService::toDto)
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
        InventoryItem item = null;
        if (request.getItemId() != null) {
            item = itemRepository.findById(request.getItemId())
                    .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy vật tư với ID: " + request.getItemId()));
            if (!ItemCategories.isMedical(item.getCategory())) {
                throw new IllegalArgumentException("'" + item.getItemName() + "' (nhóm " + item.getCategory() + ") không phải thuốc / vaccine.");
            }
        }
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
        auditTrail.record("DEWORM".equals(kind) ? "SCHEDULE_DEWORM" : "SCHEDULE_VACCINE", "Vaccinations", null,
                name + " | " + due.format(VN_DATE) + (cycle != null ? " | chu kỳ " + cycle + " tháng" : "") + " | "
                        + created.stream().map(v -> v.getVaccinationCode() + " " + v.getHorse().getHorseName()).reduce((a, b) -> a + ", " + b).orElse(""));
        return created.stream().map(VaccinationService::toDto).toList();
    }

    /** Ghi nhận đã tiêm; có chu kỳ thì tạo mũi nhắc lại = ngày tiêm + cycleMonths. Trả về [mũi vừa tiêm, mũi nhắc lại?] */
    @Transactional
    public List<VaccinationRecordDTO> execute(String codeOrId, VaccinationExecuteDTO request) {
        Vaccination v = find(codeOrId);
        if (!OPEN.contains(v.getStatus())) {
            throw new IllegalArgumentException("Mũi " + v.getVaccinationCode() + " đã " + (Vaccination.DONE.equals(v.getStatus()) ? "được ghi nhận" : "bỏ qua") + ".");
        }
        LocalDate today = LocalDate.now();
        LocalDate doneDay = request != null && StringUtils.hasText(request.getDoneAt()) ? parseDate(request.getDoneAt(), "Ngày thực hiện") : today;
        if (doneDay.isAfter(today)) {
            throw new IllegalArgumentException("Ngày thực hiện không được sau hôm nay.");
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

        List<VaccinationRecordDTO> result = new ArrayList<>(List.of(toDto(v)));
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
            result.add(toDto(next));
        }
        auditTrail.record("RECORD_VACCINATION", "Vaccinations", "status=SCHEDULED",
                "status=DONE | " + v.getVaccinationCode() + " | ngựa " + v.getHorse().getHorseName() + " | " + v.getVaccineName()
                        + (next != null ? " | nhắc lại " + next.getVaccinationCode() + " ngày " + next.getDueDate().format(VN_DATE) : ""));
        return result;
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
