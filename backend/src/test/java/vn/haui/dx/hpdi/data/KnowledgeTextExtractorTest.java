package vn.haui.dx.hpdi.data;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.Test;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.data.internal.KnowledgeTextExtractor;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeTextExtractorTest {
    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private byte[] word(String xml) throws Exception {
        var output = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write(xml.getBytes(StandardCharsets.UTF_8));
        }
        return output.toByteArray();
    }
    @Test void extractsWordParagraphsAndTableTextWithoutFollowingExternalEntities() throws Exception {
        var bytes = word("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body><w:p><w:r><w:t>Office guide</w:t></w:r></w:p><w:tbl><w:tr><w:tc><w:p><w:r><w:t>Network cable</w:t></w:r></w:p></w:tc></w:tr></w:tbl></w:body></w:document>");
        assertEquals("Office guide\nNetwork cable\n", KnowledgeTextExtractor.extract(DOCX, bytes));
        var external = word("<!DOCTYPE document [<!ENTITY external SYSTEM 'file:///etc/passwd'>]><document>&external;</document>");
        assertThrows(CapabilityException.class, () -> KnowledgeTextExtractor.extract(DOCX, external));
    }
    @Test void extractsPdfTextAndRejectsScannedOrMalformedDocuments() throws Exception {
        var bytes = new ByteArrayOutputStream();
        try (var document = new PDDocument()) {
            var page = new PDPage(); document.addPage(page);
            try (var content = new PDPageContentStream(document, page)) {
                content.beginText(); content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(20, 700); content.showText("Restart the network router"); content.endText();
            }
            document.save(bytes);
        }
        assertTrue(KnowledgeTextExtractor.extract("application/pdf", bytes.toByteArray()).contains("Restart the network router"));
        assertThrows(CapabilityException.class, () -> KnowledgeTextExtractor.extract("application/pdf", new byte[]{1, 2}));
        bytes.reset();
        try (var document = new PDDocument()) { document.addPage(new PDPage()); document.save(bytes); }
        assertThrows(CapabilityException.class, () -> KnowledgeTextExtractor.extract("application/pdf", bytes.toByteArray()));
    }
    @Test void boundsAndValidatesText() {
        assertThrows(CapabilityException.class, () -> KnowledgeTextExtractor.extract("text/plain", new byte[]{(byte)0xff}));
        assertThrows(CapabilityException.class, () -> KnowledgeTextExtractor.extract("text/plain", "a".repeat(200001).getBytes(StandardCharsets.UTF_8)));
        assertEquals("Plain text", KnowledgeTextExtractor.extract("text/markdown", "Plain text".getBytes(StandardCharsets.UTF_8)));
    }
}
