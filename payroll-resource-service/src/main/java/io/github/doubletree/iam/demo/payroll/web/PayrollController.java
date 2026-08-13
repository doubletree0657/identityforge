package io.github.doubletree.iam.demo.payroll.web;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/payroll")
public class PayrollController {

    private static final List<EmployeeResponse> EMPLOYEES = List.of(
            new EmployeeResponse("E-1001", "Avery Chen", "Engineering"),
            new EmployeeResponse("E-1002", "Mina Patel", "Finance"),
            new EmployeeResponse("E-1003", "Jonas Meyer", "People Operations"));

    private static final List<SalaryResponse> SALARIES = List.of(
            new SalaryResponse("E-1001", "Avery Chen", "Engineering", new BigDecimal("132000.00")),
            new SalaryResponse("E-1002", "Mina Patel", "Finance", new BigDecimal("118500.00")),
            new SalaryResponse("E-1003", "Jonas Meyer", "People Operations", new BigDecimal("109750.00")));

    @GetMapping("/employees")
    public List<EmployeeResponse> employees() {
        return EMPLOYEES;
    }

    @GetMapping("/salaries")
    public List<SalaryResponse> salaries() {
        return SALARIES;
    }

    @PostMapping("/salaries")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SalaryWriteResponse writeSalaryDemo() {
        return new SalaryWriteResponse("accepted", "Static payroll salary write demo accepted.");
    }

    public record EmployeeResponse(String employeeId, String name, String department) {
    }

    public record SalaryResponse(String employeeId, String name, String department, BigDecimal salaryAmount) {
    }

    public record SalaryWriteResponse(String status, String message) {
    }
}
