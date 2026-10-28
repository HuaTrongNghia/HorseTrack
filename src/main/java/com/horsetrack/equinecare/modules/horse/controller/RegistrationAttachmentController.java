package com.horsetrack.equinecare.modules.horse.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.horse.dto.RegistrationAttachmentDTO;
import com.horsetrack.equinecare.modules.horse.service.RegistrationAttachmentService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;

/** Giấy tờ chủ ngựa đính kèm hồ sơ đăng ký ngựa */
@RestController
@RequestMapping("/api/v1/horses")
@RequiredArgsConstructor
public class RegistrationAttachmentController {

    private final RegistrationAttachmentService attachmentService;

    // Chủ ngựa tải giấy tờ (PDF / JPG / PNG, tối đa 10 MB) lên hồ sơ đang chờ duyệt
    @PostMapping(value = "/{horseId}/registration-attachments", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Đính kèm giấy tờ hồ sơ", description = "PDF, JPG hoặc PNG, tối đa 10 MB. Chủ ngựa tải lên hồ sơ đang chờ duyệt.")
    public ResponseEntity<ApiResponse<RegistrationAttachmentDTO>> upload(@PathVariable Integer horseId,
                                                                         @RequestParam("file") MultipartFile file,
                                                                         @RequestParam(value = "docType", required = false) String docType) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Đã đính kèm giấy tờ", attachmentService.upload(horseId, file, docType)));
    }

    @GetMapping("/{horseId}/registration-attachments")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Horse Owner')")
    @Operation(summary = "Danh sách giấy tờ hồ sơ")
    public ResponseEntity<ApiResponse<List<RegistrationAttachmentDTO>>> list(@PathVariable Integer horseId) {
        return ResponseEntity.ok(ApiResponse.ok("Lấy danh sách giấy tờ thành công", attachmentService.list(horseId)));
    }

    // Xem / tải tệp: download=true để tải về, mặc định mở xem trên trình duyệt
    @GetMapping("/registration-attachments/{attachmentId}/file")
    @PreAuthorize("hasAnyAuthority('Club Manager', 'Horse Owner')")
    @Operation(summary = "Xem hoặc tải giấy tờ", description = "download=true để tải về. Mặc định mở xem trên trình duyệt.")
    public ResponseEntity<byte[]> file(@PathVariable Integer attachmentId, @RequestParam(defaultValue = "false") boolean download) {
        RegistrationAttachmentService.FileContent f = attachmentService.download(attachmentId);
        ContentDisposition disposition = (download ? ContentDisposition.attachment() : ContentDisposition.inline())
                .filename(f.fileName(), StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(f.mimeType()))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(f.content());
    }

    @DeleteMapping("/{horseId}/registration-attachments/{attachmentId}")
    @PreAuthorize("hasAuthority('Horse Owner')")
    @Operation(summary = "Gỡ giấy tờ hồ sơ")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Integer horseId, @PathVariable Integer attachmentId) {
        attachmentService.delete(horseId, attachmentId);
        return ResponseEntity.ok(ApiResponse.ok("Đã gỡ giấy tờ", null));
    }
}
