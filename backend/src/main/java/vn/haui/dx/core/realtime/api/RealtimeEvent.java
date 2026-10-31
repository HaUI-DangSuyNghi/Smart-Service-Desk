package vn.haui.dx.core.realtime.api;

import java.time.Instant;

/** Sự kiện "có thay đổi": client nhận thì tải lại qua REST, không mang dữ liệu nghiệp vụ. */
public record RealtimeEvent(String type, String workspace, String resource, String id, Instant at) {
    public static RealtimeEvent of(String type, String workspace, String resource, String id) {
        return new RealtimeEvent(type, workspace, resource, id, Instant.now());
    }
}
