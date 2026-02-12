package com.example.employeeLeaveApplication.dto;

public class LeaveCancelRequest {

    private Long applicationId;
    private Long employeeId;

    // =====================
    // Constructors
    // =====================
    public LeaveCancelRequest() {}

    public LeaveCancelRequest(Long applicationId, Long employeeId) {
        this.applicationId = applicationId;
        this.employeeId = employeeId;
    }

    // =====================
    // Getters & Setters
    // =====================
    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public Long getEmployeeId() {
        return employeeId;
    }

    public void setEmployeeId(Long employeeId) {
        this.employeeId = employeeId;
    }
}
