package com.example.employeeLeaveApplication.service;

import com.example.employeeLeaveApplication.component.HolidayChecker;
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
    // APPLY LEAVE (Employee / Manager / Admin)
    // =========================================================
    @Transactional
    public LeaveResponse applyLeave(LeaveApplication leave, boolean confirmLossOfPay) {

        // 0️⃣ Check if employee exists
        Employee employee = employeeRepository.findById(leave.getEmployeeId()).orElse(null);
        if (employee == null) {
            return new LeaveResponse(null,
                    "Employee with ID " + leave.getEmployeeId() + " does not exist. Leave not applied."
            );
        }


        // 1️⃣ Validate dates
        if (leave.getEndDate().isBefore(leave.getStartDate())) {
            return new LeaveResponse(null, "End date cannot be before start date");
        }

        // 2️⃣ Check for overlapping leaves
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

        // 3️⃣ Calculate leave days (with half-day support)
        BigDecimal calculatedDays;
        try {
            calculatedDays = calculateLeaveDuration(leave);
        } catch (Exception e) {
            return new LeaveResponse(null, e.getMessage());
        }

        // 4️⃣ Check leave balance
        String warning = checkBalanceAndGetWarning(leave, calculatedDays);
        if (warning != null && !confirmLossOfPay) {
            return new LeaveResponse(null, warning);
        }

        // 5️⃣ Process attachments
        processAttachments(leave);

        // 6️⃣ Set leave days and status
        leave.setDays(calculatedDays);
        leave.setStatus(LeaveStatus.PENDING);

        // 7️⃣ Save leave
        LeaveApplication savedLeave = leaveApplicationRepository.save(leave);

        // 8️⃣ Handle Comp-Off leaves
        if (leave.getLeaveType() == LeaveType.COMP_OFF && warning == null) {
            compOffService.useCompOff(
                    leave.getEmployeeId(),
                    calculatedDays,
                    savedLeave.getId()
            );
        }

        // 9️⃣ Notify next approver safely
        notifyNextApproverSafe(savedLeave);

        return new LeaveResponse(savedLeave, warning);
    }

    // =========================================================
    // SAFE ROLE-BASED NOTIFICATIONS
    // =========================================================
    private void notifyNextApproverSafe(LeaveApplication leave) {
        Employee applicant = employeeRepository.findById(leave.getEmployeeId()).orElse(null);
        if (applicant == null) return;

        Employee nextApprover = null;

        if (applicant.getRole() == Role.EMPLOYEE) {
            if (applicant.getManagerId() != null) {
                nextApprover = employeeRepository.findById(applicant.getManagerId()).orElse(null);
            }
        } else if (applicant.getRole() == Role.MANAGER || applicant.getRole() == Role.ADMIN) {
            nextApprover = employeeRepository.findByRole(Role.HR).orElse(null);
        }

        if (nextApprover != null) {
            notificationService.createNotification(
                    nextApprover.getId(),
                    nextApprover.getEmail(),
                    EventType.LEAVE_APPLIED,
                    nextApprover.getRole(),
                    Channel.EMAIL,
                    applicant.getName() +
                            " applied leave from " +
                            leave.getStartDate() +
                            " to " +
                            leave.getEndDate()
            );
        }
    }

    // =========================================================
    // CANCEL LEAVES (Employee/Admin)
    // =========================================================
    @Transactional
    public LeaveResponse cancelAdminLeave(Long applicationId) {
        LeaveApplication leave = leaveApplicationRepository.findById(applicationId).orElse(null);
        if (leave == null) return new LeaveResponse(null, "Leave not found. Cancellation skipped.");
        performCancellation(leave);
        return new LeaveResponse(leave, "Leave cancelled successfully");
    }

    @Transactional
    public LeaveResponse cancelEmployeeLeave(Long applicationId, Long employeeId) {
        LeaveApplication leave = leaveApplicationRepository.findById(applicationId).orElse(null);
        if (leave == null) return new LeaveResponse(null, "Leave not found. Cancellation skipped.");

        if (!leave.getEmployeeId().equals(employeeId)) {
            return new LeaveResponse(null, "Unauthorized cancellation. Skipped.");
        }

        if (leave.getStatus() == LeaveStatus.REJECTED || leave.getStatus() == LeaveStatus.CANCELLED) {
            return new LeaveResponse(null, "Leave already finalized. Skipped.");
        }

        if (leave.getStatus() == LeaveStatus.APPROVED) {
            return new LeaveResponse(null, "Approved leaves cannot be cancelled.");
        }

        performCancellation(leave);
        return new LeaveResponse(leave, "Leave cancelled successfully");
    }

    private void performCancellation(LeaveApplication leave) {
        // Reverse Comp-Off if used
        if (leave.getLeaveType() == LeaveType.COMP_OFF &&
                (leave.getStatus() == LeaveStatus.APPROVED || leave.getStatus() == LeaveStatus.PENDING)) {

            List<CompOff> credits = compOffRepository.findByUsedLeaveApplicationId(leave.getId());

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
    // HELPERS
    // =========================================================
    private String checkBalanceAndGetWarning(LeaveApplication leave, BigDecimal calculatedDays) {
        if (leave.getLeaveType() == LeaveType.COMP_OFF) {
            BigDecimal available = compOffService.getAvailableCompOffDays(leave.getEmployeeId());
            if (available.compareTo(calculatedDays) < 0) {
                return "Insufficient CompOff balance";
            }
        }
        return null;
    }

    private void processAttachments(LeaveApplication leave) {
        if (leave.getAttachments() != null) {
            leave.getAttachments().forEach(a -> {
                a.setFileUrl(a.getFileUrl());
                a.setLeaveApplication(leave);
            });
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

        if (total.compareTo(BigDecimal.ZERO) == 0) {
            throw new RuntimeException("All days are non-working");
        }

        return total;
    }

    private BigDecimal getLeaveDayIncrement(LeaveApplication leave, LocalDate date) {
        if (leave.getLeaveType() == LeaveType.HALF_DAY ||
                (leave.getHalfDayType() != null && date.equals(leave.getEndDate()))) {
            return new BigDecimal("0.5");
        }
        return BigDecimal.ONE;
    }

    public List<LeaveApplication> getLeavesByEmployee(Long employeeId) {
        return leaveApplicationRepository.findByEmployeeId(employeeId);
    }
}
