package vn.haui.dx.core.realtime.api;

/**
 * Phát sự kiện qua WebSocket (STOMP). Đang trong transaction thì chỉ phát sau khi commit (rollback thì bỏ),
 * sự kiện trùng trong cùng transaction gộp làm một. Lỗi khi phát không bao giờ làm hỏng nghiệp vụ.
 */
public interface RealtimeEvents {
    /** Kênh riêng của mỗi người; client đăng ký "/user" + USER_QUEUE. */
    String USER_QUEUE = "/queue/events";

    void toUser(String subject, RealtimeEvent event);

    /** destination bắt đầu bằng "/topic/". */
    void toTopic(String destination, RealtimeEvent event);
}
