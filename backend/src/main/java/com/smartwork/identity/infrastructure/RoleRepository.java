package com.smartwork.identity.infrastructure;

import com.smartwork.identity.domain.Role;
import com.smartwork.identity.domain.SystemRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(SystemRole code);
}
