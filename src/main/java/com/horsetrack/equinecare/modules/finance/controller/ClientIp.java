package com.horsetrack.equinecare.modules.finance.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/** Địa chỉ IP người dùng gửi cho VNPay (vnp_IpAddr) */
final class ClientIp {

    private ClientIp() {
    }

    static String of(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(ip)) {
            ip = ip.split(",")[0].trim();
        } else {
            ip = request.getRemoteAddr();
        }
        // IPv6 localhost → IPv4 cho VNPay
        if (ip == null || "0:0:0:0:0:0:0:1".equals(ip) || "::1".equals(ip)) {
            return "127.0.0.1";
        }
        return ip;
    }
}
