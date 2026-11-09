package vn.haui.dx.architecture;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.FragmentsRendering;
import org.springframework.web.servlet.view.xslt.XsltView;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestOnlyMvcTest {
    private static final ArchRule REST_CONTROLLERS = classes()
        .that().areAnnotatedWith(Controller.class).or().areMetaAnnotatedWith(Controller.class)
        .should().beAnnotatedWith(ResponseBody.class).orShould().beMetaAnnotatedWith(ResponseBody.class)
        .because("the backend serves response bodies, not server-rendered views");

    private static final ArchRule NO_VIEW_RENDERING = noClasses().should().dependOnClassesThat()
        .resideInAnyPackage("org.springframework.web.servlet.view..")
        .because("XSLT and SSE view fragments must remain unreachable (CVE-2026-47884/47890)");

    @Test void applicationDoesNotEnableServerSideViewRendering() {
        var production = new ClassFileImporter().withImportOption(new ImportOption.DoNotIncludeTests())
            .importPackages("vn.haui.dx");
        assertFalse(production.isEmpty());
        REST_CONTROLLERS.check(production);
        NO_VIEW_RENDERING.check(production);
        noClasses().should().dependOnClassesThat().haveFullyQualifiedName(ModelAndView.class.getName())
            .check(production);
    }

    @Test void guardsRejectUnsafeFixtures() {
        var importer = new ClassFileImporter();
        assertTrue(REST_CONTROLLERS.evaluate(importer.importClasses(ViewController.class)).hasViolation());
        assertTrue(NO_VIEW_RENDERING.evaluate(importer.importClasses(XsltController.class)).hasViolation());
        assertTrue(NO_VIEW_RENDERING.evaluate(importer.importClasses(FragmentController.class)).hasViolation());
    }

    @Controller
    static class ViewController { }
    static class XsltController { XsltView view; }
    static class FragmentController { FragmentsRendering fragments; }
}
