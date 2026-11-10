package com.horsetrack.equinecare.modules.horse.service;

import com.horsetrack.equinecare.common.exception.ResourceNotFoundException;
import com.horsetrack.equinecare.modules.audit.service.AuditTrail;
import com.horsetrack.equinecare.modules.auth.entity.User;
import com.horsetrack.equinecare.modules.auth.repository.UserRepository;
import com.horsetrack.equinecare.modules.horse.dto.RegistrationAttachmentDTO;
import com.horsetrack.equinecare.modules.horse.entity.Horse;
import com.horsetrack.equinecare.modules.horse.entity.HorseRegistration;
import com.horsetrack.equinecare.modules.horse.entity.RegistrationAttachment;
import com.horsetrack.equinecare.modules.horse.repository.HorseRegistrationRepository;
import com.horsetrack.equinecare.modules.horse.repository.HorseRepository;
import com.horsetrack.equinecare.modules.horse.repository.RegistrationAttachmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** Giấy tờ chủ ngựa đính kèm hồ sơ đăng ký ngựa; Club Manager xem khi duyệt */
@Service
@RequiredArgsConstructor
public class RegistrationAttachmentService {

    public static final long MAX_BYTES = 10L * 1024 * 1024;
    private static final int MAX_FILES_PER_REGISTRATION = 10;
    private static final Set<String> DOC_TYPES = Set.of("OWNERSHIP", "OTHER");
    private static final String ROLE_MANAGER = "Club Manager";

    /** Nội dung tệp để trả về khi xem / tải */
    public record FileContent(String fileName, String mimeType, byte[] content) {
    }

    private final RegistrationAttachmentRepository attachmentRepository;
    private final HorseRegistrationRepository registrationRepository;
    private final HorseRepository horseRepository;
    private final UserRepository userRepository;
    private final AuditTrail auditTrail;

    /** Chủ ngựa tải giấy tờ lên hồ sơ đang chờ duyệt (mới nộp hoặc vừa nộp lại) */
    @Transactional
    public RegistrationAttachmentDTO upload(Integer horseId, MultipartFile file, String docType) {
        Horse horse = findHorse(horseId);
        User owner = currentUser();
        if (!horse.getOwner().getUserId().equals(owner.getUserId())) {
            throw new AccessDeniedException("Bạn chỉ được đính kèm giấy tờ cho ngựa của chính mình");
        }
        HorseRegistration reg = pendingRegistration(horseId);
        String type = StringUtils.hasText(docType) ? docType.trim().toUpperCase() : "OWNERSHIP";
        if (!DOC_TYPES.contains(type)) {
            throw new IllegalArgumentException("Loại giấy tờ (docType) chỉ nhận OWNERSHIP hoặc OTHER.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Chưa chọn tệp.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new IllegalArgumentException("Tệp tối đa 10 MB.");
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException ex) {
            throw new IllegalArgumentException("Không đọc được tệp tải lên.");
        }
        // Kiểm tra nội dung thật của tệp, không tin phần mở rộng / content-type do trình duyệt gửi
        String mime = detectMime(content);
        if (mime == null) {
            throw new IllegalArgumentException("Chỉ nhận tệp PDF, JPG hoặc PNG (nội dung tệp không đúng định dạng).");
        }
        if (attachmentRepository.findByRegistration_RegistrationIdInOrderByAttachmentIdAsc(List.of(reg.getRegistrationId())).size() >= MAX_FILES_PER_REGISTRATION) {
            throw new IllegalArgumentException("Mỗi hồ sơ đính kèm tối đa " + MAX_FILES_PER_REGISTRATION + " tệp.");
        }

        RegistrationAttachment att = attachmentRepository.saveAndFlush(RegistrationAttachment.builder()
                .registration(reg)
                .docType(type)
                .fileName(safeFileName(file.getOriginalFilename(), mime))
                .fileUrl("pending")
                .fileSizeKb((int) Math.max(1, (content.length + 1023) / 1024))
                .mimeType(mime)
                .uploadedAt(LocalDateTime.now())
                .hasProblem(false)
                .build());
        att.setFileUrl(fileUrl(att.getAttachmentId()));
        attachmentRepository.saveAndFlush(att);
        attachmentRepository.updateContent(att.getAttachmentId(), content);
        auditTrail.record("UPLOAD_REG_DOC", "Registration_Attachments", null,
                "tệp #" + att.getAttachmentId() + " " + att.getFileName() + " (" + type + ") | hồ sơ " + reg.getRegistrationCode() + " | ngựa " + horse.getHorseName());
        return toDto(att);
    }

    /** Club Manager hoặc chủ ngựa xem danh sách giấy tờ của mọi hồ sơ của ngựa (mới nhất trước) */
    @Transactional(readOnly = true)
    public List<RegistrationAttachmentDTO> list(Integer horseId) {
        Horse horse = findHorse(horseId);
        checkCanView(horse, currentUser());
        return attachmentRepository.findByRegistration_Horse_HorseIdOrderByAttachmentIdDesc(horseId).stream().map(RegistrationAttachmentService::toDto).toList();
    }

    /** Giấy tờ theo từng hồ sơ (dùng cho lịch sử hồ sơ) */
    @Transactional(readOnly = true)
    public Map<Integer, List<RegistrationAttachmentDTO>> byRegistration(Collection<Integer> registrationIds) {
        if (registrationIds.isEmpty()) {
            return Map.of();
        }
        return attachmentRepository.findByRegistration_RegistrationIdInOrderByAttachmentIdAsc(registrationIds).stream()
                .map(RegistrationAttachmentService::toDto)
                .collect(Collectors.groupingBy(RegistrationAttachmentDTO::getRegistrationId, LinkedHashMap::new, Collectors.toList()));
    }

    @Transactional(readOnly = true)
    public FileContent download(Integer attachmentId) {
        RegistrationAttachment att = findAttachment(attachmentId);
        checkCanView(att.getRegistration().getHorse(), currentUser());
        byte[] content = attachmentRepository.findContent(attachmentId);
        if (content == null) {
            throw new ResourceNotFoundException("Tệp #" + attachmentId + " không có nội dung.");
        }
        return new FileContent(att.getFileName(), att.getMimeType() != null ? att.getMimeType() : "application/octet-stream", content);
    }

    /** Chủ ngựa gỡ tệp khi hồ sơ còn chờ duyệt */
    @Transactional
    public void delete(Integer horseId, Integer attachmentId) {
        RegistrationAttachment att = findAttachment(attachmentId);
        HorseRegistration reg = att.getRegistration();
        Horse horse = reg.getHorse();
        if (!horse.getHorseId().equals(horseId)) {
            throw new ResourceNotFoundException("Ngựa #" + horseId + " không có tệp #" + attachmentId);
        }
        if (!horse.getOwner().getUserId().equals(currentUser().getUserId())) {
            throw new AccessDeniedException("Bạn chỉ được gỡ giấy tờ của ngựa mình");
        }
        if (!"PENDING_REVIEW".equals(reg.getStatus())) {
            throw new IllegalArgumentException("Hồ sơ " + reg.getRegistrationCode() + " đã được xử lý (" + reg.getStatus() + "), không gỡ được tệp.");
        }
        attachmentRepository.delete(att);
        auditTrail.record("DELETE_REG_DOC", "Registration_Attachments", "tệp #" + attachmentId + " " + att.getFileName(),
                "đã gỡ | hồ sơ " + reg.getRegistrationCode() + " | ngựa " + horse.getHorseName());
    }

    public static RegistrationAttachmentDTO toDto(RegistrationAttachment a) {
        return RegistrationAttachmentDTO.builder()
                .attachmentId(a.getAttachmentId())
                .registrationId(a.getRegistration().getRegistrationId())
                .registrationCode(a.getRegistration().getRegistrationCode())
                .docType(a.getDocType())
                .fileName(a.getFileName())
                .mimeType(a.getMimeType())
                .fileSizeKb(a.getFileSizeKb())
                .fileUrl(a.getFileUrl())
                .uploadedAt(a.getUploadedAt())
                .build();
    }

    static String fileUrl(Integer attachmentId) {
        return "/api/v1/horses/registration-attachments/" + attachmentId + "/file";
    }

    // PDF: %PDF; PNG: 89 50 4E 47 0D 0A 1A 0A; JPEG: FF D8 FF
    static String detectMime(byte[] b) {
        if (b.length >= 4 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F') return "application/pdf";
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) return "image/png";
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) return "image/jpeg";
        return null;
    }

