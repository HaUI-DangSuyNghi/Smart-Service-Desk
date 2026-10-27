package vn.haui.dx.hpdi.human.internal;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import vn.haui.dx.core.identity.api.WorkspaceDirectory;
import vn.haui.dx.hpdi.common.api.*;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.ProcessContribution;

@Service
public class PersistentWorkAlerts implements WorkAlerts {
    private final JdbcTemplate jdbc;
    private final HumanContext humans;
    private final PermissionGuard permissions;
    private final WorkspaceDirectory directory;
    private final ActivityJournal audit;
    private final org.springframework.beans.factory.ObjectProvider<ProcessContribution> processes;
    public PersistentWorkAlerts(JdbcTemplate jdbc, HumanContext humans, PermissionGuard permissions, WorkspaceDirectory directory, ActivityJournal audit,
            org.springframework.beans.factory.ObjectProvider<ProcessContribution> processes) {
        this.jdbc = jdbc; this.humans = humans; this.permissions = permissions; this.directory = directory; this.audit = audit;
        this.processes = processes;
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(Event event) {
        if (event == null || event.type() == null || event.severity() == null
                || !valid(event.workspace(), 100) || !valid(event.processInstanceId(), 100) || !valid(event.processKey(), 100)
                || !valid(event.businessKey(), 200) || !valid(event.generation(), 200) || !valid(event.label(), 300)
                || event.taskId() != null && !valid(event.taskId(), 100) || event.agent() != null && !valid(event.agent(), 100)
                || !event.coordinators() && event.agent() == null) throw CapabilityException.validation("Invalid alert event");
        var id = UUID.randomUUID();
        int inserted = jdbc.update("insert into hpdi_alert_event(id,workspace,type,severity,process_instance_id,process_key,business_key,task_id,generation,label,coordinators,agent,created_at,retry_at)"
            + " values(?,?,?,?,?,?,?,?,?,?,?,?,current_timestamp,current_timestamp) on conflict(workspace,process_instance_id,generation,type) do nothing",
            id, event.workspace(), event.type().name(), event.severity().name(), event.processInstanceId(), event.processKey(), event.businessKey(),
            event.taskId(), event.generation(), event.label(), event.coordinators(), event.agent());
        if (inserted != 0) audit.recordSystem(event.workspace(), new BusinessActivity("human.alert.created", "human.alert", id.toString(),
            Map.of("processInstanceId", event.processInstanceId(), "eventKey", event.type().name())));
    }
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Inbox list(String workspace, Instant since, boolean unreadOnly, PageRequest page) {
        var recipient = authorize(workspace);
        var parameters = new ArrayList<Object>(List.of(workspace, recipient.subject()));
        parameters.addAll(recipient.processKeys());
        String filter = " from hpdi_alert_recipient r join hpdi_alert_event e on e.workspace=r.workspace and e.id=r.event_id where r.workspace=? and r.subject=?"
            + " and e.process_key in (" + String.join(",", Collections.nCopies(recipient.processKeys().size(), "?")) + ")";
        long unread = jdbc.queryForObject("select count(*)" + filter + " and r.read_at is null", Long.class, parameters.toArray());
        if (since != null) { filter += " and e.created_at>?"; parameters.add(Timestamp.from(since)); }
        if (unreadOnly) filter += " and r.read_at is null";
        long total = jdbc.queryForObject("select count(*)" + filter, Long.class, parameters.toArray());
        parameters.add(page.size()); parameters.add(page.offset());
        var items = jdbc.query("select e.*,r.read_at" + filter + " order by e.created_at desc,e.id desc limit ? offset ?", (row, index) -> {
            var read = row.getTimestamp("read_at");
            return new Alert(row.getObject("id", UUID.class), Type.valueOf(row.getString("type")), Severity.valueOf(row.getString("severity")),
                row.getString("process_instance_id"), row.getString("process_key"), row.getString("business_key"), row.getString("task_id"),
                row.getString("label"), row.getTimestamp("created_at").toInstant(), read == null ? null : read.toInstant());
        }, parameters.toArray());
        return new Inbox(items, page.page(), page.size(), total, unread);
    }
    @Transactional
    public void read(String workspace, UUID id) {
        var recipient = authorize(workspace);
        String subject = recipient.subject();
        var parameters = new ArrayList<Object>(List.of(workspace, id, subject));
        parameters.addAll(recipient.processKeys());
        var rows = jdbc.queryForList("select r.read_at from hpdi_alert_recipient r join hpdi_alert_event e on e.workspace=r.workspace and e.id=r.event_id"
            + " where r.workspace=? and r.event_id=? and r.subject=? and e.process_key in ("
            + String.join(",", Collections.nCopies(recipient.processKeys().size(), "?")) + ") for update of r", parameters.toArray());
        if (rows.isEmpty()) throw CapabilityException.notFound("Alert not found");
        if (rows.get(0).get("read_at") != null) return;
        jdbc.update("update hpdi_alert_recipient set read_at=current_timestamp where workspace=? and event_id=? and subject=?", workspace, id, subject);
        audit.record(workspace, new BusinessActivity("human.alert.read", "human.alert", id.toString(), Map.of()));
    }
    private record Recipient(String subject, List<String> processKeys) {}
    private Recipient authorize(String workspace) {
        var actor = humans.currentActor(workspace);
        directory.refresh(workspace);
        var member = directory.members(workspace).stream().filter(candidate -> actor.subject().equals(candidate.sub())).findFirst()
            .orElseThrow(() -> CapabilityException.forbidden("Current workspace membership required"));
        var roles = member.roles().stream().map(role -> role.value()).collect(java.util.stream.Collectors.toSet());
        if (actor.hasRole("coordinator") && roles.contains("coordinator")) permissions.require(workspace, Permissions.REQUEST_VIEW_ALL);
        else if (actor.hasRole("agent") && roles.contains("agent")) permissions.require(workspace, Permissions.TASK_HANDLE);
        else throw CapabilityException.forbidden("Current agent or coordinator required");
        var keys = processes.stream().flatMap(contribution -> contribution.definitions().stream())
            .filter(definition -> definition.observerRoles().stream().anyMatch(role -> actor.roles().contains(role) && roles.contains(role)))
            .map(definition -> definition.processKey()).distinct().toList();
        return new Recipient(actor.subject(), keys.isEmpty() ? List.of("") : keys);
    }
    private static boolean valid(String value, int maximum) { return value != null && !value.isBlank() && value.length() <= maximum; }
}
