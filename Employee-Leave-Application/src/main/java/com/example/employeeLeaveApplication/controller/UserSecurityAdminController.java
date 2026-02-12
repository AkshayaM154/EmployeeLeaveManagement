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
    public ResponseEntity<String> saveSecurity(@RequestBody UserSecurityAdmin security) {
        service.save(security);
        return ResponseEntity.ok("Admin security details saved successfully");
    }
}
