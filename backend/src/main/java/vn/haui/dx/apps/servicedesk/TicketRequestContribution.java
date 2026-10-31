package vn.haui.dx.apps.servicedesk;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.process.api.RequestContribution;

@Component
public class TicketRequestContribution implements RequestContribution {
    private final JdbcTemplate jdbc;
    private final ApplicationEventPublisher events;
    private final TicketRealtime realtime;
    private final TicketSlaService sla;
    private final long timeout;
    public TicketRequestContribution(JdbcTemplate jdbc,ApplicationEventPublisher events,TicketRealtime realtime,
            TicketSlaService sla,@Value("${serviceDesk.intake.timeout-seconds:86400}") long timeout) {
        this.jdbc=jdbc; this.events=events; this.realtime=realtime; this.sla=sla; this.timeout=timeout;
    }
    public Set<String> processKeys() { return Set.of(ServiceDeskProcess.KEY,ServiceDeskProcess.REVIEWED_KEY,ServiceDeskProcess.INTAKE_KEY); }
    public boolean ownerOnly() { return true; }
    private Map<String,Object> ticket(Context context,boolean lock) {
        var rows=jdbc.queryForList("select * from servicedesk_ticket where workspace=? and process_instance_id=?"
            + (lock ? " for update nowait" : ""),context.process().workspace(),context.process().id());
        if (rows.isEmpty()) throw CapabilityException.notFound("Ticket not found");
        return rows.get(0);
    }
    public Form describe(Context context) {
        var row=ticket(context,false);
        UUID id=(UUID)row.get("id");
        boolean linked=Boolean.TRUE.equals(jdbc.queryForObject("select exists(select 1 from servicedesk_canonical_link"
            + " where workspace=? and state='ACTIVE' and (root_id=? or follower_id=?))",Boolean.class,context.process().workspace(),id,id));
        boolean beforeReview=!context.process().ended() && !context.process().currentTasks().isEmpty()
            && context.process().currentTasks().stream().allMatch(t -> Set.of("intake","review").contains(t.key()))
            && context.process().steps().stream().noneMatch(t -> Set.of("handle","confirm","follower").contains(t.key()));
        long revision=((Number)row.get("context_revision")).longValue();
        boolean owner=context.actor().subject().equals(context.process().initiator());
        boolean approved=Boolean.TRUE.equals(jdbc.queryForObject(
            "select exists(select 1 from servicedesk_review where ticket_id=?)",Boolean.class,id));
        return new Form(Long.toString(revision),beforeReview && !linked && revision<32,
            owner && !linked && beforeReview,
            owner && approved && !linked && !context.process().ended(),
            linked ? "Ticket đang liên kết sự cố; cần xử lý liên kết trước." : "Chủ ticket được sửa hoặc hủy trước khi duyệt, đóng sau khi duyệt. Đóng sẽ kết thúc các công việc còn mở.",
            List.of(new Field("title","Tiêu đề",(String)row.get("title"),200,false),
                new Field("description","Mô tả",(String)row.get("description"),4000,true)));
    }
    public Map<String,Object> update(Context context,Form expected,Map<String,String> values,String reason) {
        var row=ticket(context,true);
        current(row,expected);
        String workspace=context.process().workspace(); UUID id=(UUID)row.get("id");
        long revision=((Number)row.get("context_revision")).longValue()+1;
        jdbc.update("update servicedesk_ticket set title=?,description=?,context_revision=?,content_revision=? where workspace=? and id=?",
            values.get("title"),values.get("description"),revision,revision,workspace,id);
        jdbc.update("update servicedesk_assessment set status='PENDING',result=null,error_code=null,attempts=0,"
            + "available_at=clock_timestamp(),lease_token=null,lease_until=null,updated_at=clock_timestamp() where ticket_id=?",id);
        jdbc.update("delete from servicedesk_ticket_label where ticket_id=?",id);
        jdbc.update("update servicedesk_intake_admission set deadline_at=clock_timestamp()+(? * interval '1 second') where ticket_id=?",timeout,id);
        events.publishEvent(new TicketContextChanged(workspace,id,revision));
        return Map.of();
    }
    public void cancel(Context context,Form expected,String reason) {
        finish(context,expected,false);
    }
    public void close(Context context,Form expected,String reason) {
        finish(context,expected,true);
    }
    private void finish(Context context,Form expected,boolean closing) {
        var row=ticket(context,true);
        current(row,expected);
        UUID id=(UUID)row.get("id"); String workspace=context.process().workspace();
        String status=closing ? "CLOSED" : "CANCELLED";
        jdbc.update("update servicedesk_ticket set awaiting_review=false,context_revision=context_revision+1,"
            +(closing ? "closed_at" : "cancelled_at")+"=clock_timestamp() where id=?",id);
        jdbc.update("update servicedesk_intake_admission set status=?,reason=?,"
            + "final_json=null,readiness_json=null,sealed_revision=null,attempt_key=null,lease_token=null,lease_until=null,"
            + "version=version+1,updated_at=clock_timestamp() where ticket_id=?",status,"REQUEST_"+status,id);
        jdbc.update("update servicedesk_assessment set status='DEGRADED',result=null,error_code=?,"
            + "lease_token=null,lease_until=null,updated_at=clock_timestamp() where ticket_id=?","REQUEST_"+status,id);
        jdbc.update("update servicedesk_final_attempt set state='STALE',finished_at=clock_timestamp() where ticket_id=? and state='RUNNING'",id);
        jdbc.update("update servicedesk_enrichment_case set status='CANCELLED_BY_PROCESS',version=version+1 where ticket_id=?",id);
        jdbc.update("update servicedesk_alert_monitor set active=false where workspace=? and ticket_id=?",workspace,id);
        for (var task:context.process().currentTasks()) if ("handle".equals(task.key()) && ServiceDeskProcess.reviewed(task.processKey()))
            sla.released(workspace,task.id());
        realtime.changed(workspace,id);
    }
    private void current(Map<String,Object> row,Form expected) {
        if (!row.get("context_revision").toString().equals(expected.revision()))
            throw CapabilityException.conflict("Ticket content changed; reload before editing");
    }
}
