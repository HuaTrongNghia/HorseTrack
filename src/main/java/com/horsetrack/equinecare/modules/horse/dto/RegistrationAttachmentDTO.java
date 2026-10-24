package com.horsetrack.equinecare.modules.horse.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationAttachmentDTO {
    private Integer attachmentId;
    private Integer registrationId;
    private String registrationCode;
    /** OWNERSHIP (giấy tờ sở hữu) hoặc OTHER */
    private String docType;
    private String fileName;
    private String mimeType;
    private Integer fileSizeKb;
    /** GET đường dẫn này (kèm token) để xem / tải tệp */
    private String fileUrl;
    private LocalDateTime uploadedAt;
}
