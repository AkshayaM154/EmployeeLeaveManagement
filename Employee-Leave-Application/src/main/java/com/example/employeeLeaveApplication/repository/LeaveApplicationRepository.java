package com.example.employeeLeaveApplication.repository;

import com.example.employeeLeaveApplication.entity.LeaveApplication;
import com.example.employeeLeaveApplication.enums.LeaveStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {

    int countByEmployeeIdInAndStatus(List<Long> employeeIds, LeaveStatus status);

    // ===============================
    // ✅ BASIC FIND (Needed by DashboardService)
    // ===============================
    List<LeaveApplication> findByEmployeeId(Long employeeId);

    // ===============================
    // ✅ FETCH JOIN — Fix Lazy Error
    // ===============================

    @Query("""
        SELECT DISTINCT l
        FROM LeaveApplication l
        LEFT JOIN FETCH l.attachments
        WHERE l.employeeId = :employeeId
    """)
    List<LeaveApplication> findByEmployeeIdWithAttachments(
            @Param("employeeId") Long employeeId
    );

    @Query("""
        SELECT DISTINCT l
        FROM LeaveApplication l
        LEFT JOIN FETCH l.attachments
    """)
    List<LeaveApplication> findAllWithAttachments();

    // ===============================

    List<LeaveApplication> findByEmployeeIdInAndStatus(
            List<Long> employeeIds,
            LeaveStatus status
    );

    List<LeaveApplication> findByStatus(LeaveStatus status);

    @Query("""
        SELECT COUNT(l)
        FROM LeaveApplication l
        WHERE l.employeeId = :employeeId
          AND l.status = 'APPROVED'
          AND YEAR(l.startDate) = :year
          AND MONTH(l.startDate) = :month
    """)
    int countApprovedInMonth(
            @Param("employeeId") Long employeeId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Query("""
        SELECT l.leaveType, COUNT(l), SUM(l.days)
        FROM LeaveApplication l
        WHERE l.employeeId = :employeeId
          AND l.status = 'APPROVED'
          AND YEAR(l.startDate) = :year
          AND MONTH(l.startDate) = :month
        GROUP BY l.leaveType
    """)
    List<Object[]> getMonthlyStats(
            @Param("employeeId") Long employeeId,
            @Param("year") Integer year,
            @Param("month") Integer month
    );

    @Query("""
        SELECT l
        FROM LeaveApplication l
        WHERE l.employeeId = :employeeId
          AND l.status = :status
          AND YEAR(l.startDate) = :year
    """)
    List<LeaveApplication> findByEmployeeIdAndStatusAndYear(
            @Param("employeeId") Long employeeId,
            @Param("status") LeaveStatus status,
            @Param("year") Integer year
    );

    @Query("""
        SELECT COALESCE(SUM(l.days), 0)
        FROM LeaveApplication l
        WHERE l.employeeId = :empId
          AND l.status = :status
          AND l.year = :year
    """)
    Double getTotalUsedDays(
            @Param("empId") Long employeeId,
            @Param("status") LeaveStatus status,
            @Param("year") Integer year
    );

    @Query("""
        SELECT l
        FROM LeaveApplication l
        WHERE l.employeeId = :empId
          AND l.status IN (:pending, :approved)
          AND l.startDate <= :endDate
          AND l.endDate >= :startDate
    """)
    List<LeaveApplication> findOverlappingLeaves(
            @Param("empId") Long empId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("pending") LeaveStatus pending,
            @Param("approved") LeaveStatus approved
    );
}
