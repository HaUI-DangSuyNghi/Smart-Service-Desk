package vn.haui.dx.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import org.junit.jupiter.api.Test;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class TierBoundaryTest {
    @Test void dependencyDirection() {
        var classes = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("vn.haui.dx");
        noClasses().that().resideInAPackage("vn.haui.dx.core..")
            .should().dependOnClassesThat().resideInAnyPackage("vn.haui.dx.hpdi..", "vn.haui.dx.apps..")
            .check(classes);
        noClasses().that().resideInAPackage("vn.haui.dx.apps..")
            .should().dependOnClassesThat().resideInAPackage("vn.haui.dx.core..")
            .check(classes);
        noClasses().that().resideInAPackage("vn.haui.dx.hpdi..api..")
            .should().dependOnClassesThat().resideInAPackage("vn.haui.dx.core..")
            .check(classes);
        noClasses().that().resideInAPackage("vn.haui.dx.hpdi..")
            .should().dependOnClassesThat().resideInAPackage("vn.haui.dx.apps..")
            .allowEmptyShould(true).check(classes);
        noClasses().that().resideInAPackage("vn.haui.dx.hpdi..")
            .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("Core types outside approved public contracts") {
                public boolean test(JavaClass type) {
                    String pkg=type.getPackageName();
                    return pkg.startsWith("vn.haui.dx.core.") && !pkg.matches("vn[.]haui[.]dx[.]core[.][^.]+[.]api([.].*)?")
                        && !pkg.equals("vn.haui.dx.core.common");
                }
            }).check(classes);
        noClasses().that().resideInAPackage("vn.haui.dx.apps..")
            .should().dependOnClassesThat(new DescribedPredicate<JavaClass>("private capability adapters") {
                public boolean test(JavaClass type) {
                    String pkg=type.getPackageName();
                    return pkg.startsWith("vn.haui.dx.hpdi.") && !pkg.matches("vn[.]haui[.]dx[.]hpdi[.][^.]+[.]api([.].*)?");
                }
            }).check(classes);
        noClasses().that().resideInAnyPackage("vn.haui.dx.apps..", "vn.haui.dx.hpdi..")
            .should().dependOnClassesThat().resideInAPackage("vn.haui.dx.core.*.internal..")
            .check(classes);
        noClasses().that().resideOutsideOfPackage("vn.haui.dx.core.workflow.internal..")
            .should().dependOnClassesThat().resideInAPackage("org.flowable..")
            .check(classes);
    }
}
