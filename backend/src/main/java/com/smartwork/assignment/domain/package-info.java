/**
 * Exposed so the policy module can read a resolved {@code AssignmentPolicy} and a
 * {@code CandidateScore}. Entities remain off-limits across modules - ADR-028 is enforced
 * by {@code ArchitectureTest.modulesMustNotDependOnAnotherModulesEntities}.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.smartwork.assignment.domain;
