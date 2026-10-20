package com.horsetrack.equinecare.modules.auth.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    // Tên attribute lưu lý do token lỗi để JwtAuthenticationEntryPoint trả về cho client
    public static final String JWT_ERROR_ATTRIBUTE = "jwt_error";

    private final JwtTokenProvider jwtTokenProvider;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt)) {
                Claims claims = jwtTokenProvider.getClaims(jwt);

                // Token đã đăng xuất thì không xác thực, để EntryPoint trả về 401
                if (tokenBlacklistService.isRevoked(claims.getId())) {
                    request.setAttribute(JWT_ERROR_ATTRIBUTE, "Token đã bị thu hồi do đăng xuất, vui lòng đăng nhập lại");
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = claims.getSubject();
                String role = claims.get("role", String.class);

                // Cấp quyền theo role đã được đóng gói trong Claim JWT
                SimpleGrantedAuthority authority = new SimpleGrantedAuthority(role != null ? role : "ROLE_USER");
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(username, null, Collections.singletonList(authority));
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        } catch (ExpiredJwtException ex) {
            log.warn("JWT Token đã hết hạn: {}", ex.getMessage());
            request.setAttribute(JWT_ERROR_ATTRIBUTE, "Token đã hết hạn, vui lòng đăng nhập lại");
        } catch (JwtException | IllegalArgumentException ex) {
            log.warn("JWT Token không hợp lệ: {}", ex.getMessage());
            request.setAttribute(JWT_ERROR_ATTRIBUTE, "Token không hợp lệ");
        } catch (Exception ex) {
            log.error("Không thể thiết lập xác thực người dùng từ JWT Token: {}", ex.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Lấy JWT token từ Authorization header: "Bearer <token>"
     */
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
