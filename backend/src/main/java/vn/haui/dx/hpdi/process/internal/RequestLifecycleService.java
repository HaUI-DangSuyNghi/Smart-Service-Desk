package vn.haui.dx.hpdi.process.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.haui.dx.core.workflow.api.WorkflowService;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.*;
import vn.haui.dx.hpdi.process.api.*;

@Service
public class RequestLifecycleService {
    public record Change(UUID id, long version, String action, String actor, String reason,
            Map<String, String> beforeValues, Map<String, String> afterValues, Instant createdAt) {}
    public record View(String processId, String version, boolean editable, boolean cancellable,
            boolean closable, boolean cancelled, String explanation, List<RequestContribution.Field> fields, List<Change> history) {}
    public record Update(String expectedVersion, Map<String, String> values, String reason) {}
    public record Cancel(String expectedVersion, String reason) {}
    private final WorkflowService workflow;
    private final ProcessCapability processes;
    private final HumanContext human;
    private final PermissionGuard permissions;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final Notifications notices;
    private final LiveUpdates live;
    private final ActivityJournal journal;
    private final Map<String, RequestContribution> contributions;

    public RequestLifecycleService(WorkflowService workflow, ProcessCapability processes, HumanContext human,
            PermissionGuard permissions, JdbcTemplate jdbc, ObjectMapper json, Notifications notices,
            LiveUpdates live, ActivityJournal journal, List<RequestContribution> contributions) {
        this.workflow=workflow; this.processes=processes; this.human=human; this.permissions=permissions;
        this.jdbc=jdbc; this.json=json.copy().enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
        this.notices=notices; this.live=live; this.journal=journal;
        var owners = new HashMap<String, RequestContribution>();
        for (var contribution : contributions) for (var key : contribution.processKeys())
            if (owners.putIfAbsent(key, contribution) != null) throw new IllegalArgumentException("Duplicate request contribution: " + key);
        this.contributions=Map.copyOf(owners);
    }

    @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public View read(String workspace, String id) {
        var context=context(workspace,id);
        var adapter=contributions.get(context.process().processKey());
        var form=adapter == null ? new RequestContribution.Form("",false,false,"Ứng dụng chưa hỗ trợ sửa/hủy.",List.of()) : adapter.describe(context);
        boolean owner=context.actor().subject().equals(context.process().initiator());
        boolean ended=context.process().ended();
        return new View(id, version(context,form), !ended && owner && form.editable(),
            !ended && (owner || context.actor().hasRole("coordinator")) && form.cancellable(),
            !ended && owner && form.closable(),
            context.process().cancelled(), form.explanation(), form.fields(), history(workspace,id));
    }

