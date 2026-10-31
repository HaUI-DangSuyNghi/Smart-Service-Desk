package vn.haui.dx.core.data.api;

import java.util.Set;

/**
 * Bộ lọc tìm kiếm; {@code null} = không lọc. {@code q} tìm theo tiêu đề, không phân biệt hoa thường.
 * {@code fileMediaTypes}: chỉ lấy bản ghi có file thuộc các loại này (ví dụ chỉ tài liệu văn bản).
 */
public record MetadataQuery(String q, String source, String sort, Set<String> fileMediaTypes) {
    public MetadataQuery {
        fileMediaTypes = fileMediaTypes == null ? null : Set.copyOf(fileMediaTypes);
    }
    public MetadataQuery(String q, String source, String sort) { this(q, source, sort, null); }
    public MetadataQuery(String q, String source) { this(q, source, "createdAt,desc"); }
}
