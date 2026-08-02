/**
 * Reusable process templates, deterministic matching, and instantiation into a real tree
 * of work items with dependency links.
 *
 * <p>Templates are what give the AI something to customize instead of invent. They are also
 * the second opinion: {@code TemplateMatcher} classifies a request independently of the
 * model, so the model's {@code matchedProcessTemplate} can be checked rather than trusted,
 * and a disagreement between the two becomes a review signal.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Process",
        allowedDependencies = {"common", "workitem::application", "workitem::domain",
                "identity::application", "organization::application", "team::application"})
package com.smartwork.process;
