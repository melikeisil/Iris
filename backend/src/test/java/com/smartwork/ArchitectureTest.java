package com.smartwork;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.List;

import static com.tngtech.archunit.base.DescribedPredicate.describe;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;

/** Structural rules from docs/01-architecture.md, enforced rather than documented. */
class ArchitectureTest {

    private static JavaClasses classes;

    private static final List<Class<? extends Annotation>> MAPPING_ANNOTATIONS = List.of(
            RequestMapping.class, GetMapping.class, PostMapping.class,
            PutMapping.class, PatchMapping.class, DeleteMapping.class);

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.smartwork");
    }

    /**
     * The rule the brief calls out explicitly. A controller returning an entity leaks the
     * schema into the API contract and makes every column a breaking change.
     *
     * <p>Scoped to what actually crosses the HTTP boundary — the return and parameter types
     * of request-mapped methods — rather than to any reference from an {@code ..api..}
     * class. The earlier, broader form forbade mappers and DTO factories from naming an
     * entity at all, which is exactly the code that <em>implements</em> this boundary:
     * {@code WorkItemMapper} has to accept a {@code WorkItem} in order to return a DTO
     * instead of one. A rule that fails on its own compliance mechanism gets deleted, not
     * obeyed.
     */
    @Test
    void controllerEndpointsMustNotExposeJpaEntities() {
        DescribedPredicate<JavaMethod> isHttpEndpoint = describe("an HTTP endpoint method",
                method -> MAPPING_ANNOTATIONS.stream().anyMatch(method::isAnnotatedWith));

        ArchCondition<JavaMethod> exposeAnEntity =
                new ArchCondition<>("have a JPA entity in its signature") {
                    @Override
                    public void check(JavaMethod method, ConditionEvents events) {
                        List<JavaClass> exposed = new ArrayList<>();
                        if (method.getRawReturnType().isAnnotatedWith(Entity.class)) {
                            exposed.add(method.getRawReturnType());
                        }
                        method.getRawParameterTypes().stream()
                                .filter(p -> p.isAnnotatedWith(Entity.class))
                                .forEach(exposed::add);

                        if (!exposed.isEmpty()) {
                            events.add(SimpleConditionEvent.satisfied(method,
                                    method.getFullName() + " exposes " + exposed));
                        }
                    }
                };

        noMethods().that(isHttpEndpoint).should(exposeAnEntity)
                .because("an endpoint returning or accepting an entity leaks the schema into "
                        + "the API contract and makes every column a breaking change; map to "
                        + "a DTO at the boundary")
                .check(classes);
    }

    @Test
    void repositoriesLiveInInfrastructure() {
        ArchRule rule = classes()
                .that().haveSimpleNameEndingWith("Repository")
                .should().resideInAPackage("..infrastructure..")
                .because("persistence is an infrastructure concern");
        rule.check(classes);
    }

    @Test
    void entitiesLiveInDomain() {
        ArchRule rule = classes()
                .that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage("..domain..")
                .because("entities are domain types, not infrastructure or API types");
        rule.check(classes);
    }

    @Test
    void domainMustNotDependOnSpringWeb() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "org.springframework.web..", "jakarta.servlet..")
                .because("domain logic must be testable without a web container");
        rule.check(classes);
    }

    @Test
    void nothingOutsideAuthMayReadPasswordHashes() {
        ArchRule rule = noClasses()
                .that().resideOutsideOfPackages("com.smartwork.auth..", "com.smartwork.identity..")
                .should().callMethod(com.smartwork.identity.domain.User.class, "getPasswordHash")
                .because("password hashes must not travel outside authentication and identity");
        rule.check(classes);
    }

    /**
     * The AI service must never be able to influence an authorization outcome. In Phase 2
     * there is no AI client yet, so this rule guards the shape of things to come: nothing
     * in a future ai/recommendation package may reach into security types directly.
     */
    @Test
    void aiPackagesMustNotTouchSecurity() {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage("..recommendation.infrastructure..", "..ai..")
                .should().dependOnClassesThat().resideInAnyPackage("com.smartwork.common.security..")
                .because("AI output must never reach an authorization decision (ADR-005)");
        rule.allowEmptyShould(true).check(classes);
    }

    /**
     * ADR-028, enforced structurally.
     *
     * <p>Phase 5 exposes {@code identity.domain} and {@code workitem.domain} as Modulith
     * named interfaces so other modules can use the shared vocabulary enums — Priority,
     * WorkItemType, EmployeeLevel and friends. Modulith now permits the whole package,
     * which would also permit reaching for {@code User} or {@code WorkItem} themselves.
     * This rule is what keeps the entities off-limits: an entity graph spanning modules is
     * what makes a monolith impossible to decompose later, because extracting a module
     * turns into unwinding a lazy-loading graph rather than replacing a service call.
     *
     * <p>Cross-module references are UUIDs. Within a module, entity associations are fine.
     */
    @Test
    void modulesMustNotDependOnAnotherModulesEntities() {
        for (String module : List.of("identity", "workitem", "organization", "team",
                "project", "workflow", "process", "analysis", "assignment", "auth")) {

            // One composed predicate, not two chained checks: the forbidden thing is a
            // dependency on a class that is *both* an @Entity *and* in this module's
            // domain package. Two separate dependOnClassesThat() clauses would instead
            // mean "depends on some entity somewhere AND on something in that package",
            // which almost every class satisfies and would flag constantly.
            // Written as an explicit lambda rather than by composing ArchUnit's built-in
            // predicates: annotatedWith() is typed on CanBeAnnotated and resideInAPackage()
            // on JavaClass, so combining them needs a cast that reads worse than this does.
            String domainPackage = "com.smartwork." + module + ".domain";
            DescribedPredicate<JavaClass> foreignEntity = describe(
                    "an @Entity in " + module + ".domain",
                    javaClass -> javaClass.isAnnotatedWith(Entity.class)
                            && javaClass.getPackageName().startsWith(domainPackage));

            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("com.smartwork." + module + "..")
                    .and().resideInAPackage("com.smartwork..")
                    // The demo seeder legitimately drives other modules' write APIs and is
                    // excluded from the module rules by its own package-info.
                    .and().resideOutsideOfPackage("com.smartwork.demo..")
                    .should().dependOnClassesThat(foreignEntity)
                    .because("cross-module references are UUIDs, never JPA associations (ADR-028); "
                            + module + ".domain is exposed for its enums, not its entities");

            rule.allowEmptyShould(true).check(classes);
        }
    }
}
