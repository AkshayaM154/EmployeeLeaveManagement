package com.example.employeeLeaveApplication.controller;

import com.example.employeeLeaveApplication.entity.UserSecurityAdmin;
import com.example.employeeLeaveApplication.service.UserSecurityAdminService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/security")
public class UserSecurityAdminController {

    private final UserSecurityAdminService service;

    public UserSecurityAdminController(UserSecurityAdminService service) {
        this.service = service;
    }

    @PostMapping("/save")
    public ResponseEntity<String> updateSecurity(@RequestBody UserSecurityAdmin security) {

        // 🔹 Check if record exists for this userId
        UserSecurityAdmin existing = service.findByUserId(security.getUserId());

        if (existing == null) {
            // 🔹 Stop gracefully if user does not exist
            return ResponseEntity.ok(
                    "No record found for userId " + security.getUserId() + ". Update skipped."
            );
        }

        // 🔹 Update existing record
        existing.setVpnEnabled(security.getVpnEnabled());
        existing.setBiometricRegistered(security.getBiometricRegistered());
        service.save(existing);

        return ResponseEntity.ok("Admin security details updated successfully");
    }
}
