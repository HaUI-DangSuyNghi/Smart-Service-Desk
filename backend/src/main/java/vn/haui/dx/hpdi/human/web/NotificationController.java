package vn.haui.dx.hpdi.human.web;

import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import vn.haui.dx.hpdi.human.api.Notifications;

/** Chuông thông báo của người đang đăng nhập (mọi vai trò, kể cả người gửi yêu cầu). */
@RestController
@RequestMapping("/api/hpdi/v1/workspaces/{workspace}/notifications")
public class NotificationController {
    private final Notifications notifications;
    public NotificationController(Notifications notifications) { this.notifications = notifications; }

    @GetMapping
    public Notifications.Inbox list(@PathVariable String workspace, @RequestParam(defaultValue = "false") boolean unreadOnly,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return notifications.list(workspace, unreadOnly, page, size);
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void read(@PathVariable String workspace, @PathVariable UUID id) { notifications.read(workspace, id); }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void readAll(@PathVariable String workspace) { notifications.readAll(workspace); }
}
