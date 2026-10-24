package vn.haui.dx.hpdi.human.api;

import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;

/** Nhật ký hoạt động nhìn từ người dùng. */
public interface ActivityFeed {
    /** Thao tác do chính người gọi làm trong workspace, mới nhất trước; mọi thành viên workspace được xem. */
    Page<ActivityEntry> mine(String workspace, PageRequest page);
}
