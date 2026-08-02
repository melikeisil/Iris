/**
 * People: users, their system roles, employee level, capacity and skill proficiency.
 *
 * <p>Deliberately contains no protected personal characteristic. {@code
 * ProtectedCharacteristicsSchemaTest} fails the build if a column for one is ever added,
 * whatever the intent - see ADR-013.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Identity",
        allowedDependencies = {"common", "organization::application"})
package com.smartwork.identity;
