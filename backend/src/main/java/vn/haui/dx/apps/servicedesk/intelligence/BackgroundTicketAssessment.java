package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketProcessView;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;
import vn.haui.dx.hpdi.intelligence.api.*;

@Service
@EnableScheduling
public class BackgroundTicketAssessment {
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger(BackgroundTicketAssessment.class);
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final IntelligenceCapability intelligence;
    private final BackgroundProcessing processing;
    private final ActivityJournal audit;
    private final TransactionTemplate transaction;
    private final TicketTriageProjection projection;
    private final boolean scheduled;
    private final List<String> workspaces;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeCoordinator> intake;
    private final org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.TicketAlerts> alerts;

    public BackgroundTicketAssessment(JdbcTemplate jdbc, ObjectMapper json, IntelligenceCapability intelligence,
            BackgroundProcessing processing, ActivityJournal audit, PlatformTransactionManager manager,
            @Value("${service-desk.background.scheduled:true}") boolean scheduled,
            @Value("${service-desk.background.workspaces:}") List<String> workspaces,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeCoordinator> intake,
            org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.TicketAlerts> alerts) {
        this.jdbc=jdbc; this.json=json; this.intelligence=intelligence; this.processing=processing;
        this.audit=audit; this.transaction=new TransactionTemplate(manager);
        this.projection=new TicketTriageProjection(json); this.scheduled=scheduled;
        this.workspaces=workspaces.stream().filter(value -> !value.isBlank()).distinct().toList();
        this.intake=intake;
        this.alerts=alerts;
    }

    @Transactional(propagation=Propagation.MANDATORY)
    public void enqueue(Ticket ticket) {
        jdbc.update("insert into servicedesk_assessment(ticket_id) values(?)",ticket.id());
        history(ticket.id(),0,"QUEUED",null);
    }

    public StoredAssessment read(String workspace, UUID id) {
        var rows=jdbc.query("select a.status,a.version,a.result::text,a.error_code from servicedesk_assessment a"
            + " join servicedesk_ticket t on t.id=a.ticket_id where t.workspace=? and t.id=?",(row,index) ->
                new StoredAssessment(row.getString(1),row.getInt(2),decode(row.getString(3)),row.getString(4)),workspace,id);
        return rows.isEmpty()?null:rows.get(0);
    }

