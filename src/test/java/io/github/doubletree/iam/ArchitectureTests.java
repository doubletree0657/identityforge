package io.github.doubletree.iam;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ArchitectureTests {

    @Test
    void moduleDependenciesAreAcyclicAndDeclared() {
        ApplicationModules.of(IdentityForgeApplication.class).verify();
    }

    @Test
    void applicationUseCasesDoNotReadTheHttpSecurityContext() {
        var classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("io.github.doubletree.iam");

        noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat()
                .resideInAPackage("org.springframework.security.core.context..")
                .check(classes);
    }
}
