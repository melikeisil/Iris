package com.smartwork.organization.application;

import com.smartwork.common.error.ApiException;
import com.smartwork.organization.domain.Department;
import com.smartwork.organization.infrastructure.DepartmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class DepartmentService implements DepartmentDirectory {

    private final DepartmentRepository departments;

    public DepartmentService(DepartmentRepository departments) {
        this.departments = departments;
    }

    @Override
    public Optional<DepartmentView> find(UUID organizationId, UUID departmentId) {
        return departments.findByIdInOrganization(departmentId, organizationId).map(DepartmentService::toView);
    }

    @Override
    public Optional<DepartmentView> findByCode(UUID organizationId, String code) {
        return departments.findByCode(code.trim().toUpperCase(), organizationId).map(DepartmentService::toView);
    }

    @Override
    public List<DepartmentView> listAll(UUID organizationId) {
        return departments.findAllInOrganization(organizationId).stream().map(DepartmentService::toView).toList();
    }

    @Override
    public DepartmentView require(UUID organizationId, UUID departmentId) {
        return find(organizationId, departmentId)
                .orElseThrow(() -> ApiException.notFound("Department", departmentId));
    }

    @Transactional
    public DepartmentView create(UUID organizationId, String code, String name) {
        String normalized = code.trim().toUpperCase();
        if (departments.existsByOrganizationIdAndCode(organizationId, normalized)) {
            throw ApiException.duplicate("A department with code '" + normalized + "' already exists.");
        }
        return toView(departments.save(new Department(organizationId, normalized, name)));
    }

    @Transactional
    public DepartmentView update(UUID organizationId, UUID departmentId, String name, UUID leadUserId, boolean active) {
        Department department = departments.findByIdInOrganization(departmentId, organizationId)
                .orElseThrow(() -> ApiException.notFound("Department", departmentId));
        department.rename(name);
        department.assignLead(leadUserId);
        department.setActive(active);
        return toView(department);
    }

    private static DepartmentView toView(Department department) {
        return new DepartmentView(
                department.getId(), department.getOrganizationId(), department.getCode(),
                department.getName(), department.getLeadUserId(), department.isActive());
    }
}
