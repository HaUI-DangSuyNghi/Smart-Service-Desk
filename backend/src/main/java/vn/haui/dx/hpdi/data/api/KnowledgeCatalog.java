package vn.haui.dx.hpdi.data.api;
import java.util.UUID;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;
/** Knowledge Hub: tài liệu văn bản (text/plain, text/markdown) có nguồn, phiên bản, quyền sử dụng. */
public interface KnowledgeCatalog {
    AuthorizedKnowledge resolve(String workspace, UUID metadataId);
    /** Tìm theo tiêu đề ({@code q} null = tất cả), mới nhất trước; chỉ tài liệu có file văn bản. */
    Page<KnowledgeSummary> search(String workspace, String q, PageRequest page);
}
