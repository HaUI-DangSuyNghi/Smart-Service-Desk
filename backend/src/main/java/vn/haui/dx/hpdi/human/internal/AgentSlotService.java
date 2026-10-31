package vn.haui.dx.hpdi.human.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.settings.api.WorkPolicy;

@Service
public class AgentSlotService implements AgentSlots {
    /** Đăng ký trước tối đa bấy nhiêu ngày; xem lịch tối đa bấy nhiêu ngày một lần. */
    static final int HORIZON_DAYS = 62;
    static final int MAX_RANGE_DAYS = 62;

    private final JdbcTemplate jdbc;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final AgentDirectory directory;
    private final ActivityJournal audit;
    private final AgentScheduleService schedules;
    private final WorkPolicy work;
    private final Clock clock;
    private final ZoneId zone;
    private final org.springframework.beans.factory.ObjectProvider<Notifications> notifications;

    public AgentSlotService(JdbcTemplate jdbc, HumanContext humans, PermissionGuard permissions,
            AgentDirectory directory, ActivityJournal audit, AgentScheduleService schedules, WorkPolicy work,
            org.springframework.beans.factory.ObjectProvider<Clock> clock,
            @org.springframework.beans.factory.annotation.Value("${dx.scheduling.business-zone:Asia/Ho_Chi_Minh}") String zone,
            org.springframework.beans.factory.ObjectProvider<Notifications> notifications) {
        this.jdbc=jdbc; this.humans=humans; this.permissions=permissions; this.directory=directory;
        this.audit=audit; this.schedules=schedules; this.work=work;
        this.clock=clock.getIfAvailable(Clock::systemUTC); this.zone=ZoneId.of(zone);
        this.notifications=notifications;
    }

    // Tên ca seed là tiếng Anh; tên khác do quản trị đặt thì giữ nguyên
    private static String shiftLabel(String name) {
        return "Morning".equals(name) ? "Sáng" : "Afternoon".equals(name) ? "Chiều" : name;
    }

    /** Thông báo về một ô ca: tới người được nêu và/hoặc điều phối viên, link về trang Ca trực. */
    private void notice(String workspace, String type, String title, Slot slot, Set<String> subjects, boolean coordinators, boolean email) {
        String when=shiftLabel(slot.shift())+" "+slot.date().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"));
        notifications.getObject().notify(new Notifications.Notice(workspace,type,Notifications.Severity.INFO,title,when,"/duty",
            null,null,null,subjects,coordinators,email));
    }

    private record Access(String subject, boolean coordinator) {}

    private Access access(String workspace) {
        var actor=humans.currentActor(workspace);
        if (actor.hasRole("coordinator")) {
            permissions.requireCurrent(workspace,"coordinator",Permissions.TASK_ASSIGN);
            return new Access(actor.subject(),true);
        }
        if (!actor.hasRole("agent")) throw CapabilityException.forbidden("Agent or coordinator required");
        permissions.requireCurrent(workspace,"agent",Permissions.TASK_HANDLE);
        return new Access(actor.subject(),false);
    }

    /** "me" là chính mình; kỹ thuật viên chỉ thao tác lịch của mình, điều phối viên thao tác hộ. */
    private String target(String workspace, Access access, String subject) {
        String target="me".equals(subject) ? access.subject() : subject;
        if (target==null || target.isBlank() || target.length()>100) throw CapabilityException.validation("Invalid subject");
        if (!access.coordinator() && !access.subject().equals(target))
            throw CapabilityException.forbidden("Own schedule required");
        if (directory.currentAgents(workspace,null).stream().noneMatch(agent -> target.equals(agent.subject())))
            throw CapabilityException.forbidden("Current agent membership required");
        return target;
    }

    private static LocalTime cutoff(WorkPolicy.Snapshot settings) {
        return settings.shifts().stream().map(WorkPolicy.Shift::start).min(Comparator.naturalOrder())
            .orElseThrow(() -> new IllegalStateException("No shifts configured"));
    }

