package vn.haui.dx.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class TierBoundaryTest {
    @Test void dependencyDirection() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("vn.haui.dx");
        noClasses().that().resideInAPackage("vn.haui.dx.core..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.haui.dx.hpdi..", "vn.haui.dx.apps..")
            .check(classes);
        noClasses().that().resideInAPackage("vn.haui.dx.hpdi..")
            .should().dependOnClassesThat().resideInAPackage("vn.haui.dx.apps..")
            .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAnyPackage("vn.haui.dx.apps..", "vn.haui.dx.hpdi..")
            .should().dependOnClassesThat().resideInAPackage("vn.haui.dx.core.*.internal..")
            .check(classes);
        noClasses().that().resideOutsideOfPackage("vn.haui.dx.core.workflow.internal..")
            .should().dependOnClassesThat().resideInAPackage("org.flowable..")
            .check(classes);
    }
}
