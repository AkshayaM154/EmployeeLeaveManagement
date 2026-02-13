package com.example.employeeLeaveApplication.dto;

public class LeaveResponse {

    private LeaveApplicationDTO leave;
    private String warning;

    public LeaveResponse(LeaveApplicationDTO leave, String warning) {
        this.leave = leave;
        this.warning = warning;
    }

    public LeaveApplicationDTO getLeave() {
        return leave;
    }

    public void setLeave(LeaveApplicationDTO leave) {
        this.leave = leave;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }
}
