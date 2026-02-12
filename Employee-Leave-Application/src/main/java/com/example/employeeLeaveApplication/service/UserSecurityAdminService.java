package com.example.employeeLeaveApplication.service;

import com.example.employeeLeaveApplication.entity.UserSecurityAdmin;
import com.example.employeeLeaveApplication.repository.UserSecurityAdminRepository;
import org.springframework.stereotype.Service;

@Service
public class UserSecurityAdminService {

    private final UserSecurityAdminRepository repository;

    public UserSecurityAdminService(UserSecurityAdminRepository repository) {
        this.repository = repository;
    }

    public UserSecurityAdmin save(UserSecurityAdmin userSecurityAdmin) {
        return repository.save(userSecurityAdmin);
    }
}
