package vn.haui.dx.hpdi.human.api;

/**
 * Báo "có thay đổi" tới màn hình đang mở (WebSocket); client nhận thì tải lại qua REST nên không gửi dữ liệu.
 * Chỉ phát sau khi transaction commit; lỗi khi phát không ảnh hưởng nghiệp vụ.
 */
public interface LiveUpdates {
    /** Kênh chung hàng đợi / ca trực / yêu cầu sửa ET của workspace (agent, coordinator). */
    String WORK = "work";

    /** Riêng một người (mọi workspace của họ dùng chung một kênh). */
    void toUser(String workspace, String subject, String type, String resource, String id);

    /**
     * Kênh /topic/workspaces/{workspace}/{channel}. channel do tầng sở hữu đặt (vd "tickets", "tickets/{id}")
     * và phải có LiveChannelGuard cho phép đăng ký.
     */
    void toChannel(String workspace, String channel, String type, String resource, String id);
}
