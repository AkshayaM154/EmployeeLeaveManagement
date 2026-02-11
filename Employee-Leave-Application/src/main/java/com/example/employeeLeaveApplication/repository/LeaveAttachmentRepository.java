package com.example.employeeLeaveApplication.repository;

import com.example.employeeLeaveApplication.entity.LeaveAttachment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LeaveAttachmentRepository extends JpaRepository<LeaveAttachment, Long> {

    // Fetch all attachments for a specific leave application
    List<LeaveAttachment> findByLeaveApplication_Id(Long leaveApplicationId);

}
