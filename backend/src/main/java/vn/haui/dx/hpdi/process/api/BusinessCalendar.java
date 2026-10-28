package vn.haui.dx.hpdi.process.api;

import java.time.*;
import java.util.*;

public record BusinessCalendar(String zone, Set<DayOfWeek> days, List<Shift> shifts, Set<LocalDate> holidays) {
    public record Shift(LocalTime start, LocalTime end) {
        public Shift {
            if (start == null || end == null || !start.isBefore(end)) throw new IllegalArgumentException("Invalid shift");
        }
    }
    public BusinessCalendar {
        ZoneId.of(zone);
        days = Set.copyOf(days);
        holidays = Set.copyOf(holidays);
        shifts = shifts.stream().sorted(Comparator.comparing(Shift::start)).toList();
        if (days.isEmpty() || shifts.isEmpty()) throw new IllegalArgumentException("Empty business calendar");
        for (int index = 1; index < shifts.size(); index++)
            if (shifts.get(index).start().isBefore(shifts.get(index - 1).end())) throw new IllegalArgumentException("Overlapping shifts");
    }
    public boolean working(Instant instant) {
        var local = instant.atZone(ZoneId.of(zone));
        return days.contains(local.getDayOfWeek()) && !holidays.contains(local.toLocalDate())
            && intervals(local.toLocalDate()).stream().anyMatch(interval -> !instant.isBefore(interval.start()) && instant.isBefore(interval.end()));
    }
    private record Interval(Instant start, Instant end) {}
    private List<Interval> intervals(LocalDate date) {
        var ordered = shifts.stream().map(shift -> new Interval(
            date.atTime(shift.start()).atZone(ZoneId.of(zone)).toInstant(),
            date.atTime(shift.end()).atZone(ZoneId.of(zone)).toInstant()))
            .filter(interval -> interval.start().isBefore(interval.end())).sorted(Comparator.comparing(Interval::start)).toList();
        var result = new ArrayList<Interval>();
        for (var interval : ordered) {
            var start = interval.start();
            var end = interval.end();
            if (!result.isEmpty() && !start.isAfter(result.get(result.size() - 1).end())) {
                var previous = result.remove(result.size() - 1);
                start = previous.start();
                if (previous.end().isAfter(end)) end = previous.end();
            }
            result.add(new Interval(start, end));
        }
        return result;
    }
    public Instant add(Instant from, Duration duration, boolean continuous) {
        if (duration.isNegative() || duration.compareTo(Duration.ofDays(3650)) > 0) throw new IllegalArgumentException("Invalid SLA duration");
        if (duration.isZero() || continuous) return from.plus(duration);
        var date = from.atZone(ZoneId.of(zone)).toLocalDate();
        var remaining = duration;
        for (int count = 0; count < 36600; count++, date = date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek()) || holidays.contains(date)) continue;
            for (var interval : intervals(date)) {
                var start = interval.start();
                var end = interval.end();
                if (start.isBefore(from)) start = from;
                if (!start.isBefore(end)) continue;
                var available = Duration.between(start, end);
                if (remaining.compareTo(available) <= 0) return start.plus(remaining);
                remaining = remaining.minus(available);
            }
        }
        throw new IllegalArgumentException("Calendar exceeds 100-year processing window");
    }
    public Duration between(Instant from, Instant to, boolean continuous) {
        if (to.isBefore(from)) throw new IllegalArgumentException("Negative SLA interval");
        if (continuous) return Duration.between(from, to);
        var date = from.atZone(ZoneId.of(zone)).toLocalDate();
        var last = to.atZone(ZoneId.of(zone)).toLocalDate();
        if (java.time.temporal.ChronoUnit.DAYS.between(date, last) > 36600) throw new IllegalArgumentException("Calendar exceeds 100-year processing window");
        var total = Duration.ZERO;
        for (; !date.isAfter(last); date = date.plusDays(1)) {
            if (!days.contains(date.getDayOfWeek()) || holidays.contains(date)) continue;
            for (var interval : intervals(date)) {
                var start = interval.start();
                var end = interval.end();
                if (start.isBefore(from)) start = from;
                if (end.isAfter(to)) end = to;
                if (start.isBefore(end)) total = total.plus(Duration.between(start, end));
            }
        }
        return total;
    }
}