    /** Hạn đăng ký / huỷ của kỹ thuật viên: trước giờ bắt đầu ca đầu tiên của ngày đó. */
    private Instant deadline(LocalDate date, WorkPolicy.Snapshot settings) {
        return date.atTime(cutoff(settings)).atZone(zone).toInstant();
    }

    private static boolean weekend(LocalDate date) {
        return date.getDayOfWeek()==DayOfWeek.SATURDAY || date.getDayOfWeek()==DayOfWeek.SUNDAY;
    }

    /** T7, CN hoặc ngày lễ trong Cấu hình: chỉ đăng ký khi bật "cho đăng ký ngày nghỉ". */
    static boolean dayOff(LocalDate date, WorkPolicy.Snapshot settings) {
        return weekend(date) || settings.holidays().stream().anyMatch(holiday -> holiday.date().equals(date));
    }

    /** Hình thức làm việc; chưa đặt là SHIFT (đăng ký từng ca). */
    String mode(String workspace, String subject) {
        return jdbc.query("select mode from hpdi_agent_work_mode where workspace=? and subject=?",
            (row,index) -> row.getString(1),workspace,subject).stream().findFirst().orElse("SHIFT");
    }

    private vn.haui.dx.hpdi.human.api.LiveUpdates live;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    void setLive(vn.haui.dx.hpdi.human.api.LiveUpdates live) { this.live = live; }

    // Phát sau commit nên gọi ở đầu hàm cũng được: lỗi thì rollback, không phát
    private void workChanged(String workspace) {
        if (live != null) live.toChannel(workspace, vn.haui.dx.hpdi.human.api.LiveUpdates.WORK, "WORK_CHANGED", "work", null);
    }

    @Override
    @Transactional(readOnly=true)
    public Policy policy(String workspace) {
        access(workspace);
        var settings=work.current();
        return new Policy(zone.getId(),
            settings.shifts().stream().map(shift -> new ShiftWindow(shift.name(),shift.start(),shift.end())).toList(),
            cutoff(settings),settings.weekendRegistration(),settings.scheduleApproval(),LocalDate.now(clock.withZone(zone)),
            settings.holidays().stream().map(holiday -> new HolidayNote(holiday.date(),holiday.name())).toList());
    }

    @Override
    @Transactional(readOnly=true)
    public List<Slot> list(String workspace, LocalDate from, LocalDate to) {
        var access=access(workspace);
        if (from==null || to==null || to.isBefore(from) || ChronoUnit.DAYS.between(from,to)>=MAX_RANGE_DAYS)
            throw CapabilityException.validation("from <= to and at most " + MAX_RANGE_DAYS + " days required");
        String sql="select * from hpdi_agent_slot where workspace=? and slot_date between ? and ?"
            + (access.coordinator() ? "" : " and subject=?") + " order by slot_date,starts_at,subject";
        return access.coordinator()
            ? jdbc.query(sql,AgentSlotService::slot,workspace,from,to)
            : jdbc.query(sql,AgentSlotService::slot,workspace,from,to,access.subject());
    }

