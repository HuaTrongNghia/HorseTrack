package com.horsetrack.equinecare.config;

import com.horsetrack.equinecare.modules.auth.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    // Khởi tạo Bean băm mật khẩu
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    // Cấu hình phân quyền các luồng API
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // 1. Mở khóa Swagger UI và OpenAPI không bị chặn HTTP 403
                        .requestMatchers(
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/v3/api-docs",
                                "/swagger-resources/**",
                                "/swagger-resources",
                                "/configuration/ui",
                                "/configuration/security",
                                "/webjars/**",
                                "/error"
                        ).permitAll()

                        // 2. Cấp quyền cho API Xác thực
                        .requestMatchers("/api/v1/auth/register-owner", "/api/v1/auth/login").permitAll()
                        .requestMatchers("/api/v1/auth/staff/**").hasAuthority("Club Manager")

                        // 3. Mở quyền cho các API khác trong giai đoạn kiểm thử nếu cần hoặc yêu cầu xác thực
                        .anyRequest().authenticated()
                )
                // Bổ sung JWT Authentication Filter để nhận và giải mã Bearer Token gửi từ Swagger hoặc Postman
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }
}
