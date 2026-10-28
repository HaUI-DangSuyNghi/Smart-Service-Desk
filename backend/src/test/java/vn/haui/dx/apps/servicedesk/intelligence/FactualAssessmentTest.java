package vn.haui.dx.apps.servicedesk.intelligence;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.ProblemAssessor;
import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.SubcategoryResolver;
import vn.haui.dx.apps.servicedesk.intelligence.taxonomy.TaxonomyCatalog;

import java.util.List;
import java.util.Map;

class FactualAssessmentTest {
    private final AssessmentPolicy policy = new AssessmentPolicy(new ObjectMapper());
    private final ProblemAssessor assessor = new ProblemAssessor(policy);
    private final SubcategoryResolver resolver =
            new SubcategoryResolver(new TaxonomyCatalog(new ObjectMapper()));

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "14 máy trong phòng B305 không truy cập được Internet, tiết học bắt đầu sau 8"
                        + " phút|I2|U4|HIGH",
                "1 máy trong phòng B305 không truy cập được Internet; tiết học bắt đầu sau 8"
                        + " phút|I1|U4|HIGH",
                "27 người dùng không đăng nhập được; deadline in 12 minutes|I2|U4|HIGH",
                "2 phòng mất mạng; exam starts in 9 minutes|I4|U4|CRITICAL",
                "một thiết bị không hoạt động; class begins in 0.1 hours|I1|U4|HIGH",
                "hai máy tính bị lỗi; bài kiểm tra diễn ra trong 14 phút|I2|U4|HIGH",
                "🚀 toàn bộ máy trong phòng B305 không kết nối được; thi sau 7 phút|I3|U4|CRITICAL",
                "cả phòng mất mạng; thi sau 16 phút|I3|NULL|NULL",
                "phòng B305 có 14 máy; tiết học bắt đầu sau 8 phút|NULL|U4|NULL",
                "phòng B305 không truy cập được Internet; thi sau 8 phút|NULL|U4|NULL",
                "cả phòng có 14 máy, trong đó một máy bị hỏng|I1|NULL|NULL",
                "phòng có 14 máy, một máy không truy cập được Internet|I1|NULL|NULL",
                "14 máy không bị hỏng|NULL|NULL|NULL",
                "14 máy không có lỗi|NULL|NULL|NULL",
                "nếu 14 máy mất mạng, thi sau 8 phút|NULL|NULL|NULL",
                "nếu cả phòng mất mạng|NULL|NULL|NULL",
                "hôm qua 14 máy bị hỏng, thi sau 8 phút|NULL|NULL|NULL",
                "14 máy hỏng đã khắc phục|NULL|NULL|NULL",
                "14 máy hỏng; thi bắt đầu sau 2 giờ|I2|NULL|NULL",
                "14 máy hỏng; đề thi tải xong trong 8 phút|I2|NULL|NULL",
                "14 máy hỏng; tiết học bắt đầu sau 60 phút, sửa máy trong 8 phút|I2|NULL|NULL",
                "0 máy bị lỗi; exam starts in 8 minutes|NULL|U4|NULL",
                "một máy bị lỗi; nhiều phòng mất mạng|NULL|NULL|NULL",
                "-3 máy bị lỗi|NULL|NULL|NULL",
                "1,500 computers are down|NULL|NULL|NULL",
                "khoảng 14 máy bị lỗi|NULL|NULL|NULL",
                "ít nhất một máy bị lỗi|NULL|NULL|NULL",
                "KHOẢNG 14 MÁY BỊ LỖI|NULL|NULL|NULL"
            })
    void quantitiesAndRelativeDeadlinesRequireObservedRelations(
            String text, String impact, String urgency, String priority) {
        var fields = Map.of("description", text);
        var result = assessor.assess(fields);
        assertEquals(nullable(impact), result.impact().value());
        assertEquals(nullable(urgency), result.urgency().value());
        assertEquals(nullable(priority), result.priority());
        result.evidence().forEach(span -> assertTrue(span.verifies(fields.get(span.field()))));
        result.facts()
                .forEach(
                        fact ->
                                assertTrue(
                                        fact.evidence()
                                                .verifies(fields.get(fact.evidence().field()))));
    }

    @Test
    void exposesCountsAndRelativeTimeAsFactsInsteadOfCategoryGuesses() {
        var result =
                assessor.assess(
                        Map.of(
                                "description",
                                "🚀 14 máy trong phòng B305 không truy cập được Internet; tiết học"
                                        + " bắt đầu sau 8 phút"));
        assertTrue(
                result.facts().stream()
                        .anyMatch(
                                fact ->
                                        fact.slot().equals("affectedCount")
                                                && fact.value().equals("14")));
        assertTrue(
                result.facts().stream()
                        .anyMatch(
                                fact ->
                                        fact.slot().equals("deadlineMinutes")
                                                && fact.value().equals("8")));
        assertEquals("I2", result.impact().value());
    }

    @ParameterizedTest
    @CsvSource(
            delimiter = '|',
            value = {
                "NETWORK|mất Wi-Fi; ứng dụng bị treo|WIFI_CONNECTION|SOFTWARE",
                "SOFTWARE|mất Wi-Fi; ứng dụng bị treo|APPLICATION_ERROR|NETWORK",
                "HARDWARE|máy in không in được; tài khoản bị khóa|PRINTER_FAILURE|ACCOUNT",
                "ACCOUNT|máy in không in được; tài khoản bị khóa|ACCOUNT_LOCKED|HARDWARE"
            })
    void multiSymptomTicketsKeepTheUniqueChildInsideTheModelParent(
            String parent, String text, String child, String related) {
        var result = resolver.resolve(parent, Map.of("title", text));
        assertEquals(child, result.child());
        assertEquals("SUPPORTED_BY_RULE", result.status());
        assertEquals(List.of(related), result.relatedParents());
        assertTrue(
                result.evidence().stream()
                        .allMatch(span -> span.ruleId().equals("CHILD-" + child)));
    }

    @Test
    void foreignSymptomsCannotChooseAmongAmbiguousSiblings() {
        var result =
                resolver.resolve(
                        "NETWORK", Map.of("title", "mất Wi-Fi; mất mạng dây; ứng dụng bị treo"));
        assertNull(result.child());
        assertEquals("AMBIGUOUS", result.status());
        assertEquals(List.of("SOFTWARE"), result.relatedParents());
    }

    private static String nullable(String value) {
        return "NULL".equals(value) ? null : value;
    }
}
