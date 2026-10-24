package vn.haui.dx.hpdi.human.api;

import java.time.Instant;
import vn.haui.dx.hpdi.common.api.Page;
import vn.haui.dx.hpdi.common.api.PageRequest;

/** Nhật ký hoạt động nhìn từ người dùng. */
public interface ActivityFeed {
    /** Thao tác do chính người gọi làm trong workspace, mới nhất trước; mọi thành viên workspace được xem. */
    Page<ActivityEntry> mine(String workspace, PageRequest page);

    /**
     * Thao tác của mọi người trong workspace, mới nhất trước; Core chỉ cho điều phối viên/quản trị nền tảng.
     * Bộ lọc null = không lọc; {@code from} tính cả, {@code to} không tính.
     */
    Page<ActivityEntry> workspace(String workspace, Instant from, Instant to, String action, String actor,
                                  PageRequest page);
}
