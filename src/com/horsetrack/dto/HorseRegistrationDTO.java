package com.horsetrack.dto;

import lombok.Data;

@Data
public class HorseRegistrationDTO {
    private String ownerId; // Mã chủ ngựa
    private String horseName; // Tên ngựa
    private String breed; // Giống ngựa
    private Integer age; // Tuổi
    private String gender; // Giới tính
    private String originCertificate; // Giấy tờ nguồn gốc (Link file đính kèm)
}