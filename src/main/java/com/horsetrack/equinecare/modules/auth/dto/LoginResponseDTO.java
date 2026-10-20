package com.horsetrack.equinecare.modules.auth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Kết quả trả về khi đăng nhập thành công.
 * expiresIn: số giây còn lại trước khi accessToken hết hạn.
 * refreshExpiresIn: số giây còn lại trước khi refreshToken hết hạn.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponseDTO {
    private String accessToken;
    @Builder.Default
    private String tokenType = "Bearer";
    private long expiresIn;
    private String refreshToken;
    private long refreshExpiresIn;
    private String role;
    private Integer userId;
}
