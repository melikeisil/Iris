/**
 * Exposed so other modules can reference the shared vocabulary enums - notably
 * {@link com.smartwork.identity.domain.EmployeeLevel}, which the assignment module's level
 * restrictions are keyed on.
 *
 * <p>Exposing the package does <em>not</em> license reaching for the entities in it.
 * {@code ArchitectureTest.modulesMustNotDependOnAnotherModulesEntities} fails the build on
 * any cross-module reference to a {@code @Entity} type, which is the ADR-028 rule that
 * actually matters. Modulith controls which packages are visible; ArchUnit controls what
 * may be taken from them.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.smartwork.identity.domain;
