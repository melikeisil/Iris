/**
 * Projects and their components.
 *
 * <p>Components are not decoration: the assignment scorer awards 10% for experience in the
 * components a work item affects, so they are a first-class part of the domain.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Project",
        allowedDependencies = {"common", "organization::application"})
package com.smartwork.project;
