/**
 * Exposed so {@code analysis} can read a matched template when building the AI's
 * decomposition request and when falling back to the template unmodified.
 *
 * <p>Entities in here remain off-limits to other modules as JPA associations —
 * {@code ArchitectureTest.modulesMustNotDependOnAnotherModulesEntities} enforces ADR-028.
 * {@code analysis} reads a {@code ProcessTemplate} to name its code and list its task
 * titles; it never holds a reference to one on an entity of its own.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.smartwork.process.domain;
