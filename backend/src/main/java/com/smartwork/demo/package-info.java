/**
 * Demo seeding. Active only under the {@code demo} profile and excluded from the module
 * dependency rules, since seeding legitimately touches several modules' write APIs.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Demo",
        allowedDependencies = {"common", "identity::application", "identity::domain",
                "organization::application", "team::application", "project::application",
                "process::application", "workitem::application", "workitem::domain"})
package com.smartwork.demo;
