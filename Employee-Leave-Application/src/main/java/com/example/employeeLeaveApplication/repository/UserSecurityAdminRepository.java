package com.example.employeeLeaveApplication.repository;

import com.example.employeeLeaveApplication.entity.UserSecurityAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserSecurityAdminRepository extends JpaRepository<UserSecurityAdmin, Long> {
}
