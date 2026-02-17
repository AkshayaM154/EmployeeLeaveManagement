package com.example.employeeLeaveApplication.service;

import com.example.employeeLeaveApplication.component.HolidayChecker;
import com.example.employeeLeaveApplication.dto.LeaveApplicationDTO;
import com.example.employeeLeaveApplication.dto.LeaveResponse;
import com.example.employeeLeaveApplication.entity.CompOff;
import com.example.employeeLeaveApplication.entity.Employee;
import com.example.employeeLeaveApplication.entity.LeaveApplication;
import com.example.employeeLeaveApplication.enums.*;
import com.example.employeeLeaveApplication.repository.CompOffRepository;
import com.example.employeeLeaveApplication.repository.EmployeeRepository;
import com.example.employeeLeaveApplication.repository.LeaveApplicationRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LeaveApplicationService {

    private final LeaveApplicationRepository leaveApplicationRepository;
    private final NotificationService notificationService;
    private final EmployeeRepository employeeRepository;
    private final HolidayChecker holidayChecker;
    private final CompOffService compOffService;
    private final CompOffRepository compOffRepository;

    public LeaveApplicationService(
            LeaveApplicationRepository leaveApplicationRepository,
            NotificationService notificationService,
            EmployeeRepository employeeRepository,
            HolidayChecker holidayChecker,
            CompOffService compOffService,
            CompOffRepository compOffRepository
    ) {
        this.leaveApplicationRepository = leaveApplicationRepository;
        this.notificationService = notificationService;
        this.employeeRepository = employeeRepository;
        this.holidayChecker = holidayChecker;
        this.compOffService = compOffService;
        this.compOffRepository = compOffRepository;
    }

    // =========================================================
    // APPLY LEAVE
    // =========================================================
    @Transactional
    public LeaveResponse applyLeave(LeaveApplication leave, boolean confirmLossOfPay) {

        Employee employee = employeeRepository.findById(leave.getEmployeeId()).orElse(null);
        if (employee == null) {
            return new LeaveResponse(null,
                    "Employee with ID " + leave.getEmployeeId() + " does not exist.");
        }

        if (leave.getEndDate().isBefore(leave.getStartDate())) {
            return new LeaveResponse(null, "End date cannot be before start date");
        }

        List<LeaveApplication> overlaps =
                leaveApplicationRepository.findOverlappingLeaves(
                        leave.getEmployeeId(),
                        leave.getStartDate(),
                        leave.getEndDate(),
                        LeaveStatus.PENDING,
                        LeaveStatus.APPROVED
                );

        if (!overlaps.isEmpty()) {
            return new LeaveResponse(null, "Leave dates overlap with an existing leave");
        }

        BigDecimal calculatedDays;
        try {
            calculatedDays = calculateLeaveDuration(leave);
        } catch (Exception e) {
            return new LeaveResponse(null, e.getMessage());
        }

        String warning = checkBalanceAndGetWarning(leave, calculatedDays);
        if (warning != null && !confirmLossOfPay) {
            return new LeaveResponse(null, warning);
        }

        processAttachments(leave);

        leave.setDays(calculatedDays);
        leave.setStatus(LeaveStatus.PENDING);

        LeaveApplication savedLeave = leaveApplicationRepository.save(leave);

        if (leave.getLeaveType() == LeaveType.COMP_OFF && warning == null) {
            compOffService.useCompOff(
                    leave.getEmployeeId(),
                    calculatedDays,
                    savedLeave.getId()
            );
        }

        notifyManager(savedLeave);

        return new LeaveResponse(mapToDTO(savedLeave), warning);
    }

    // =========================================================
    // CANCEL LEAVES
    // =========================================================
    @Transactional
    public LeaveResponse cancelAdminLeave(Long applicationId) {

        LeaveApplication leave =
                leaveApplicationRepository.findById(applicationId).orElse(null);

        if (leave == null)
            return new LeaveResponse(null, "Leave not found");

        performCancellation(leave);

        return new LeaveResponse(mapToDTO(leave), "Leave cancelled successfully");
    }

    @Transactional
    public LeaveResponse cancelEmployeeLeave(Long applicationId, Long employeeId) {

        LeaveApplication leave =
                leaveApplicationRepository.findById(applicationId).orElse(null);

        if (leave == null)
            return new LeaveResponse(null, "Leave not found");

        if (!leave.getEmployeeId().equals(employeeId))
            return new LeaveResponse(null, "Unauthorized cancellation");

        if (leave.getStatus() == LeaveStatus.REJECTED ||
                leave.getStatus() == LeaveStatus.CANCELLED)
            return new LeaveResponse(null, "Leave already finalized");

        if (leave.getStatus() == LeaveStatus.APPROVED)
            return new LeaveResponse(null, "Approved leaves cannot be cancelled");

        performCancellation(leave);

        return new LeaveResponse(mapToDTO(leave), "Leave cancelled successfully");
    }

    private void performCancellation(LeaveApplication leave) {

        if (leave.getLeaveType() == LeaveType.COMP_OFF &&
                (leave.getStatus() == LeaveStatus.APPROVED ||
                        leave.getStatus() == LeaveStatus.PENDING)) {

            List<CompOff> credits =
                    compOffRepository.findByUsedLeaveApplicationId(leave.getId());

            for (CompOff credit : credits) {
                credit.setStatus(CompOffStatus.EARNED);
                credit.setUsedLeaveApplicationId(null);
                compOffRepository.save(credit);
            }
        }

        leave.setStatus(LeaveStatus.CANCELLED);
        leaveApplicationRepository.save(leave);
    }

    // =========================================================
    // DTO MAPPER
    // =========================================================
    private LeaveApplicationDTO mapToDTO(LeaveApplication leave) {

        LeaveApplicationDTO dto = new LeaveApplicationDTO();

        dto.setId(leave.getId());
        dto.setEmployeeId(leave.getEmployeeId());
        dto.setLeaveType(leave.getLeaveType());
        dto.setStartDate(leave.getStartDate());
        dto.setEndDate(leave.getEndDate());
        dto.setDays(leave.getDays());
        dto.setStatus(leave.getStatus());
        dto.setReason(leave.getReason());

        if (leave.getAttachments() != null) {
            dto.setAttachmentUrls(
                    leave.getAttachments()
                            .stream()
                            .map(a -> a.getFileUrl())
                            .collect(Collectors.toList())
            );
        }

        return dto;
    }

    // =========================================================
    // GETTERS — 🔥 FIXED HERE
    // =========================================================

    public List<LeaveApplicationDTO> getLeavesByEmployee(Long employeeId) {

        return leaveApplicationRepository
                .findByEmployeeIdWithAttachments(employeeId)   // ✅ FIX
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    public List<LeaveApplicationDTO> getAllLeaves() {

        return leaveApplicationRepository
                .findAllWithAttachments()   // ✅ FIX
                .stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    // =========================================================
    // HELPERS
    // =========================================================

    private String checkBalanceAndGetWarning(LeaveApplication leave, BigDecimal calculatedDays) {

        if (leave.getLeaveType() == LeaveType.COMP_OFF) {

            BigDecimal available =
                    compOffService.getAvailableCompOffDays(leave.getEmployeeId());

            if (available.compareTo(calculatedDays) < 0)
                return "Insufficient CompOff balance";
        }

        return null;
    }

    private void processAttachments(LeaveApplication leave) {

        if (leave.getAttachments() != null) {
            leave.getAttachments().forEach(a -> a.setLeaveApplication(leave));
        }
    }

    public BigDecimal calculateLeaveDuration(LeaveApplication leave) {

        BigDecimal total = BigDecimal.ZERO;
        LocalDate date = leave.getStartDate();

        while (!date.isAfter(leave.getEndDate())) {

            if (!holidayChecker.isNonWorkingDay(date)) {
                total = total.add(getLeaveDayIncrement(leave, date));
            }

            date = date.plusDays(1);
        }

        if (total.compareTo(BigDecimal.ZERO) == 0)
            throw new RuntimeException("All days are non-working");

        return total;
    }

    private BigDecimal getLeaveDayIncrement(LeaveApplication leave, LocalDate date) {

        if (leave.getLeaveType() == LeaveType.HALF_DAY ||
                (leave.getHalfDayType() != null &&
                        date.equals(leave.getEndDate())))
            return new BigDecimal("0.5");

        return BigDecimal.ONE;
    }

    private void notifyManager(LeaveApplication leave) {

        Employee employee = employeeRepository.findById(leave.getEmployeeId())
                .orElseThrow(() -> new RuntimeException("Employee not found"));
        Employee manager = employeeRepository.findById(employee.getManagerId())
                .orElseThrow(() -> new RuntimeException("Manager not found"));

        notificationService.createNotification(
                manager.getId(),
                manager.getEmail(),
                EventType.LEAVE_APPLIED,
                manager.getRole(),
                Channel.EMAIL,
                "Employee " + employee.getName() +
                        " applied leave from " +
                        leave.getStartDate() +
                        " to " +
                        leave.getEndDate()
        );
    }
}
