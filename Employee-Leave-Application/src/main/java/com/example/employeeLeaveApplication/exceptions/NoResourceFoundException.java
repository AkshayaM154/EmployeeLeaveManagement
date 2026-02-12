package com.example.employeeLeaveApplication.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND) // ✅ maps to 404
public class NoResourceFoundException extends RuntimeException {

  public NoResourceFoundException(String message) {
    super(message);
  }
}
