package io.github.doubletree.iam.oauth.web;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/demo-resource-api/payroll")
public class DemoResourceApiController {

    private static final List<DemoEmployeeResponse> EMPLOYEES = List.of(
            new DemoEmployeeResponse("E-1001", "Avery Chen", "Engineering"),
            new DemoEmployeeResponse("E-1002", "Mina Patel", "Finance"),
            new DemoEmployeeResponse("E-1003", "Jonas Meyer", "People Operations"));

    private static final List<DemoSalaryResponse> SALARIES = List.of(
            new DemoSalaryResponse("E-1001", "Avery Chen", "Engineering", new BigDecimal("132000.00")),
            new DemoSalaryResponse("E-1002", "Mina Patel", "Finance", new BigDecimal("118500.00")),
            new DemoSalaryResponse("E-1003", "Jonas Meyer", "People Operations", new BigDecimal("109750.00")));

    @GetMapping("/employees")
    public List<DemoEmployeeResponse> employees() {
        return EMPLOYEES;
    }

    @GetMapping("/salaries")
    public List<DemoSalaryResponse> salaries() {
        return SALARIES;
    }

    @PostMapping("/salaries")
    public DemoSalaryWriteResponse writeSalaryDemo() {
        return new DemoSalaryWriteResponse("accepted", "Static payroll salary write demo accepted.");
    }

    public record DemoEmployeeResponse(String employeeId, String name, String department) {
    }

    public record DemoSalaryResponse(String employeeId, String name, String department, BigDecimal salaryAmount) {
    }

    public record DemoSalaryWriteResponse(String status, String message) {
    }
}
