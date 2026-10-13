package com.horsetrack.equinecare.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME_NAME = "bearerAuth";

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("HorseTrack API Documentation")
                        .description("Tài liệu API Hệ thống Quản lý và Chăm sóc Huấn luyện Ngựa đua - EquineCare")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("HorseTrack Development Team")
                                .email("support@horsetrack.com"))
                        .license(new License().name("Apache 2.0").url("https://springdoc.org")))
                // Thiết lập Security Requirement toàn cục: hiển thị nút Authorize và khóa trên các API
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME_NAME))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME, new SecurityScheme()
                                .name(SECURITY_SCHEME_NAME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("Dán chuỗi JWT Token của bạn vào đây (chỉ cần dán mã token, Swagger sẽ tự động đính kèm header 'Authorization: Bearer <token>').")));
    }
}
