package com.horsetrack.equinecare.modules.horse.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/** Giấy tờ chủ ngựa đính kèm hồ sơ đăng ký ngựa (Club Manager xem khi duyệt) */
@Entity
@Table(name = "Registration_Attachments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegistrationAttachment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "attachment_id")
    private Integer attachmentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registration_id", nullable = false)
    private HorseRegistration registration;

    // OWNERSHIP (giấy tờ sở hữu) hoặc OTHER
    @Column(name = "doc_type", nullable = false, length = 20)
    private String docType;

    @Column(name = "file_name", nullable = false, length = 255)
    private String fileName;

    // Đường dẫn API tải tệp
    @Column(name = "file_url", nullable = false, length = 500)
    private String fileUrl;

    @Column(name = "file_size_kb")
    private Integer fileSizeKb;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "uploaded_at", nullable = false)
    private LocalDateTime uploadedAt;

    @Column(name = "has_problem", nullable = false)
    private Boolean hasProblem;

    @Column(name = "problem_note", length = 255)
    private String problemNote;

    // Nội dung tệp (cột file_content) đọc / ghi bằng truy vấn riêng trong RegistrationAttachmentRepository để không nạp khi liệt kê
}
