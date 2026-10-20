package com.horsetrack.equinecare.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPageResponseDTO {
    private List<UserResponseDTO> users;
    private int currentPage;
    private int totalPages;
    private long totalElements;
}
