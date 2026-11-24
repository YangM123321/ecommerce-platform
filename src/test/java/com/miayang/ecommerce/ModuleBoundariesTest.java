package com.miayang.ecommerce;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Fails the build if one module reaches into another module's internals.
 * Other modules may only use a module's "api" package.
 *
 * allowEmptyShould(true): the internal packages are still empty in Session 1.
 */
@AnalyzeClasses(packages = "com.miayang.ecommerce", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleBoundariesTest {

    @ArchTest
    static final ArchRule userInternalsArePrivate = classes()
            .that().resideInAPackage("..ecommerce.user.internal..")
            .should().onlyBeAccessed().byAnyPackage("..ecommerce.user..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule productInternalsArePrivate = classes()
            .that().resideInAPackage("..ecommerce.product.internal..")
            .should().onlyBeAccessed().byAnyPackage("..ecommerce.product..")
            .allowEmptyShould(true);

    @ArchTest
    static final ArchRule orderInternalsArePrivate = classes()
            .that().resideInAPackage("..ecommerce.order.internal..")
            .should().onlyBeAccessed().byAnyPackage("..ecommerce.order..")
            .allowEmptyShould(true);
}
