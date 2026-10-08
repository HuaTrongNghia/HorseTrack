package com.horsetrack.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable) // Tắt bảo vệ CSRF để có thể test API từ bên ngoài
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/accounts/**", "/index.html").permitAll() // Mở cửa tự do cho API accounts và file giao diện
                        .anyRequest().authenticated() // Các API khác sau này sẽ yêu cầu đăng nhập
                );
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(); // Khai báo máy xay mật khẩu BCrypt ở đây
    }
}