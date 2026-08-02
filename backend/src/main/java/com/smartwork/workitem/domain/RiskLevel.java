package com.smartwork.workitem.domain;

public enum RiskLevel {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isAtLeast(RiskLevel other) {
        return ordinal() >= other.ordinal();
    }
}
