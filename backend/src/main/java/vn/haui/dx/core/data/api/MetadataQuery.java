package vn.haui.dx.core.data.api;

/** Bộ lọc tìm kiếm; {@code null} = không lọc. {@code q} tìm theo tiêu đề, không phân biệt hoa thường. */
public record MetadataQuery(String q, String source) {}
