package com.horsetrack.equinecare.modules.notification.service;

import com.horsetrack.equinecare.modules.auth.service.StaffAccountService;
import jakarta.mail.Flags;
import jakarta.mail.Folder;
import jakarta.mail.Message;
import jakarta.mail.Multipart;
import jakarta.mail.Part;
import jakarta.mail.Session;
import jakarta.mail.Store;
import jakarta.mail.internet.InternetAddress;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Gmail nhận thư rồi mới trả về khi địa chỉ không tồn tại. Thư trả về trước đây nằm trong hộp thư hệ thống.
 * Vòng này đọc thư trả về chưa đọc, ghi Nhật ký email (FAILED), bật chuông quản lý, rồi xóa thư khỏi hộp thư hệ thống.
 */
@Slf4j
@Component
public class EmailBounceWatcher {

    private static final Pattern NAMED_RECIPIENT = Pattern.compile(
            "(?i)(?:Final-Recipient|Original-Recipient)\\s*:\\s*[^;\\n]*;\\s*<?([A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,})>?");
    private static final Pattern ANY_EMAIL = Pattern.compile("(?i)[A-Z0-9._%+\\-]+@[A-Z0-9.\\-]+\\.[A-Z]{2,}");

    private final StaffAccountService staffAccountService;
    private final String username;
    private final String password;
    private final String fromAddress;

    public EmailBounceWatcher(StaffAccountService staffAccountService,
                              @Value("${spring.mail.username:}") String username,
                              @Value("${spring.mail.password:}") String password,
                              @Value("${app.mail.from:}") String fromAddress) {
        this.staffAccountService = staffAccountService;
        this.username = username;
        this.password = password;
        this.fromAddress = StringUtils.hasText(fromAddress) ? fromAddress : username;
    }

    @Scheduled(fixedDelayString = "${app.mail.bounce-check-ms:180000}", initialDelayString = "${app.mail.bounce-check-ms:180000}")
    public void poll() {
        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            return;
        }
        Properties props = new Properties();
        props.put("mail.store.protocol", "imaps");
        props.put("mail.imaps.connectiontimeout", "8000");
        props.put("mail.imaps.timeout", "15000");
        props.put("mail.imaps.ssl.enable", "true");
        try {
            Session session = Session.getInstance(props);
            try (Store store = session.getStore("imaps")) {
                store.connect("imap.gmail.com", username, password);
                Folder inbox = store.getFolder("INBOX");
                inbox.open(Folder.READ_WRITE);
                boolean delete = false;
                try {
                    int count = inbox.getMessageCount();
                    if (count > 0) {
                        int start = Math.max(1, count - 49);
                        for (Message message : inbox.getMessages(start, count)) {
                            if (message.isSet(Flags.Flag.SEEN) || !isBounce(message)) {
                                continue;
                            }
                            String body = dump(message, 0);
                            String subject = message.getSubject() == null ? "Thư trả về" : message.getSubject();
                            List<String> recipients = recipients(body);
                            if (recipients.isEmpty()) {
                                message.setFlag(Flags.Flag.SEEN, true);
                                continue;
                            }
                            boolean staff = staffMail(subject, body);
                            for (String email : recipients) {
                                staffAccountService.recordBouncedStaffEmail(email, subject,
                                        "Máy chủ thư trả lại: địa chỉ không tồn tại hoặc không nhận thư.", staff);
                            }
                            message.setFlag(Flags.Flag.DELETED, true);
                            delete = true;
                        }
                    }
                } finally {
                    inbox.close(delete);
                }
            }
        } catch (Exception ex) {
            log.warn("Không đọc được thư trả về trong hộp thư hệ thống: {}", ex.getMessage());
        }
    }

    private boolean isBounce(Message message) throws Exception {
        String from = "";
        if (message.getFrom() != null) {
            StringBuilder sb = new StringBuilder();
            for (var address : message.getFrom()) {
                sb.append(address instanceof InternetAddress internet ? internet.getAddress() : address.toString()).append(' ');
            }
            from = sb.toString().toLowerCase();
        }
        String subject = message.getSubject() == null ? "" : message.getSubject().toLowerCase();
        return from.contains("mailer-daemon") || from.contains("postmaster")
                || subject.contains("delivery status") || subject.contains("undeliver")
                || subject.contains("delivery incomplete") || subject.contains("returned mail")
                || subject.contains("mail delivery failed");
    }

    private List<String> recipients(String body) {
        Set<String> found = new LinkedHashSet<>();
        Matcher named = NAMED_RECIPIENT.matcher(body == null ? "" : body);
        while (named.find()) {
            addIfExternal(found, named.group(1));
        }
        if (found.isEmpty() && body != null) {
            Matcher any = ANY_EMAIL.matcher(body);
            while (any.find() && found.size() < 3) {
                addIfExternal(found, any.group());
            }
        }
        return new ArrayList<>(found);
    }

    private void addIfExternal(Set<String> found, String email) {
        if (!StringUtils.hasText(email)) {
            return;
        }
        String normalized = email.trim().toLowerCase();
        if (normalized.contains("mailer-daemon") || normalized.contains("postmaster")) {
            return;
        }
        if (StringUtils.hasText(fromAddress) && normalized.equalsIgnoreCase(fromAddress.trim())) {
            return;
        }
        if (StringUtils.hasText(username) && normalized.equalsIgnoreCase(username.trim())) {
            return;
        }
        found.add(normalized);
    }

    private static boolean staffMail(String subject, String body) {
        String text = ((subject == null ? "" : subject) + " " + (body == null ? "" : body)).toLowerCase();
        return text.contains("tài khoản nhân viên") || text.contains("xác nhận tài khoản");
    }

    private static String dump(Part part, int depth) throws Exception {
        if (depth > 4) {
            return "";
        }
        if (part.isMimeType("text/*") || part.isMimeType("message/delivery-status")) {
            Object content = part.getContent();
            String text = content == null ? "" : content.toString();
            return text.length() > 20000 ? text.substring(0, 20000) : text;
        }
        if (part.isMimeType("multipart/*")) {
            Object content = part.getContent();
            if (!(content instanceof Multipart multipart)) {
                return "";
            }
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < multipart.getCount() && sb.length() < 20000; i++) {
                sb.append(dump(multipart.getBodyPart(i), depth + 1)).append('\n');
            }
            return sb.toString();
        }
        return "";
    }
}