    @Transactional
    public View update(String workspace, String id, Update request) {
        if (request == null || request.values() == null) throw CapabilityException.validation("Request values required");
        mutate(workspace,id,request.expectedVersion(),request.reason(),request.values(),"UPDATED");
        return read(workspace,id);
    }
    @Transactional
    public View cancel(String workspace, String id, Cancel request) {
        if (request == null) throw CapabilityException.validation("Cancellation required");
        mutate(workspace,id,request.expectedVersion(),request.reason(),Map.of(),"CANCELLED");
        return read(workspace,id);
    }
    @Transactional
    public View close(String workspace, String id, Cancel request) {
        if (request == null) throw CapabilityException.validation("Closure required");
        mutate(workspace,id,request.expectedVersion(),request.reason(),Map.of(),"CLOSED");
        return read(workspace,id);
    }
    private RequestContribution.Context context(String workspace, String id) {
        return new RequestContribution.Context(processes.timeline(workspace,id),human.currentActor(workspace));
    }
    private void mutate(String workspace, String id, String expected, String reason, Map<String,String> values, String action) {
        boolean cancel="CANCELLED".equals(action), close="CLOSED".equals(action);
        if (expected == null || !expected.matches("[a-f0-9]{64}") || reason == null || reason.isBlank() || reason.length()>1000
                || reason.codePoints().anyMatch(c -> Character.isISOControl(c) && c!='\n' && c!='\r'))
            throw CapabilityException.validation("Current request version and reason required");
        try {
            java.util.function.Supplier<Map<String,Object>> operation=() -> {
            var context=context(workspace,id);
            var adapter=contributions.get(context.process().processKey());
            if (adapter == null) throw CapabilityException.conflict("Application does not support request changes");
            boolean owner=context.actor().subject().equals(context.process().initiator());
            if (!owner && (adapter.ownerOnly() || !cancel || !context.actor().hasRole("coordinator")))
                throw CapabilityException.forbidden("Request owner required");
            permissions.require(workspace, owner ? Permissions.REQUEST_CREATE : Permissions.TASK_ASSIGN);
            var form=adapter.describe(context);
            if (!expected.equals(version(context,form))) throw CapabilityException.conflict("Request changed; reload before editing");
            if (cancel ? !form.cancellable() : close ? !form.closable() : !form.editable()) throw CapabilityException.conflict(form.explanation());
            var before=values(form);
            Map<String,Object> variables;
            Map<String,String> after;
            if (cancel) {
                adapter.cancel(context,form,reason.trim()); variables=Map.of(); after=before;
            } else if (close) {
                adapter.close(context,form,reason.trim()); variables=Map.of(); after=before;
            } else {
                validate(values,form);
                after=new TreeMap<>(); values.forEach((key,value)->after.put(key,value.trim()));
                if (before.equals(after)) throw CapabilityException.conflict("Request is unchanged");
                variables=adapter.update(context,form,after,reason.trim());
            }
            long revision=revision(workspace,id)+1;
            jdbc.update("insert into hpdi_request_change(id,workspace,process_id,version,action,actor,reason,before_values,after_values)"
                + " values(?,?,?,?,?,?,?,cast(? as jsonb),cast(? as jsonb))", UUID.randomUUID(),workspace,id,revision,
                action, context.actor().subject(),reason.trim(),encode(before),encode(after));
            String event="REQUEST_"+action;
            var recipients=new HashSet<String>(); recipients.add(context.process().initiator());
            context.process().currentTasks().stream().map(WorkItem::assignee).filter(Objects::nonNull).forEach(recipients::add);
            context.process().steps().stream().map(ProcessTimeline.Step::assignee).filter(Objects::nonNull).forEach(recipients::add);
            notices.notify(new Notifications.Notice(workspace,event,Notifications.Severity.INFO,
                cancel ? "Yêu cầu đã được hủy" : close ? "Yêu cầu đã được đóng" : "Yêu cầu đã được cập nhật",reason.trim(),"/processes/"+id,
                context.process().processKey(),context.process().businessKey(),context.actor().subject(),recipients,true,true));
            for (String subject:recipients) live.toUser(workspace,subject,event,"request",id);
            live.toChannel(workspace,LiveUpdates.WORK,event,"request",id);
            journal.record(workspace,new BusinessActivity(cancel ? "request.cancel" : close ? "request.close" : "request.update","process.request",id,
                Map.of("requestVersion",Long.toString(revision),"processKey",context.process().processKey())));
            return variables;
            };
            if (close) workflow.closeRequest(workspace,id,reason.trim(),operation);
            else workflow.changeRequest(workspace,id,cancel ? reason.trim() : null,operation);
        } catch (org.springframework.dao.DataAccessException busy) {
            if (busy instanceof org.springframework.dao.PessimisticLockingFailureException
                    || busy.getMostSpecificCause() instanceof java.sql.SQLException sql && Set.of("55P03","40P01").contains(sql.getSQLState()))
                throw CapabilityException.conflict("Request is being changed; reload and try again");
            throw busy;
        }
    }
    private void validate(Map<String,String> values, RequestContribution.Form form) {
        if (!values.keySet().equals(values(form).keySet())) throw CapabilityException.validation("Unsupported request fields");
        for (var field:form.fields()) {
            String value=values.get(field.key());
            if (value==null || value.isBlank() || value.length()>field.maxLength() || value.codePoints().anyMatch(c ->
                    Character.isISOControl(c) && !(field.multiline() && (c=='\n'||c=='\r'||c=='\t'))))
                throw CapabilityException.validation("Invalid request field: " + field.key());
        }
    }
    private Map<String,String> values(RequestContribution.Form form) {
        var result=new TreeMap<String,String>(); form.fields().forEach(f -> result.put(f.key(),f.value())); return result;
    }
    private long revision(String workspace,String id) {
        return jdbc.queryForObject("select coalesce(max(version),0) from hpdi_request_change where workspace=? and process_id=?",Long.class,workspace,id);
    }
    private String version(RequestContribution.Context context,RequestContribution.Form form) {
        try {
            String material=encode(List.of(context.process(),form,revision(context.process().workspace(),context.process().id())));
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(material.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }
    private List<Change> history(String workspace,String id) {
        return jdbc.query("select * from hpdi_request_change where workspace=? and process_id=? order by version desc limit 50",
            (row,n)->new Change(row.getObject("id",UUID.class),row.getLong("version"),row.getString("action"),row.getString("actor"),
                row.getString("reason"),decode(row.getString("before_values")),decode(row.getString("after_values")),
                row.getTimestamp("created_at").toInstant()),workspace,id);
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); } catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
    private Map<String,String> decode(String value) {
        try { return json.readValue(value,new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>(){}); }
        catch (java.io.IOException failure) { throw new IllegalStateException(failure); }
    }
}
