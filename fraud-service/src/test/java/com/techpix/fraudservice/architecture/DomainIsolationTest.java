package com.techpix.fraudservice.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

/**
 * O domínio de risco não conhece HTTP, banco nem o schema legado.
 * Só a ACL conhece o schema legado. Se uma regra importar JdbcClient, o build quebra.
 */
class DomainIsolationTest {

    static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("com.techpix.fraudservice");

    @Test
    void domainDoesNotDependOnInfrastructure() {
        noClasses().that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..acl..", "..persistence..", "..api..", "..health..",
                        "org.springframework.jdbc..", "org.springframework.web..", "java.sql..")
                .check(CLASSES);
    }

    @Test
    void onlyTheAntiCorruptionLayerTouchesTheLegacySchema() {
        noClasses().that().resideOutsideOfPackage("..acl..")
                .should().dependOnClassesThat().haveSimpleNameStartingWith("Legacy")
                .check(CLASSES);
    }
}
