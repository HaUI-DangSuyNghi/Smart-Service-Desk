package vn.haui.dx.hpdi.human.api;

/** Quyền đăng ký kênh realtime của một app; đã kiểm là thành viên workspace trước khi gọi. */
public interface LiveChannelGuard {
    boolean supports(String channel);

    /** Chạy với danh tính người đăng ký (HumanContext dùng được); false hoặc ném lỗi = từ chối. */
    boolean allows(String workspace, String channel);
}
