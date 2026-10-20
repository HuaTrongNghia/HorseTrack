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
 * Quy tắc tên đăng nhập khi tạo tài khoản mới:
 * 4–30 ký tự, chỉ gồm chữ không dấu (a–z, A–Z), chữ số, dấu chấm và gạch dưới.
 * Không áp dụng cho đăng nhập để các tài khoản cũ (tạo trước khi có quy tắc) vẫn đăng nhập được.
 * Dùng kèm @NotBlank để có thông báo riêng khi bỏ trống.
 */
@Documented
@Constraint(validatedBy = {})
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@ReportAsSingleViolation
@Pattern(regexp = "^\\s*$|^[A-Za-z0-9._]{4,30}$") // chuỗi rỗng/toàn khoảng trắng để @NotBlank báo
public @interface ValidUsername {

    String message() default "Tên đăng nhập phải dài 4–30 ký tự, chỉ gồm chữ không dấu, chữ số, dấu chấm (.) và gạch dưới (_)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
