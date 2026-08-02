/**
 * Exposed because {@code GuardContext} and {@code GuardResult} <em>are</em> the engine's
 * calling contract, not an internal detail.
 *
 * <p>The whole point of ADR-030 is that guards evaluate a plain context record assembled by
 * the caller rather than reaching into work items themselves. That design only works if the
 * caller can name the record — {@code workitem}'s {@code GuardContextAssembler} exists
 * precisely to build one. Keeping this package internal while requiring callers to
 * construct its central type was a contradiction; it was also a latent
 * {@code ModularityTest} failure from Phase 3, caught in Phase 5 by a static check when no
 * compiler was available to catch it earlier.
 */
@org.springframework.modulith.NamedInterface("domain")
package com.smartwork.workflow.domain;
