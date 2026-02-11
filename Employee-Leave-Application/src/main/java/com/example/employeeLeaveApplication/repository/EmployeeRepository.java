package com.example.employeeLeaveApplication.repository;

<<<<<<< HEAD:Employee-Leave-Application/src/main/java/com/example/notificationservice/repository/EmployeeRepository.java
import com.example.notificationservice.entity.Employee;
import com.example.notificationservice.enums.Role;
=======
import com.example.employeeLeaveApplication.entity.Employee;
>>>>>>> origin/dev:Employee-Leave-Application/src/main/java/com/example/employeeLeaveApplication/repository/EmployeeRepository.java
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByEmail(String email);

    boolean existsByEmail(String email); // ✅ Added for EmployeeService check

    List<Employee> findByManagerId(Long managerId);

    Optional<Employee> findByRole(Role role);
}
