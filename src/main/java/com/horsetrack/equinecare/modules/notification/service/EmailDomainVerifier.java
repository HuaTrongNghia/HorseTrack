package com.horsetrack.equinecare.modules.notification.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.naming.NameNotFoundException;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import java.util.Hashtable;

/**
 * Kiểm tra nhanh email có thể nhận thư: tên miền phải có bản ghi MX (hoặc A) trên DNS.
 * Chặn được email gõ sai tên miền (gmail.con, abc.xyz...). Không xác định được hộp thư có tồn tại trên tên miền thật
 * (ví dụ sai tên trước @gmail.com) - trường hợp đó người dùng không nhận được OTP nên không đăng ký được.
 */
@Slf4j
@Component
public class EmailDomainVerifier {

    private final boolean enabled;

    public EmailDomainVerifier(@Value("${app.mail.verify-domain:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /** Ném IllegalArgumentException khi tên miền chắc chắn không nhận thư; lỗi mạng / DNS thì cho qua. */
    public void checkDeliverable(String email) {
        if (!enabled || email == null || !email.contains("@")) {
            return;
        }
        String domain = email.substring(email.lastIndexOf('@') + 1).trim().toLowerCase();
        try {
            if (!hasRecord(domain, "MX") && !hasRecord(domain, "A")) {
                throw notDeliverable(email, domain);
            }
        } catch (NameNotFoundException ex) {
            throw notDeliverable(email, domain);
        } catch (NamingException ex) {
            log.warn("Không kiểm tra được tên miền email {} ({}), vẫn cho gửi OTP", domain, ex.getMessage());
        }
    }

    private static boolean hasRecord(String domain, String type) throws NamingException {
        Hashtable<String, String> env = new Hashtable<>();
        env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
        env.put("com.sun.jndi.dns.timeout.initial", "2000");
        env.put("com.sun.jndi.dns.timeout.retries", "1");
        InitialDirContext ctx = new InitialDirContext(env);
        try {
            Attributes attrs = ctx.getAttributes(domain, new String[]{type});
            Attribute attr = attrs.get(type);
            return attr != null && attr.size() > 0;
        } finally {
            ctx.close();
        }
    }

    private static IllegalArgumentException notDeliverable(String email, String domain) {
        return new IllegalArgumentException("Email " + email + " không có thật: tên miền '" + domain + "' không nhận thư. Vui lòng kiểm tra lại địa chỉ email.");
    }
}
