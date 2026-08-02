/**
 * The seam between the platform and the AI service: the HTTP client, the org-context
 * vocabulary builder, stored recommendations and plan proposals.
 *
 * <p>Everything this module produces is a <em>proposal</em>. Nothing here mutates a work
 * item's status, assignee or approval state — the AI service's contract (docs/07 §2) says
 * its output is never an authority, and confining the integration to a module that only
 * writes to {@code recommendations} is how that stays true rather than being a promise in
 * a document.
 *
 * <p>Degrades on purpose: with the AI container stopped, analysis records a FAILED
 * recommendation and decomposition falls back to a matched process template. Filing and
 * working a ticket never depends on the model being reachable.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Analysis",
        allowedDependencies = {"common", "workitem::application", "workitem::domain",
                "process::application", "process::domain", "identity::application",
                "organization::application", "team::application", "project::application"})
package com.smartwork.analysis;
