package vn.haui.dx.hpdi.human.internal;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;

@Service
public class AgentScheduleService implements AgentSchedules {
    private final JdbcTemplate jdbc;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final AgentDirectory directory;
    private final ActivityJournal audit;
    private final Clock clock;
    private final String defaultZone;
    /** Bật: chỉ trực khi đang trong ca đã đăng ký; tắt: chưa dùng lịch thì theo công tắc tay (hành vi cũ). */
    private final boolean shiftRequired;
    private final vn.haui.dx.hpdi.settings.api.WorkPolicy work;

    public AgentScheduleService(JdbcTemplate jdbc, HumanContext humans, PermissionGuard permissions,
            AgentDirectory directory, ActivityJournal audit, org.springframework.beans.factory.ObjectProvider<Clock> clock,
            @org.springframework.beans.factory.annotation.Value("${dx.scheduling.business-zone:Asia/Ho_Chi_Minh}") String defaultZone,
            @org.springframework.beans.factory.annotation.Value("${dx.scheduling.shift-required:true}") boolean shiftRequired,
            vn.haui.dx.hpdi.settings.api.WorkPolicy work) {
        this.jdbc=jdbc; this.humans=humans; this.permissions=permissions;
        this.directory=directory; this.audit=audit; this.clock=clock.getIfAvailable(Clock::systemUTC);
        this.defaultZone=ZoneId.of(defaultZone).getId();
        this.shiftRequired=shiftRequired;
        this.work=work;
    }

