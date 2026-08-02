package com.smartwork;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Compile-time verification of the module boundaries described in
 * docs/01-architecture.md. This is what makes "modular monolith" a property rather than
 * an aspiration - and it is what keeps the option of extracting a module later cheap.
 */
class ModularityTest {

    /**
     * Test classes share the {@code com.smartwork} package tree and would otherwise be
     * detected as modules. They are excluded rather than relocated, so tests can keep
     * reaching into whatever they need to verify.
     */
    private static final DescribedPredicate<JavaClass> TEST_CLASSES =
            JavaClass.Predicates.resideInAnyPackage(
                    "com.smartwork.support..", "com.smartwork.integration..");

    static final ApplicationModules MODULES =
            ApplicationModules.of(SmartWorkApplication.class, TEST_CLASSES);

    @Test
    void moduleBoundariesHold() {
        MODULES.verify();
    }

    @Test
    void writeModuleDocumentation() {
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml();
    }
}
