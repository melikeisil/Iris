/**
 * Shared kernel. Declared OPEN so other modules may use its sub-packages directly;
 * in exchange it must never depend on any other module.
 */
@org.springframework.modulith.ApplicationModule(
        type = org.springframework.modulith.ApplicationModule.Type.OPEN,
        displayName = "Common")
package com.smartwork.common;
