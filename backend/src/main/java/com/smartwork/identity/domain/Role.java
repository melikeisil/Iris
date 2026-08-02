package com.smartwork.identity.domain;

import com.smartwork.common.domain.Ids;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

/** Lookup table for the four global system roles. Seeded by R__seed_reference_data.sql. */
@Entity
@Table(name = "roles")
public class Role {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id = Ids.newId();

    @Enumerated(EnumType.STRING)
    @Column(name = "code", nullable = false, length = 32, unique = true)
    private SystemRole code;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    protected Role() {
    }

    public Role(SystemRole code, String name) {
        this.code = code;
        this.name = name;
    }

    public UUID getId() {
        return id;
    }

    public SystemRole getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Role role && code == role.code;
    }

    @Override
    public int hashCode() {
        return code == null ? 0 : code.hashCode();
    }
}
