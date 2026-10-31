package vn.haui.dx.apps.servicedesk;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.process.api.*;
import vn.haui.dx.hpdi.settings.api.WorkPolicy;

/**
 * Chính sách R5 cho lần duyệt mới: mục tiêu, giờ ca, nâng bậc, ngưỡng cảnh báo lấy từ Cấu hình hệ thống;
 * múi giờ, ngày làm việc, ngày nghỉ lấy từ thuộc tính. Mỗi ticket lưu bản chụp lúc duyệt (servicedesk_sla.policy)
 * nên đổi cấu hình không làm đổi hạn của việc đã duyệt.
 */
@Component
public class TicketSlaConfiguration {
    private record Cached(long version, SlaPolicy policy) {}

    private final WorkPolicy work;
    private final String zone;
    private final Set<DayOfWeek> weekdays;
    private final Set<LocalDate> holidays;
    private volatile Cached cached;

    public TicketSlaConfiguration(WorkPolicy work,
            @Value("${service-desk.sla.zone:Asia/Ho_Chi_Minh}") String zone,
            @Value("${service-desk.sla.working-days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") String days,
            @Value("${service-desk.sla.holidays:}") String holidays) {
        this.work = work;
        this.zone = zone;
        this.weekdays = Arrays.stream(days.split(",")).map(String::trim).map(DayOfWeek::valueOf)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
        this.holidays = holidays.isBlank() ? Set.of() : Arrays.stream(holidays.split(",")).map(String::trim)
            .map(LocalDate::parse).collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    /** Chính sách hiện hành; dựng lại khi version cấu hình đổi. */
    public SlaPolicy current() {
        var settings = work.current();
        var value = cached;
        if (value != null && value.version() == settings.version()) return value.policy();
        var policy = policy(settings, zone, weekdays, holidays);
        cached = new Cached(settings.version(), policy);
        return policy;
    }

    static SlaPolicy policy(WorkPolicy.Snapshot settings, String zone, Set<DayOfWeek> weekdays, Set<LocalDate> holidays) {
        var windows = settings.shifts().stream()
            .sorted(Comparator.comparing(WorkPolicy.Shift::start))
            .map(shift -> new BusinessCalendar.Shift(shift.start(), shift.end())).toList();
        var targets = new EnumMap<SlaPolicy.Priority, SlaPolicy.Target>(SlaPolicy.Priority.class);
        for (var priority : SlaPolicy.Priority.values()) {
            var target = settings.targets().get(priority.name());
            // CRITICAL tính liên tục 24/7, mức khác tính theo giờ làm việc
            targets.put(priority, new SlaPolicy.Target(target.startWithinMinutes(), target.resolveWithinMinutes(),
                priority == SlaPolicy.Priority.CRITICAL));
        }
        return new SlaPolicy("r5-settings-v" + settings.version(), new BusinessCalendar(zone, weekdays, windows, holidays),
            targets, settings.escalateAfterRatio(), SlaPolicy.Priority.valueOf(settings.maxEscalatedPriority()),
            settings.waitingExternalCounts(), settings.warnRemainingRatio());
    }
}
