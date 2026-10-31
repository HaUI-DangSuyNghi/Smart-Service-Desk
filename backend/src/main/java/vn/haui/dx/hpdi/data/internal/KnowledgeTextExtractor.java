package vn.haui.dx.hpdi.data.internal;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.zip.ZipInputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.SAXParserFactory;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;
import vn.haui.dx.hpdi.common.api.CapabilityException;

public final class KnowledgeTextExtractor {
    public static final Set<String> MEDIA_TYPES = Set.of("text/plain", "text/markdown", "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");
    public static final int MAX_CHARACTERS = 200_000;
    private KnowledgeTextExtractor() {}

    public static String extract(String mediaType, byte[] bytes) {
        try {
            String text = switch (mediaType) {
                case "text/plain", "text/markdown" -> StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
                case "application/pdf" -> pdf(bytes);
                case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" -> docx(bytes);
                default -> throw CapabilityException.validation("Unsupported knowledge document format");
            };
            if (text.isBlank()) throw CapabilityException.validation("No readable text. Scanned PDFs need OCR before upload.");
            if (text.length() > MAX_CHARACTERS) throw CapabilityException.validation("Document exceeds 200000 text characters; split it into smaller files.");
            return text;
        } catch (IOException | javax.xml.parsers.ParserConfigurationException | SAXException error) {
            throw CapabilityException.validation("Cannot read document. Use an unencrypted PDF with text or a valid DOCX.");
        }
    }

    private static String pdf(byte[] bytes) throws IOException {
        try (var document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted() || !document.getCurrentAccessPermission().canExtractContent() || document.getNumberOfPages() > 200)
                throw CapabilityException.validation("PDF must allow text extraction and contain at most 200 pages.");
            var result = new StringBuilder();
            var stripper = new PDFTextStripper();
            for (int page = 1; page <= document.getNumberOfPages(); page++) {
                stripper.setStartPage(page);
                stripper.setEndPage(page);
                result.append(stripper.getText(document));
                if (result.length() > MAX_CHARACTERS) throw CapabilityException.validation("PDF text is too large; split the document.");
            }
            return result.toString();
        }
    }

    private static String docx(byte[] bytes) throws IOException, javax.xml.parsers.ParserConfigurationException, SAXException {
        try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            int total = 0, entries = 0;
            byte[] document = null;
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                if (++entries > 512) throw new IOException("Too many ZIP entries");
                byte[] content = zip.readNBytes(16 * 1024 * 1024 - total + 1);
                total += content.length;
                if (total > 16 * 1024 * 1024) throw new IOException("Expanded ZIP is too large");
                if (entry.getName().equals("word/document.xml")) document = content;
            }
            if (document == null || document.length > 8 * 1024 * 1024) throw new IOException("Missing or oversized document XML");
            var factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            var result = new StringBuilder();
            factory.newSAXParser().parse(new ByteArrayInputStream(document), new DefaultHandler() {
                private boolean inText;
                @Override public void startElement(String uri, String local, String name, Attributes attributes) {
                    inText = local.equals("t");
                    if (local.equals("tab")) result.append('\t');
                    if (local.equals("br")) result.append('\n');
                }
                @Override public void characters(char[] chars, int start, int length) throws SAXException {
                    if (inText) result.append(chars, start, length);
                    if (result.length() > MAX_CHARACTERS) throw new SAXException("Text limit exceeded");
                }
                @Override public void endElement(String uri, String local, String name) {
                    inText = false;
                    if (local.equals("p")) result.append('\n');
                }
            });
            return result.toString();
        }
    }
}
