package com.example.employeeLeaveApplication.controller;

import com.example.employeeLeaveApplication.dto.LeaveApplicationDTO;
import com.example.employeeLeaveApplication.dto.LeaveResponse;
import com.example.employeeLeaveApplication.entity.LeaveApplication;
import com.example.employeeLeaveApplication.entity.LeaveAttachment;
import com.example.employeeLeaveApplication.enums.HalfDayType;
import com.example.employeeLeaveApplication.enums.LeaveStatus;
import com.example.employeeLeaveApplication.enums.LeaveType;
import com.example.employeeLeaveApplication.service.LeaveAllocationService;
import com.example.employeeLeaveApplication.service.LeaveApplicationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.net.InetAddress;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/leaves")
public class LeaveApplicationController {

    private final LeaveApplicationService leaveApplicationService;
    private final LeaveAllocationService leaveAllocationService;

    public LeaveApplicationController(
            LeaveApplicationService leaveApplicationService,
            LeaveAllocationService leaveAllocationService) {

        this.leaveApplicationService = leaveApplicationService;
        this.leaveAllocationService = leaveAllocationService;
    }

    @Value("${file.upload-dir:uploads/leaves}")
    private String uploadDir;

    // ========================
    // APPLY LEAVE
    // ========================
    @PostMapping(value = "/apply", consumes = "multipart/form-data")
    public LeaveResponse applyLeave(
            @RequestParam Long employeeId,
            @RequestParam String leaveType,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String reason,
            @RequestParam(required = false) String halfDayType,
            @RequestParam(defaultValue = "false") boolean confirmLossOfPay,
            @RequestParam(required = false) MultipartFile[] files,
            HttpServletRequest request
    ) throws IOException {

        LeaveType type;
        try {
            type = LeaveType.valueOf(leaveType.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid leave type");
        }

        LeaveApplication leave = new LeaveApplication();
        leave.setEmployeeId(employeeId);
        leave.setLeaveType(type);
        leave.setStartDate(startDate);
        leave.setEndDate(endDate);
        leave.setReason(reason);
        leave.setStatus(LeaveStatus.PENDING);

        if (halfDayType != null && !halfDayType.isEmpty()) {
            leave.setHalfDayType(HalfDayType.valueOf(halfDayType.toUpperCase()));
        }

        // Process attachments
        if (files != null && files.length > 0) {

            Path uploadPath = Paths.get(uploadDir);
            Files.createDirectories(uploadPath);

            String hostname = InetAddress.getLocalHost().getHostName().toLowerCase();
            if (!hostname.endsWith(".local")) hostname += ".local";
            int port = request.getServerPort();

            List<LeaveAttachment> attachments = new ArrayList<>();

            for (MultipartFile file : files) {

                if (file.isEmpty()) continue;

                String uniqueName = UUID.randomUUID() + "_" + file.getOriginalFilename();
                Files.write(uploadPath.resolve(uniqueName), file.getBytes());

                String encodedName =
                        URLEncoder.encode(uniqueName, StandardCharsets.UTF_8)
                                .replace("+", "%20");

                String fullUrl = String.format(
                        "http://%s:%d/api/files/download/%s",
                        hostname,
                        port,
                        encodedName
                );

                LeaveAttachment attachment = new LeaveAttachment();
                attachment.setFileUrl(fullUrl);
                attachment.setLeaveApplication(leave);

                attachments.add(attachment);
            }

            leave.setAttachments(attachments);
        }

        // 🔥 Just call service and return DTO-based response
        return leaveApplicationService.applyLeave(leave, confirmLossOfPay);
    }

    // ========================
    // GET LEAVES FOR EMPLOYEE
    // ========================
    @GetMapping("/employee/{employeeId}")
    public List<LeaveApplicationDTO> getEmployeeLeaves(
            @PathVariable Long employeeId) {

        return leaveApplicationService.getLeavesByEmployee(employeeId);
    }

    // ========================
    // CANCEL LEAVE (EMPLOYEE)
    // ========================
    @PostMapping("/cancel/employee")
    public LeaveResponse cancelEmployeeLeave(
            @RequestParam Long applicationId,
            @RequestParam Long employeeId
    ) {
        return leaveApplicationService.cancelEmployeeLeave(applicationId, employeeId);
    }

    // ========================
    // CANCEL LEAVE (ADMIN)
    // ========================
    @PostMapping("/cancel/admin")
    public LeaveResponse cancelAdminLeave(
            @RequestParam Long applicationId) {

        return leaveApplicationService.cancelAdminLeave(applicationId);
    }

    @GetMapping("/all")
    public List<LeaveApplicationDTO> getAllLeaves() {
        return leaveApplicationService.getAllLeaves();
    }
    @GetMapping("/{employeeId}")
    public List<LeaveApplicationDTO> getLeavesByEmployee(
            @PathVariable Long employeeId) {
        return leaveApplicationService.getLeavesByEmployee(employeeId);
    }

}
