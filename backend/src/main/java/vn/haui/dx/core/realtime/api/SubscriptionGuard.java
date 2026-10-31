package vn.haui.dx.core.realtime.api;

/** Quyền đăng ký kênh /topic/**; kênh không guard nào nhận thì bị từ chối. */
public interface SubscriptionGuard {
    boolean supports(String destination);

    /** Chạy với danh tính người đăng ký (SecurityContext); false hoặc ném lỗi = từ chối. */
    boolean allows(String destination);
}
