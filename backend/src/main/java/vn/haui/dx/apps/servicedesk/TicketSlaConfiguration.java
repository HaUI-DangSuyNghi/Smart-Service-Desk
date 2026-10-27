package vn.haui.dx.apps.servicedesk;

import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import vn.haui.dx.hpdi.process.api.*;
import static vn.haui.dx.hpdi.process.api.SlaPolicy.Priority.*;

@Configuration
public class TicketSlaConfiguration {
    @Bean
    SlaPolicy ticketSlaPolicy(
            @Value("${service-desk.sla.zone:Asia/Ho_Chi_Minh}") String zone,
            @Value("${service-desk.sla.working-days:MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY}") String days,
            @Value("${service-desk.sla.shifts:07:00-11:30,13:00-17:00}") String shifts,
            @Value("${service-desk.sla.holidays:}") String holidays,
            @Value("${service-desk.sla.waiting-external-counts:true}") boolean external,
            @Value("${service-desk.sla.escalate-after-ratio:0.5}") double ratio,
            @Value("${service-desk.sla.max-escalated-priority:HIGH}") SlaPolicy.Priority ceiling) {
        var windows = Arrays.stream(shifts.split(",")).map(value -> {
            var bounds = value.trim().split("-");
            if (bounds.length != 2) throw new IllegalArgumentException("Invalid SLA shift");
            return new BusinessCalendar.Shift(LocalTime.parse(bounds[0]), LocalTime.parse(bounds[1]));
        }).toList();
        var weekdays = Arrays.stream(days.split(",")).map(String::trim).map(DayOfWeek::valueOf).collect(java.util.stream.Collectors.toSet());
        var dates = holidays.isBlank() ? Set.<LocalDate>of() : Arrays.stream(holidays.split(",")).map(String::trim).map(LocalDate::parse).collect(java.util.stream.Collectors.toSet());
        long day = windows.stream().mapToLong(shift -> Duration.between(shift.start(), shift.end()).toMinutes()).sum();
        return new SlaPolicy("r5-v1", new BusinessCalendar(zone, weekdays, windows, dates), Map.of(
            CRITICAL, new SlaPolicy.Target(15, 120, true), HIGH, new SlaPolicy.Target(60, 480, false),
            MEDIUM, new SlaPolicy.Target(240, day * 2, false), LOW, new SlaPolicy.Target(day, day * 5, false)), ratio, ceiling, external);
    }
}
