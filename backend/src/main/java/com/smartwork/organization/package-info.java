/**
 * The tenant root and its departmental structure.
 *
 * <p>Sits at the bottom of the dependency graph: it depends on nothing but {@code common},
 * which is what lets every other module reference an organization or a department.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Organization",
        allowedDependencies = {"common"})
package com.smartwork.organization;
