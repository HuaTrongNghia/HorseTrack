package com.horsetrack.equinecare.modules.auth.controller;

import com.horsetrack.equinecare.common.base.ApiResponse;
import com.horsetrack.equinecare.modules.auth.dto.ChangePasswordRequestDTO;
import com.horsetrack.equinecare.modules.auth.dto.UserProfileResponseDTO;
import com.horsetrack.equinecare.modules.auth.service.AccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/account")
@RequiredArgsConstructor
public class AccountController {

    private final AccountService accountService;

    @GetMapping("/my-profile")
    public ResponseEntity<ApiResponse<UserProfileResponseDTO>> getMyProfile() {
        UserProfileResponseDTO profile = accountService.getMyProfile();
        return ResponseEntity.ok(ApiResponse.ok("Fetched profile successfully", profile));
    }

    @PutMapping("/change-password")
    public ResponseEntity<ApiResponse<Void>> changePassword(@Valid @RequestBody ChangePasswordRequestDTO request) {
        accountService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully", null));
    }
}
