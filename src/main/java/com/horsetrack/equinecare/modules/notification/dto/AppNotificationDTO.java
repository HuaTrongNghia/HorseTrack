package com.horsetrack.equinecare.modules.notification.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppNotificationDTO {
    Long id;
    String purpose;
    String title;
    String message;
    String link;
    boolean read;
    String createdAt;
}
