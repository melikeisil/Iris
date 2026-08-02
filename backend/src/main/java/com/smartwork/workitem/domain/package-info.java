/**
 * Exposed so other modules can reference the shared classification enums -
 * {@code WorkItemType}, {@code WorkFamily}, {@code Priority}, {@code RiskLevel},
 * {@code Complexity}, {@code ClosureType}, {@code OriginType}, {@code LinkType}. These are
 * the platform's common vocabulary: the assignment scorer, the process templates, the
 * analysis client and the AI service's JSON all speak in them.
 *
 * <p>Duplicating them per module was the alternative and would be worse - four copies of
 * {@code Priority} that must be kept in sync by convention is exactly the kind of drift a
 * modular monolith is supposed to prevent.
 *
 * <p>The {@code WorkItem} aggregate lives here too and is <em>not</em> fair game:
 * {@code ArchitectureTest.modulesMustNotDependOnAnotherModulesEntities} enforces ADR-028
 * by failing the build on any cross-module reference to an {@code @Entity} type.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.smartwork.workitem.domain;
