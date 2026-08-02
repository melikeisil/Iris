package com.smartwork.organization.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read contract for departments, used by identity, team, project and later reporting. */
public interface DepartmentDirectory {

    Optional<DepartmentView> find(UUID organizationId, UUID departmentId);

    Optional<DepartmentView> findByCode(UUID organizationId, String code);

    List<DepartmentView> listAll(UUID organizationId);

    /** @throws com.smartwork.common.error.ApiException NOT_FOUND when it does not exist. */
    DepartmentView require(UUID organizationId, UUID departmentId);

    record DepartmentView(UUID id, UUID organizationId, String code, String name,
                          UUID leadUserId, boolean active) {
    }
}