    private String authorize(String workspace, String subject, boolean write) {
        var actor=humans.currentActor(workspace);
        String target="me".equals(subject) ? actor.subject() : subject;
        if (actor.hasRole("coordinator")) permissions.requireCurrent(workspace,"coordinator",Permissions.TASK_ASSIGN);
        else {
            if (write || !actor.hasRole("agent") || !actor.subject().equals(target))
                throw CapabilityException.forbidden("Coordinator or own agent schedule required");
            permissions.requireCurrent(workspace,"agent",Permissions.TASK_HANDLE);
        }
        if (target==null || target.isBlank() || target.length()>100)
            throw CapabilityException.validation("Invalid subject");
        if (directory.currentAgents(workspace,null).stream().noneMatch(agent -> target.equals(agent.subject())))
            throw CapabilityException.forbidden("Current agent membership required");
        return target;
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Schedule get(String workspace, String subject) {
        return read(workspace,authorize(workspace,subject,false));
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<Schedule> list(String workspace) {
        var actor=humans.currentActor(workspace);
        if (actor.hasRole("coordinator")) permissions.requireCurrent(workspace,"coordinator",Permissions.TASK_ASSIGN);
        else {
            if (!actor.hasRole("agent")) throw CapabilityException.forbidden("Agent required");
            permissions.requireCurrent(workspace,"agent",Permissions.TASK_HANDLE);
        }
        return directory.currentAgents(workspace,null).stream()
            .filter(agent -> actor.hasRole("coordinator") || actor.subject().equals(agent.subject()))
            .sorted(Comparator.comparing(WorkspaceMember::subject)).map(agent -> read(workspace,agent.subject())).toList();
    }

    private Schedule read(String workspace, String subject) {
        var rows=jdbc.queryForList("select version,enabled from hpdi_agent_schedule where workspace=? and subject=?",workspace,subject);
        if (rows.isEmpty()) return new Schedule(subject,0,false,List.of());
        long version=((Number)rows.get(0).get("version")).longValue();
        var shifts=jdbc.query("select starts_at,ends_at,timezone from hpdi_agent_shift where workspace=? and subject=? and version=? order by starts_at",
            (row,index) -> new Shift(row.getTimestamp(1).toInstant(),row.getTimestamp(2).toInstant(),row.getString(3)),workspace,subject,version);
        return new Schedule(subject,version,(Boolean)rows.get(0).get("enabled"),List.copyOf(shifts));
    }

    @Transactional
    public Schedule replace(String workspace, String subject, Change change) {
        String target=authorize(workspace,subject,true);
        if (change==null || change.version()==null || change.version()<0 || change.version()==Long.MAX_VALUE
                || change.enabled()==null || change.shifts()==null || change.shifts().size()>366)
            throw CapabilityException.validation("Version, enabled and at most 366 shifts required");
        var shifts=validate(change.shifts().stream().map(shift -> shift==null ? null : new Shift(shift.startsAt(),shift.endsAt(),
            shift.timezone()==null ? defaultZone : shift.timezone())).toList());
        lock(workspace,target);
        var previous=read(workspace,target);
        if (previous.version()!=change.version()) throw CapabilityException.conflict("Schedule version mismatch");
        long next=previous.version()+1;
        jdbc.update("insert into hpdi_agent_schedule(workspace,subject,version,enabled) values(?,?,?,?)"
            + " on conflict(workspace,subject) do update set version=excluded.version,enabled=excluded.enabled,updated_at=clock_timestamp()",
            workspace,target,next,change.enabled());
        for (var shift:shifts) jdbc.update("insert into hpdi_agent_shift(workspace,subject,version,starts_at,ends_at,timezone) values(?,?,?,?,?,?)",
            workspace,target,next,Timestamp.from(shift.startsAt()),Timestamp.from(shift.endsAt()),shift.timezone());
        audit.record(workspace,new BusinessActivity("human.schedule.changed","human.agent",target,
            Map.of("subjectId",target,"previousVersion",Long.toString(previous.version()),"scheduleVersion",Long.toString(next),
                "enabledKey",change.enabled().toString(),"shiftCountKey",Integer.toString(shifts.size()))));
        return new Schedule(target,next,change.enabled(),shifts);
    }

    static List<Shift> validate(List<Shift> input) {
        for (var shift:input) {
            if (shift==null || shift.startsAt()==null || shift.endsAt()==null || shift.timezone()==null
                    || shift.timezone().length()>100 || !shift.startsAt().isBefore(shift.endsAt())
                    || Duration.between(shift.startsAt(),shift.endsAt()).compareTo(Duration.ofHours(24))>0)
                throw CapabilityException.validation("Each shift requires start < end, at most 24 hours and an IANA timezone");
            try {
                if (!ZoneId.getAvailableZoneIds().contains(shift.timezone())) throw new java.time.DateTimeException("IANA zone required");
                ZoneId.of(shift.timezone());
            }
            catch (java.time.DateTimeException invalid) { throw CapabilityException.validation("Invalid timezone"); }
        }
        var shifts=input.stream().sorted(Comparator.comparing(Shift::startsAt)).toList();
        for (int index=1; index<shifts.size(); index++)
            if (shifts.get(index).startsAt().isBefore(shifts.get(index-1).endsAt()))
                throw CapabilityException.validation("Overlapping shifts");
        return shifts;
    }

    void lock(String workspace, String subject) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> {},"agent-duty:"+workspace+":"+subject);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Effective effective(String workspace, String subject) {
        String target=authorize(workspace,subject,false);
        return evaluate(workspace,target);
    }

    Effective evaluate(String workspace, String subject) {
        var schedule=read(workspace,subject);
        // Mặc định đang trực: chỉ nghỉ khi chính kỹ thuật viên đã tắt
        boolean manual=Boolean.TRUE.equals(jdbc.queryForObject("select not exists(select 1 from hpdi_agent_duty where workspace=? and subject=? and not on_duty)",
            Boolean.class,workspace,subject));
        Instant now=clock.instant();
        // Ô ca đã duyệt đang chạy (đăng ký theo tuần)
        var slot=jdbc.query("select starts_at,ends_at from hpdi_agent_slot where workspace=? and subject=? and status='APPROVED'"
                + " and starts_at<=? and ends_at>? order by ends_at desc limit 1",
            (row,index) -> new Shift(row.getTimestamp(1).toInstant(),row.getTimestamp(2).toInstant(),defaultZone),
            workspace,subject,Timestamp.from(now),Timestamp.from(now)).stream().findFirst().orElse(null);
        // Người làm hành chính: không cần ô đăng ký, trực trong mọi ca của ngày làm việc
        if (slot==null && Boolean.TRUE.equals(jdbc.queryForObject(
                "select exists(select 1 from hpdi_agent_work_mode where workspace=? and subject=? and mode='OFFICE')",
                Boolean.class,workspace,subject)))
            slot=officeShift(work.current(),ZoneId.of(defaultZone),now);
        return project(schedule,manual,now,slot,shiftRequired);
    }

    /** Ca hành chính đang chạy: T2-T6 không phải ngày lễ và giờ nằm trong một ca của Cấu hình; ngoài giờ là null. */
    static Shift officeShift(vn.haui.dx.hpdi.settings.api.WorkPolicy.Snapshot settings, ZoneId zone, Instant now) {
        var date=now.atZone(zone).toLocalDate();
        if (AgentSlotService.dayOff(date,settings)) return null;
        for (var shift:settings.shifts()) {
            Instant start=date.atTime(shift.start()).atZone(zone).toInstant();
            Instant end=date.atTime(shift.end()).atZone(zone).toInstant();
            if (!now.isBefore(start) && now.isBefore(end)) return new Shift(start,end,zone.getId());
        }
        return null;
    }

    static Effective project(Schedule schedule, boolean manual, Instant now) {
        return project(schedule,manual,now,null,false);
    }

    /**
     * Trực thực tế = công tắc tay bật (tắt = nghỉ đột xuất) và đang trong ca: ca của lịch cũ hoặc ô đã duyệt.
     * shiftRequired: không có ca nào đang chạy thì nghỉ, kể cả khi chưa dùng lịch.
     */
    static Effective project(Schedule schedule, boolean manual, Instant now, Shift slot, boolean shiftRequired) {
        var current=schedule.shifts().stream().filter(shift -> schedule.enabled() && !now.isBefore(shift.startsAt()) && now.isBefore(shift.endsAt())).findFirst().orElse(slot);
        boolean scheduled=current!=null;
        boolean enabled=schedule.enabled() || shiftRequired;
        boolean effective=manual && (!enabled || scheduled);
        return new Effective(manual,enabled,scheduled,effective,current==null ? null : current.startsAt(),
            current==null ? null : current.endsAt(),current==null ? null : Math.toIntExact(Duration.between(now,current.endsAt()).toMinutes()),
            schedule.version(),!manual ? "MANUAL_OFF_DUTY" : !effective ? "OUTSIDE_SHIFT" : enabled ? "SCHEDULE_AND_MANUAL" : "LEGACY_MANUAL");
    }
}
