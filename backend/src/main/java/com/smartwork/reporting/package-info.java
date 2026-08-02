/**
 * Read-only capacity and analytics projections for dashboards.
 *
 * <p>This module owns no write path and no policy. It aggregates data the rest of the
 * platform already persists and exposes it in shapes the frontend can render directly,
 * which is exactly what Phase 9's reporting slice calls for.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Reporting",
        allowedDependencies = {"common", "assignment::application", "team::application", "organization"})
package com.smartwork.reporting;
