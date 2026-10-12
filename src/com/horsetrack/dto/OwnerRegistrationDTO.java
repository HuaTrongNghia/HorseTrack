package com.horsetrack.dto;
import lombok.Data;

@Data
public class OwnerRegistrationDTO {
    private String username;
    private String fullName;
    private String email;
    private String phone;
    private String password;
    private String confirmPassword;
    private String billingAddress;
    private String taxCode;
    private boolean agreeToTerms;
}