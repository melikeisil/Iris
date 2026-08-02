package com.smartwork.workitem.domain;

public enum Priority {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL;

    public boolean isAtLeast(Priority other) {
        return ordinal() >= other.ordinal();
    }
}
