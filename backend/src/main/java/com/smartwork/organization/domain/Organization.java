package com.smartwork.organization.domain;

import com.smartwork.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * The tenant root. Single-tenant deployments still get one of these: multi-tenancy is
 * nearly free at design time and prohibitively expensive to retrofit (assumption A1).
 */
@Entity
@Table(name = "organizations")
public class Organization extends BaseEntity {

    @Column(name = "code", nullable = false, length = 64, unique = true)
    private String code;

    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Column(name = "timezone", nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(name = "active", nullable = false)
    private boolean active = true;

    protected Organization() {
    }

    public Organization(String code, String name, String timezone) {
        this.code = code;
        this.name = name;
        this.timezone = timezone;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public String getTimezone() {
        return timezone;
    }

    public boolean isActive() {
        return active;
    }
}
