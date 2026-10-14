package vn.haui.dx.core.data.api;

import vn.haui.dx.core.identity.api.CurrentUser;

/** App khai báo bean này để quyết định ai được đọc file gắn vào đối tượng của app. */
public interface FileAccessPolicy {
    /** Loại đối tượng, ví dụ {@code servicedesk.ticket}. */
    String resourceType();
    boolean canRead(CurrentUser user, String workspace, String resourceId);
}
