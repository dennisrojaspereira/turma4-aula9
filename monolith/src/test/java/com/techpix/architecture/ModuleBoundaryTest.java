package com.techpix.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * As fronteiras dos módulos, como código executável.
 * <p>
 * Um diagrama diz "Payment usa Fraud pela API". Este teste garante. Se alguém importar
 * {@code com.techpix.fraud.internal.FraudService} de dentro de Payment, o build quebra.
 * <p>
 * Modularidade é uma decisão de design. Distribuição é uma decisão operacional.
 * Este teste protege a primeira sem depender da segunda.
 */
class ModuleBoundaryTest {

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(new ImportOption.DoNotIncludeTests())
                .importPackages("com.techpix");
    }

    @Test
    void internalPackagesAreInvisibleToOtherModules() {
        for (String module : new String[]{"account", "fraud", "ledger", "notification", "payment"}) {
            ArchRule rule = noClasses()
                    .that().resideOutsideOfPackage("com.techpix." + module + "..")
                    .should().dependOnClassesThat().resideInAPackage("com.techpix." + module + ".internal..")
                    .as("nenhum modulo pode acessar com.techpix." + module + ".internal");
            rule.check(classes);
        }
    }

    @Test
    void modulesOnlyDependOnAllowedModules() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("payment").definedBy("com.techpix.payment..")
                .layer("account").definedBy("com.techpix.account..")
                .layer("fraud").definedBy("com.techpix.fraud..")
                .layer("ledger").definedBy("com.techpix.ledger..")
                .layer("notification").definedBy("com.techpix.notification..")
                .layer("shared").definedBy("com.techpix.shared..")
                // Payment orquestra: e o unico que conhece os outros.
                .whereLayer("payment").mayNotBeAccessedByAnyLayer()
                .whereLayer("account").mayOnlyBeAccessedByLayers("payment")
                .whereLayer("fraud").mayOnlyBeAccessedByLayers("payment")
                .whereLayer("ledger").mayOnlyBeAccessedByLayers("payment")
                .whereLayer("notification").mayOnlyBeAccessedByLayers("payment")
                // shared e infraestrutura: qualquer um usa, ele nao usa ninguem.
                .whereLayer("shared").mayOnlyBeAccessedByLayers("payment", "account", "fraud", "ledger", "notification")
                .check(classes);
    }

    @Test
    void fraudKnowsNothingAboutPayment() {
        // A regra mais importante para a extracao: Fraud nao pode depender de Payment.
        // Se dependesse, extrair Fraud significaria copiar Payment junto.
        noClasses().that().resideInAPackage("com.techpix.fraud..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.techpix.payment..", "com.techpix.account..", "com.techpix.ledger..", "com.techpix.notification..")
                .check(classes);
    }

    @Test
    void noCyclesBetweenModules() {
        slices().matching("com.techpix.(*)..").should().beFreeOfCycles().check(classes);
    }

    @Test
    void sharedDependsOnNoModule() {
        noClasses().that().resideInAPackage("com.techpix.shared..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.techpix.payment..", "com.techpix.account..", "com.techpix.fraud..", "com.techpix.ledger..", "com.techpix.notification..")
                .check(classes);
    }
}
