package com.horsetrack.equinecare.common.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import jakarta.validation.ReportAsSingleViolation;
import jakarta.validation.constraints.Pattern;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Quy tắc mật khẩu dùng chung (đăng ký, tạo nhân viên, đổi mật khẩu, đặt lại mật khẩu):
 * 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số, không chứa khoảng trắng.
 * Tối đa 64 để không vượt giới hạn 72 byte của BCrypt.
 * Dùng kèm @NotBlank để có thông báo riêng khi bỏ trống (null được coi là hợp lệ ở đây).
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Pattern(regexp = "^\\s*$|^(?=.*\\p{L})(?=.*\\d)\\S{8,64}$") // chuỗi rỗng/toàn khoảng trắng để @NotBlank báo
public @interface StrongPassword {

    String message() default "Mật khẩu phải dài 8–64 ký tự, có ít nhất 1 chữ cái và 1 chữ số, không chứa khoảng trắng";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
