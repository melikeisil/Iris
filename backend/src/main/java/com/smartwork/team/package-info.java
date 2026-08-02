/**
 * Teams and memberships. Owns team capacity, which the workload calculation and the
 * team/department component of the assignment score both read.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Team",
        allowedDependencies = {"common", "organization::application", "identity::application"})
package com.smartwork.team;
