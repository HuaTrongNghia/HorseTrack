package com.horsetrack.equinecare.modules.horse.repository;

import com.horsetrack.equinecare.modules.horse.entity.RegistrationAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RegistrationAttachmentRepository extends JpaRepository<RegistrationAttachment, Integer> {

    List<RegistrationAttachment> findByRegistration_RegistrationIdInOrderByAttachmentIdAsc(Collection<Integer> registrationIds);

    List<RegistrationAttachment> findByRegistration_Horse_HorseIdOrderByAttachmentIdDesc(Integer horseId);

    // Nội dung tệp đọc / ghi riêng, không map vào entity để danh sách không nạp tệp
    @Query(value = "SELECT file_content FROM Registration_Attachments WHERE attachment_id = :id", nativeQuery = true)
    byte[] findContent(@Param("id") Integer attachmentId);

    @Modifying
    @Query(value = "UPDATE Registration_Attachments SET file_content = :content WHERE attachment_id = :id", nativeQuery = true)
    int updateContent(@Param("id") Integer attachmentId, @Param("content") byte[] content);
}
