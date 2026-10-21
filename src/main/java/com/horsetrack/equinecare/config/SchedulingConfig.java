package com.horsetrack.equinecare.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Bật các tác vụ chạy định kỳ (VD: SLA Monitor cảnh báo việc chăm sóc trễ hạn - BR_05).
 * Tắt bằng app.scheduling.enabled=false (VD: khi chạy test).
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