    @Override
    @Transactional
    public Slot register(String workspace, String subject, Registration registration) {
        workChanged(workspace);
        var access=access(workspace);
        String target=target(workspace,access,subject);
        if (registration==null || registration.date()==null || registration.shift()==null)
            throw CapabilityException.validation("date and shift required");
        var settings=work.current();
        var shift=settings.shifts().stream().filter(item -> item.name().equals(registration.shift())).findFirst()
            .orElseThrow(() -> CapabilityException.validation("Unknown shift"));
        LocalDate date=registration.date();
        if (date.isAfter(LocalDate.now(clock.withZone(zone)).plusDays(HORIZON_DAYS)))
            throw CapabilityException.validation("Registration horizon exceeded");
        boolean off=dayOff(date,settings);
        if (off && !settings.weekendRegistration())
            throw CapabilityException.conflict(weekend(date) ? "Weekend registration disabled" : "Holiday registration disabled");
        // Người làm hành chính đã tự trực ngày thường; chỉ đăng ký thêm ngày nghỉ
        if (!off && "OFFICE".equals(mode(workspace,target))) throw CapabilityException.conflict("Office hours agent");
        Instant start=date.atTime(shift.start()).atZone(zone).toInstant();
        Instant end=date.atTime(shift.end()).atZone(zone).toInstant();
        Instant now=clock.instant();
        // Điều phối viên đăng ký hộ được tới hết ca (sửa lỗi gấp); kỹ thuật viên phải trước hạn của ngày
        if (access.coordinator() ? !end.isAfter(now) : !now.isBefore(deadline(date,settings)))
            throw CapabilityException.conflict("Registration closed for this day");
        String status=access.coordinator() || !settings.scheduleApproval() ? "APPROVED" : "PENDING";
        schedules.lock(workspace,target);
        var rows=jdbc.query("insert into hpdi_agent_slot(id,workspace,subject,slot_date,shift_name,starts_at,ends_at,status,"
            + "registered_by,decided_by,decided_at) values(?,?,?,?,?,?,?,?,?,?,?) on conflict do nothing returning *",
            AgentSlotService::slot,UUID.randomUUID(),workspace,target,date,shift.name(),Timestamp.from(start),Timestamp.from(end),
            status,access.subject(),"APPROVED".equals(status) ? access.subject() : null,"APPROVED".equals(status) ? Timestamp.from(now) : null);
        if (rows.isEmpty()) throw CapabilityException.conflict("Shift already registered");
        var slot=rows.get(0);
        record(workspace,"human.schedule.slot.registered",slot);
        if ("PENDING".equals(status)) notice(workspace,"SHIFT_PENDING","Có ca trực chờ duyệt",slot,Set.of(),true,false);
        // Điều phối viên đăng ký hộ: báo cho người được xếp ca
        else if (!target.equals(access.subject())) notice(workspace,"SHIFT_ASSIGNED","Bạn được xếp ca trực",slot,Set.of(target),false,true);
        return slot;
    }

    @Override
    @Transactional
    public void cancel(String workspace, String subject, UUID id) {
        workChanged(workspace);
        var access=access(workspace);
        String target=target(workspace,access,subject);
        var slot=find(workspace,id);
        if (!slot.subject().equals(target)) throw CapabilityException.notFound("Slot not found");
        Instant now=clock.instant();
        if (access.coordinator() ? !slot.endsAt().isAfter(now) : !now.isBefore(deadline(slot.date(),work.current())))
            throw CapabilityException.conflict("Registration closed for this day");
        schedules.lock(workspace,target);
        if (jdbc.update("delete from hpdi_agent_slot where workspace=? and id=?",workspace,id)!=1)
            throw CapabilityException.notFound("Slot not found");
        record(workspace,"human.schedule.slot.cancelled",slot);
    }

    @Override
    @Transactional
    public Slot approve(String workspace, UUID id) {
        workChanged(workspace);
        var access=access(workspace);
        if (!access.coordinator()) throw CapabilityException.forbidden("Coordinator required");
        var rows=jdbc.query("update hpdi_agent_slot set status='APPROVED',decided_by=?,decided_at=? "
            + "where workspace=? and id=? and status='PENDING' returning *",
            AgentSlotService::slot,access.subject(),Timestamp.from(clock.instant()),workspace,id);
        if (rows.isEmpty()) throw CapabilityException.conflict("Pending slot required");
        record(workspace,"human.schedule.slot.approved",rows.get(0));
        notice(workspace,"SHIFT_APPROVED","Ca trực của bạn đã được duyệt",rows.get(0),Set.of(rows.get(0).subject()),false,true);
        return rows.get(0);
    }

    @Override
    @Transactional
    public void reject(String workspace, UUID id) {
        workChanged(workspace);
        var access=access(workspace);
        if (!access.coordinator()) throw CapabilityException.forbidden("Coordinator required");
        var slot=find(workspace,id);
        if (jdbc.update("delete from hpdi_agent_slot where workspace=? and id=? and status='PENDING'",workspace,id)!=1)
            throw CapabilityException.conflict("Pending slot required");
        record(workspace,"human.schedule.slot.rejected",slot);
        notice(workspace,"SHIFT_REJECTED","Ca trực của bạn bị từ chối",slot,Set.of(slot.subject()),false,true);
    }