    private StoredAssessment.Result decode(String value) {
        try { return value==null?null:json.readValue(value,StoredAssessment.Result.class); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Stored assessment is invalid"); }
    }

    private record Job(Ticket ticket, int attempt, UUID lease, boolean exhausted) {}

    private Job claim() {
        if (workspaces.isEmpty()) return null;
        return transaction.execute(status -> {
            var rows=jdbc.query("select t.*,a.attempts from servicedesk_assessment a join servicedesk_ticket t on t.id=a.ticket_id"
                + " where a.status='PENDING' and a.available_at<=clock_timestamp() and (a.lease_until is null or a.lease_until<=clock_timestamp())"
                + " and (not exists(select 1 from servicedesk_intake_admission i where i.ticket_id=t.id)"
                + " or exists(select 1 from servicedesk_intake_admission i where i.ticket_id=t.id"
                + " and i.status in ('FINAL_ASSESSMENT_QUEUED','ASSESSING')))"
                + " and t.workspace in ("+String.join(",",java.util.Collections.nCopies(workspaces.size(),"?"))+")"
                + " order by a.available_at,a.ticket_id limit 1 for update of a skip locked",(row,index) -> {
                    UUID id=row.getObject("id",UUID.class);
                    String workspace=row.getString("workspace");
                    var ticket=new Ticket(id,workspace,row.getString("creator"),row.getObject("client_request_id",UUID.class),
                        row.getString("title"),row.getString("description"),row.getTimestamp("created_at").toInstant(),
                        new TicketProcessView(row.getString("process_instance_id"),row.getString("process_key"),id.toString(),workspace));
                    int attempts=row.getInt("attempts");
                    return new Job(ticket,Math.min(3,attempts+1),UUID.randomUUID(),attempts==3);
                },workspaces.toArray());
            if (rows.isEmpty()) return null;
            var job=rows.get(0);
            jdbc.update("update servicedesk_assessment set attempts=?,lease_token=?,lease_until=clock_timestamp()+interval '30 seconds' where ticket_id=?",
                job.attempt(),job.lease(),job.ticket().id());
            history(job.ticket().id(),job.attempt(),"CLAIMED",null);
            return job;
        });
    }

    @Scheduled(fixedDelayString="${service-desk.background.poll-ms:1000}")
    public void poll() {
        if (!scheduled) return;
        try { processOne(); }
        catch (RuntimeException failure) { LOG.warn("Assessment job transaction failed; lease recovery remains enabled"); }
    }

    public boolean processOne() {
        var job=claim();
        if (job==null) return false;
        try {
            if (job.exhausted()) { transaction.executeWithoutResult(status -> finish(job,null,"RETRY_EXHAUSTED",false)); return true; }
            processing.run(job.ticket().workspace(),() -> {
                processing.authorize(job.ticket().workspace(),job.ticket().creator());
                if (intake.getObject().enrolled(job.ticket())) {
                    intake.getObject().processFinal(job.ticket(), job.lease(),
                            () -> finish(job, null, "USE_FINAL_CONTEXT_ASSESSMENT", false));
                    return;
                }
                var suggestion=intelligence.suggest(job.ticket().workspace(),new TriageInput("service-desk","0.3.0",job.ticket().title(),job.ticket().description()));
                var triage=projection.project(suggestion,job.ticket());
                if (!"SUCCESS".equals(triage.status())) throw IntelligenceFailure.unavailable();
                var source=json.valueToTree(suggestion.attributes());
                var runtime=source.path("runtime");
                var result=new StoredAssessment.Result(source.path("contractVersion").asText(),runtime.path("profileVersion").asText(),
                    runtime.path("engine").asText(),runtime.path("rulesVersion").asText(null),runtime.path("modelVersion").asText(null),
                    triage.category(),triage.priority(),triage.impact(),triage.urgency(),triage.reasonCode(),triage.inputEvidence(),triage.explanation(),
                    triage.needsReview(),"INSUFFICIENT_INFORMATION".equals(triage.reasonCode())?List.of(
                        "Which device or service is affected?","How many users are affected and is work blocked?"):List.of());
                transaction.executeWithoutResult(status -> {
                    processing.authorize(job.ticket().workspace(),job.ticket().creator());
                    if (finish(job,result,null,false))
                        audit.record(job.ticket().workspace(),new BusinessActivity("servicedesk.assessment.ready","servicedesk.ticket",
                            job.ticket().id().toString(),java.util.Map.of("assessmentVersion","1")));
                });
            });
        } catch (RuntimeException failure) {
            if (intake.getObject().enrolled(job.ticket())) {
                var state = intakeState(job.ticket());
                if (state != null) intake.getObject().manual(job.ticket().workspace(), job.ticket().id(), state.version(), "FINAL_PROCESSING_UNAVAILABLE");
            }
            String code=failure instanceof IntelligenceFailure intelligenceFailure ? intelligenceFailure.code()
                : failure instanceof CapabilityException ? "AUTHORIZATION_REVOKED" : "PROCESSING_FAILED";
            boolean retry=failure instanceof IntelligenceFailure intelligenceFailure ? intelligenceFailure.status()>=503
                : !(failure instanceof CapabilityException);
            transaction.executeWithoutResult(status -> finish(job,null,code,retry));
        }
        return true;
    }

    private vn.haui.dx.apps.servicedesk.intake.IntakeAdmission.View intakeState(Ticket ticket) {
        return intake.getObject().state(ticket.workspace(), ticket.id());
    }

    private boolean finish(Job job, StoredAssessment.Result result, String error, boolean retry) {
        String state=result!=null?"READY":retry && job.attempt()<3?"PENDING":"DEGRADED";
        String encoded=null;
        try { if (result!=null) encoded=json.writeValueAsString(result); }
        catch (JsonProcessingException failure) { throw new IllegalStateException("Assessment serialization failed"); }
        int count=jdbc.update("update servicedesk_assessment set status=?,result=cast(? as jsonb),error_code=?,lease_token=null,lease_until=null,"
            + " available_at=clock_timestamp()+(? * interval '1 second'),updated_at=clock_timestamp()"
            + " where ticket_id=? and status='PENDING' and lease_token=? and lease_until>clock_timestamp()",
            state,encoded,error,job.attempt()==1?5:30,job.ticket().id(),job.lease());
        if (count==1) history(job.ticket().id(),job.attempt(),state,error);
        if (count==1 && result!=null) alerts.getObject().advisory(job.ticket(), "legacy-v1", result.priority());
        return count==1;
    }

    private void history(UUID id, int attempt, String event, String error) {
        jdbc.update("insert into servicedesk_assessment_history(ticket_id,attempt,event,error_code) values(?,?,?,?)",id,attempt,event,error);
    }
}
