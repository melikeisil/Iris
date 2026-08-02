package com.smartwork.organization.api;

import com.smartwork.common.tenancy.OrganizationContext;
import com.smartwork.organization.application.DepartmentDirectory;
import com.smartwork.organization.application.DepartmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/departments")
@Tag(name = "admin")
public class AdminDepartmentController {

    private final DepartmentService departments;

    public AdminDepartmentController(DepartmentService departments) {
        this.departments = departments;
    }

    @GetMapping
    @Operation(summary = "List departments")
    public List<DepartmentDirectory.DepartmentView> list() {
        return departments.listAll(OrganizationContext.require());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get one department")
    public DepartmentDirectory.DepartmentView get(@PathVariable UUID id) {
        return departments.require(OrganizationContext.require(), id);
    }

    @PostMapping
    @Operation(summary = "Create a department")
    public ResponseEntity<DepartmentDirectory.DepartmentView> create(
            @Valid @RequestBody CreateDepartmentRequest request) {
        return ResponseEntity.status(201).body(
                departments.create(OrganizationContext.require(), request.code(), request.name()));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update a department and its lead")
    public DepartmentDirectory.DepartmentView update(@PathVariable UUID id,
                                                     @Valid @RequestBody UpdateDepartmentRequest request) {
        return departments.update(OrganizationContext.require(), id,
                request.name(), request.leadUserId(), request.active());
    }

    public record CreateDepartmentRequest(@NotBlank @Size(max = 64) String code,
                                          @NotBlank @Size(max = 255) String name) {
    }

    public record UpdateDepartmentRequest(@NotBlank @Size(max = 255) String name,
                                          UUID leadUserId,
                                          boolean active) {
    }
}