    @Override
    @Transactional(readOnly=true)
    public List<WorkMode> workModes(String workspace) {
        var access=access(workspace);
        var saved=new java.util.HashMap<String,String>();
        jdbc.query("select subject,mode from hpdi_agent_work_mode where workspace=?",
            (org.springframework.jdbc.core.RowCallbackHandler) row -> saved.put(row.getString(1),row.getString(2)),workspace);
        return directory.currentAgents(workspace,null).stream()
            .filter(agent -> access.coordinator() || access.subject().equals(agent.subject()))
            .sorted(Comparator.comparing(WorkspaceMember::subject))
            .map(agent -> new WorkMode(agent.subject(),saved.getOrDefault(agent.subject(),"SHIFT"))).toList();
    }

    @Override
    @Transactional
    public WorkMode setWorkMode(String workspace, String subject, WorkModeChange change) {
        workChanged(workspace);
        var access=access(workspace);
        // Kỹ thuật viên tự chọn hình thức của mình; điều phối viên đổi hộ (target kiểm tra quyền)
        String target=target(workspace,access,subject);
        if (change==null || !List.of("SHIFT","OFFICE").contains(change.mode()))
            throw CapabilityException.validation("mode must be SHIFT or OFFICE");
        schedules.lock(workspace,target);
        jdbc.update("insert into hpdi_agent_work_mode(workspace,subject,mode,updated_by) values(?,?,?,?)"
            + " on conflict(workspace,subject) do update set mode=excluded.mode,updated_by=excluded.updated_by,updated_at=clock_timestamp()",
            workspace,target,change.mode(),access.subject());
        // Chuyển sang hành chính: ô ngày làm việc chưa kết thúc thành thừa (đã tự trực) nên bỏ;
        // giữ ô đã qua (lịch sử) và ô T7/CN/ngày lễ (trực thêm)
        int removed=0;
        if ("OFFICE".equals(change.mode())) {
            var settings=work.current();
            var redundant=jdbc.query("select id,slot_date from hpdi_agent_slot where workspace=? and subject=? and ends_at>?",
                    (row,index) -> java.util.Map.entry(row.getObject(1,UUID.class),row.getObject(2,LocalDate.class)),
                    workspace,target,Timestamp.from(clock.instant())).stream()
                .filter(slot -> !dayOff(slot.getValue(),settings)).map(java.util.Map.Entry::getKey).toList();
            for (var id:redundant) removed+=jdbc.update("delete from hpdi_agent_slot where workspace=? and id=?",workspace,id);
        }
        audit.record(workspace,new BusinessActivity("human.schedule.mode.changed","human.agent",target,
            Map.of("subjectId",target,"modeKey",change.mode(),"removedSlotsKey",Integer.toString(removed))));
        return new WorkMode(target,change.mode());
    }

    private Slot find(String workspace, UUID id) {
        if (id==null) throw CapabilityException.validation("Slot id required");
        return jdbc.query("select * from hpdi_agent_slot where workspace=? and id=?",AgentSlotService::slot,workspace,id)
            .stream().findFirst().orElseThrow(() -> CapabilityException.notFound("Slot not found"));
    }

    private void record(String workspace, String action, Slot slot) {
        audit.record(workspace,new BusinessActivity(action,"human.agent",slot.subject(),
            Map.of("subjectId",slot.subject(),"slotId",slot.id().toString(),"dateKey",slot.date().toString(),
                // Tên ca do quản trị đặt (có thể có dấu, khoảng trắng): audit chỉ nhận ký tự an toàn
                "shiftKey",slot.shift().replaceAll("[^a-zA-Z0-9:._/@-]","_"),"statusKey",slot.status())));
    }

    private static Slot slot(ResultSet row, int index) throws SQLException {
        return new Slot(row.getObject("id",UUID.class),row.getString("subject"),row.getObject("slot_date",LocalDate.class),
            row.getString("shift_name"),row.getTimestamp("starts_at").toInstant(),row.getTimestamp("ends_at").toInstant(),
            row.getString("status"),row.getString("registered_by"));
    }
}