    private static String safeFileName(String original, String mime) {
        String name = StringUtils.hasText(original) ? StringUtils.getFilename(original.replace('\\', '/')) : null;
        if (!StringUtils.hasText(name)) {
            name = "giay-to" + ("application/pdf".equals(mime) ? ".pdf" : "image/png".equals(mime) ? ".png" : ".jpg");
        }
        name = name.replaceAll("[\\r\\n\"]", "_");
        return name.length() > 255 ? name.substring(name.length() - 255) : name;
    }

    private HorseRegistration pendingRegistration(Integer horseId) {
        HorseRegistration latest = registrationRepository.findFirstByHorse_HorseIdOrderByRegistrationIdDesc(horseId)
                .orElseThrow(() -> new IllegalArgumentException("Ngựa này chưa có hồ sơ đăng ký."));
        if (!"PENDING_REVIEW".equals(latest.getStatus())) {
            throw new IllegalArgumentException("Chỉ đính kèm giấy tờ khi hồ sơ đang chờ duyệt (hiện tại: " + latest.getStatus()
                    + "). Hồ sơ bị từ chối thì nộp lại trước rồi tải giấy tờ lên.");
        }
        return latest;
    }

    private static void checkCanView(Horse horse, User user) {
        if (ROLE_MANAGER.equals(user.getRoleName())) {
            return;
        }
        if (horse.getOwner() == null || !horse.getOwner().getUserId().equals(user.getUserId())) {
            throw new AccessDeniedException("Bạn không được xem giấy tờ của ngựa " + horse.getHorseName());
        }
    }

    private RegistrationAttachment findAttachment(Integer id) {
        return attachmentRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy tệp đính kèm với ID: " + id));
    }

    private Horse findHorse(Integer id) {
        return horseRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy ngựa với ID: " + id));
    }

    private User currentUser() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Không tìm thấy người dùng: " + username));
    }
}
