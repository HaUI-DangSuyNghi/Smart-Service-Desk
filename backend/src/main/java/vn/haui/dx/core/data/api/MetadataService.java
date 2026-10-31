package vn.haui.dx.core.data.api;

import java.util.UUID;
import vn.haui.dx.core.common.Page;
import vn.haui.dx.core.common.PageRequest;

/** Metadata theo workspace. Thành viên được đọc; agent, coordinator được tạo. Khác workspace → NOT_FOUND. */
public interface MetadataService {
    MetadataRecord create(String workspace, NewMetadata input);
    MetadataRecord get(String workspace, UUID id);
    Page<MetadataRecord> search(String workspace, MetadataQuery query, PageRequest page);
}
