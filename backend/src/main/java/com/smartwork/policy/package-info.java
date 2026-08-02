/**
 * The policy engine: six routes, the fourteen auto-assign conditions, approval requests,
 * review queues and escalation.
 *
 * <p>This module answers one question — <em>how much human scrutiny does this work need
 * before someone starts it?</em> It is the last authority in the pipeline and the only one
 * that can cause work to be assigned. Three properties make that safe to trust:
 *
 * <p><b>The decision is a pure function.</b> {@code PolicyEngine} takes a
 * {@code RoutingInputs} record and returns a {@code RoutingDecision}. No repository, no
 * clock, no principal. That is what lets the fourteen conditions be tested one at a time,
 * and what makes "replay this decision" mean simply calling the function again.
 *
 * <p><b>Routes are evaluated strictest-first, first match wins.</b> No combination of
 * conditions can produce a laxer outcome than the strictest applicable trigger.
 *
 * <p><b>Deciding what scrutiny is needed is separate from deciding whose queue it lands
 * in.</b> {@code ApproverResolver} does the second. Reorganising a department therefore
 * changes who reviews something without changing whether it needed review — which is the
 * only way a decision stored a year ago still means something today.
 */
@org.springframework.modulith.ApplicationModule(
        displayName = "Policy",
        allowedDependencies = {"common", "workitem::application", "workitem::domain",
                "assignment::application", "assignment::domain", "analysis::application",
                "identity::application", "organization::application", "team::application"})
package com.smartwork.policy;
