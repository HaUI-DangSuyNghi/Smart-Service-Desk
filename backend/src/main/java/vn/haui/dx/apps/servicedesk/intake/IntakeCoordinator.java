package vn.haui.dx.apps.servicedesk.intake;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import vn.haui.dx.apps.servicedesk.Ticket;
import vn.haui.dx.apps.servicedesk.TicketService;
import vn.haui.dx.apps.servicedesk.intelligence.ClassifierConfiguration;
import vn.haui.dx.apps.servicedesk.intelligence.ClassifierProcessingGate;
import vn.haui.dx.apps.servicedesk.intelligence.TicketAssessmentInputAssembler;
import vn.haui.dx.apps.servicedesk.intelligence.TicketContextualAssessmentService;
import vn.haui.dx.apps.servicedesk.intelligence.assessment.AssessmentPolicy;
import vn.haui.dx.apps.servicedesk.intelligence.context.*;
import vn.haui.dx.hpdi.common.api.CapabilityException;
import vn.haui.dx.hpdi.human.api.ActivityJournal;
import vn.haui.dx.hpdi.human.api.BusinessActivity;
import vn.haui.dx.hpdi.intelligence.api.BackgroundProcessing;
import vn.haui.dx.hpdi.process.api.ProcessCapability;

/** CPU screening and snapshot sealing; inference runs outside DB transactions. */
@Service
public class IntakeCoordinator {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final TransactionTemplate transaction;
    private final TicketService tickets;
    private final IntakeAdmission admission;
    private final ContextEvaluationGuard guard;
    private final ContextSlotExtractor extractor;
    private final ContextReadinessPolicy readiness;
    private final DuplicatePreflightService duplicates;
    private final TicketContextualAssessmentService assessments;
    private final TicketAssessmentInputAssembler inputs;
    private final ClassifierConfiguration.Settings model;
    private final ContextProcessingGate contextRights;
    private final ClassifierProcessingGate modelRights;
    private final AssessmentPolicy assessmentPolicy;
    private final ProcessCapability processes;
    private final ActivityJournal audit;
    private final vn.haui.dx.apps.servicedesk.TicketAlerts alerts;
    private final BackgroundProcessing processing;
    private final List<String> workspaces;
    private final boolean scheduled;
    private final vn.haui.dx.hpdi.human.api.HumanContext human;
    private record Plan(ContextReadinessView readiness, DuplicatePreflightService.View duplicates,
            String identity, String grants, String status, String reason, String taskId) {}

    public IntakeCoordinator(JdbcTemplate jdbc, ObjectMapper json, PlatformTransactionManager manager,
            TicketService tickets, IntakeAdmission admission, ContextEvaluationGuard guard, ContextSlotExtractor extractor,
            ContextReadinessPolicy readiness, DuplicatePreflightService duplicates, TicketContextualAssessmentService assessments,
            TicketAssessmentInputAssembler inputs, ClassifierConfiguration.Settings model, ContextProcessingGate contextRights,
            ClassifierProcessingGate modelRights, AssessmentPolicy assessmentPolicy, ProcessCapability processes,
            ActivityJournal audit, BackgroundProcessing processing, vn.haui.dx.hpdi.human.api.HumanContext human,
            @Value("${service-desk.background.workspaces:}") List<String> workspaces,
            @Value("${service-desk.background.scheduled:true}") boolean scheduled, vn.haui.dx.apps.servicedesk.TicketAlerts alerts) {
        this.jdbc = jdbc;
        this.alerts = alerts;
        this.json = json;
        this.transaction = new TransactionTemplate(manager);
        this.tickets = tickets;
        this.admission = admission;
        this.guard = guard;
        this.extractor = extractor;
        this.readiness = readiness;
        this.duplicates = duplicates;
        this.assessments = assessments;
        this.inputs = inputs;
        this.model = model;
        this.contextRights = contextRights;
        this.modelRights = modelRights;
        this.assessmentPolicy = assessmentPolicy;
        this.processes = processes;
        this.audit = audit;
        this.processing = processing;
        this.human = human;
        this.workspaces = workspaces.stream().filter(value -> !value.isBlank()).distinct().toList();
        this.scheduled = scheduled;
    }

    public boolean enrolled(Ticket ticket) { return admission.find(ticket.workspace(), ticket.id()) != null; }
    public IntakeAdmission.View state(String workspace, UUID id) { return admission.find(workspace, id); }

    @Scheduled(fixedDelayString = "${serviceDesk.intake.poll-ms:60000}")
    public void poll() {
        if (!scheduled) return;
        for (String workspace : workspaces) {
            var ids = jdbc.query("select ticket_id from servicedesk_intake_admission where workspace=? and status='NEW' "
                    + "order by deadline_at,ticket_id limit 50", (row, index) -> row.getObject(1, UUID.class), workspace);
            for (UUID id : ids) {
                try { processing.run(workspace, () -> screen(workspace, id)); }
                catch (RuntimeException failure) {
                    var state = admission.find(workspace, id);
                    if (state != null) manual(workspace, id, state.version(), "SERVICE_IDENTITY_UNAVAILABLE");
                }
            }
        }
    }

    public IntakeAdmission.View screen(String workspace, UUID id) {
        tickets.getForIntelligence(workspace, id);
        var state = admission.find(workspace, id);
        if (state == null) throw CapabilityException.notFound("Intake admission not found");
        if (!"NEW".equals(state.status())) return state;
        try {
            Plan plan = guard.evaluate(workspace, id, session -> {
                if (session.shadow()) throw CapabilityException.forbidden("Shadow cannot release intake");
                var ready = readiness.evaluate(session.snapshot(), extractor.extract(session.snapshot()),
                        (int) Math.min(session.snapshot().contextRevision(), readiness.maxRounds()));
                if (ready.overall() == ContextReadinessView.State.MANUAL_REVIEW_REQUIRED) {
                    return new Plan(ready, null, null, null, "MANUAL_REQUIRED", "SAFETY_OR_MANUAL_POLICY", null);
                } else if (ready.overall() == ContextReadinessView.State.ENRICHMENT_REQUIRED) {
                    return new Plan(ready, null, null, null, "NEEDS_ENRICHMENT", "REQUIRED_CONTEXT_MISSING", null);
                } else {
                    // Stage A is CPU advisory. DRAFT duplicate policy never hard-holds or auto-merges.
                    var preflight = duplicates.probe(workspace, id);
                    String grants = contextRights.verifyContextual(workspace, session.scope(), readiness.digest(), inputs.policyDigest());
                    if (model.mode() == ClassifierConfiguration.Mode.ASSISTED) {
                        grants += ":" + modelRights.verifyAssessment(workspace, assessmentPolicy.bundleDigest());
                    }
                    String processingDigest = ContextPolicyCatalog.digest(grants);
                    String policy = policyDigest();
                    String identity = ContextPolicyCatalog.digest(workspace + ":" + id + ":" + state.reviewRound() + ":"
                            + session.snapshot().contextRevision() + ":" + session.snapshot().digest() + ":" + policy + ":" + processingDigest);
                    String task = processes.timeline(workspace, session.ticket().process().processInstanceId()).currentTasks().stream()
                            .filter(value -> List.of("review", "intake").contains(value.key())).findFirst()
                            .orElseThrow(() -> CapabilityException.conflict("Ticket is not awaiting review")).id();
                    return new Plan(ready, preflight, identity, processingDigest, "FINAL_ASSESSMENT_QUEUED", "SNAPSHOT_SEALED", task);
                }
            });
            saveScreen(workspace, state, plan.readiness(), plan.duplicates(), plan.identity(), plan.grants(), plan.status(), plan.reason(), plan.taskId());
        } catch (RuntimeException failure) {
            if (!human.currentActor(workspace).hasRole("assessment-worker")) throw failure;
            manual(workspace, id, state.version(), "PROCESSING_OR_CONTEXT_UNAVAILABLE");
        }
        return admission.find(workspace, id);
    }

    private void saveScreen(String workspace, IntakeAdmission.View previous, ContextReadinessView ready,
            DuplicatePreflightService.View preflight, String identity, String grants,
            String next, String reason, String taskId) {
        transaction.executeWithoutResult(status -> {
            lock(previous.ticketId());
            int changed = jdbc.update("update servicedesk_intake_admission a set status=?,readiness_json=cast(? as jsonb),"
                    + "duplicate_json=cast(? as jsonb),sealed_revision=?,context_digest=?,attempt_key=?,processing_digest=?,"
                    + "review_task_id=?,policy_digest=?,reason=?,version=version+1,updated_at=clock_timestamp() "
                    + "where ticket_id=? and version=? and status='NEW' and context_revision=? "
                    + "and exists(select 1 from servicedesk_ticket t where t.id=a.ticket_id and t.context_revision=a.context_revision "
                    + "and (t.awaiting_review or a.process_stage='intake'))",
                    next, encode(ready), preflight == null ? null : encode(preflight), identity == null ? null : ready.contextRevision(),
                    ready.contextDigest(), identity, grants, taskId, policyDigest(), reason, previous.ticketId(), previous.version(), ready.contextRevision());
            if (changed != 1) throw CapabilityException.conflict("Intake changed before seal");
            if ("MANUAL_REQUIRED".equals(next) || preflight != null && preflight.incidentSignal()) {
                jdbc.update("insert into servicedesk_intake_notice(id,ticket_id,workspace,context_revision,kind) values (?,?,?,?,?) on conflict do nothing",
                        UUID.randomUUID(), previous.ticketId(), workspace,
                        ready.contextRevision(), "MANUAL_REQUIRED".equals(next) ? "SAFETY_OR_HUMAN_INTAKE" : "INCIDENT_POSSIBLE");
            }
            if (identity != null) {
                jdbc.update("update servicedesk_assessment set status='PENDING',result=null,error_code=null,attempts=0,"
                        + "available_at=clock_timestamp(),lease_token=null,lease_until=null where ticket_id=?", previous.ticketId());
            }
        });
    }

    /** Invoked only by the existing leased background worker under its real service identity. */
    public void processFinal(Ticket ticket, UUID lease, Runnable acknowledge) {
        var before = admission.find(ticket.workspace(), ticket.id());
        if (before == null) throw CapabilityException.conflict("Missing intake");
        String identity = jdbc.queryForObject("select attempt_key from servicedesk_intake_admission where ticket_id=?", String.class, ticket.id());
        if (identity == null) throw CapabilityException.conflict("Missing sealed assessment identity");
        boolean admitted = Boolean.TRUE.equals(transaction.execute(status -> {
            lock(ticket.id());
            int claimed = jdbc.update("update servicedesk_intake_admission set status='ASSESSING',lease_token=?,"
                    + "lease_until=clock_timestamp()+interval '30 seconds',version=version+1 where ticket_id=? "
                    + "and attempt_key=? and status in ('FINAL_ASSESSMENT_QUEUED','ASSESSING') "
                    + "and deadline_at>clock_timestamp()", lease, ticket.id(), identity);
            if (claimed == 0) return false;
            return jdbc.update("insert into servicedesk_final_attempt(identity,ticket_id,context_revision,review_round,state,lease_token) "
                    + "values (?,?,?,?,'RUNNING',?) on conflict do nothing", identity, ticket.id(), before.contextRevision(), before.reviewRound(), lease) == 1;
        }));
        if (!admitted) {
            manual(ticket.workspace(), ticket.id(), admission.find(ticket.workspace(), ticket.id()).version(), "FINAL_ATTEMPT_INTERRUPTED_OR_REPLAYED");
            transaction.executeWithoutResult(status -> acknowledge.run());
            return;
        }
        TicketContextualAssessmentService.View result = null;
        String failure = null;
        try {
            String grants = contextRights.verifyContextual(ticket.workspace(), ContextProcessingGate.WORKSPACE_REVIEW, readiness.digest(), inputs.policyDigest());
            if (model.mode() == ClassifierConfiguration.Mode.ASSISTED) {
                grants += ":" + modelRights.verifyAssessment(ticket.workspace(), assessmentPolicy.bundleDigest());
            }
            String sealedGrants = jdbc.queryForObject("select processing_digest from servicedesk_intake_admission where ticket_id=?", String.class, ticket.id());
            if (!ContextPolicyCatalog.digest(grants).equals(sealedGrants) || !policyDigest().equals(before.policyDigest())) {
                throw CapabilityException.conflict("Sealed processing grant or policy changed");
            }
            result = assessments.assessFinal(ticket.workspace(), ticket.id(), before.contextRevision(), before.contextDigest());
            tickets.getForIntelligence(ticket.workspace(), ticket.id());
        } catch (RuntimeException unavailable) { failure = "FINAL_ASSESSMENT_UNAVAILABLE"; }
        var completed = result;
        String reason = failure;
        transaction.executeWithoutResult(status -> {
            lock(ticket.id());
            var current = admission.find(ticket.workspace(), ticket.id());
            boolean fresh = "ASSESSING".equals(current.status()) && current.contextRevision() == before.contextRevision()
                    && current.reviewRound() == before.reviewRound() && before.contextDigest().equals(current.contextDigest())
                    && Boolean.TRUE.equals(jdbc.queryForObject("select lease_token=? and attempt_key=? from servicedesk_intake_admission where ticket_id=?",
                            Boolean.class, lease, identity, ticket.id()));
            if (!fresh) {
                jdbc.update("update servicedesk_final_attempt set state='STALE',finished_at=clock_timestamp() where identity=?", identity);
            } else {
                boolean modelReady = completed != null && completed.ai07() != null && completed.ai07().category() != null
                        && List.of("REVIEW_REQUIRED", "MODEL_REUSED_MATERIAL").contains(completed.assessmentStatus())
                        && completed.contextRevision() == before.contextRevision() && before.contextDigest().equals(completed.contextDigest());
                String next = modelReady ? "REVIEW_RELEASED" : "MANUAL_REQUIRED";
                String why = modelReady ? "FINAL_CONTEXT_ASSESSED" : reason == null ? "MODEL_DISABLED_ABSTAIN_OR_DEGRADED" : reason;
                jdbc.update("update servicedesk_final_attempt set state=?,result=cast(? as jsonb),finished_at=clock_timestamp() where identity=?",
                        modelReady ? "DONE" : "DEGRADED", completed == null ? null : encode(completed), identity);
                jdbc.update("update servicedesk_intake_admission set status=?,final_json=cast(? as jsonb),reason=?,"
                        + "lease_token=null,lease_until=null,version=version+1,updated_at=clock_timestamp() where ticket_id=?",
                        next, completed == null ? null : encode(completed), why, ticket.id());
                jdbc.update("insert into servicedesk_intake_receipt(id,ticket_id,operation_id,action,context_revision,review_round,"
                        + "context_digest,policy_digest,payload_digest,actor,reason,final_json,review_task_id) "
                        + "select ?,ticket_id,?,'FINAL_ASSESSMENT',context_revision,review_round,context_digest,policy_digest,?,"
                        + "?,?,final_json,review_task_id from servicedesk_intake_admission where ticket_id=?",
                        UUID.randomUUID(), lease, identity, human.currentActor(ticket.workspace()).subject(), why, ticket.id());
                audit.record(ticket.workspace(), new BusinessActivity("servicedesk.intake.assessed", "servicedesk.ticket", ticket.id().toString(),
                        Map.of("attemptKey", identity, "contextVersion", Long.toString(before.contextRevision()))));
                if (modelReady) alerts.advisory(ticket, identity, completed.ai07().priority());
            }
            acknowledge.run();
        });
    }

    public void manual(String workspace, UUID id, long version, String reason) {
        transaction.executeWithoutResult(status -> {
            lock(id);
            jdbc.update("update servicedesk_intake_admission set status='MANUAL_REQUIRED',reason=?,version=version+1,"
                    + "lease_token=null,lease_until=null where workspace=? and ticket_id=? and version=? "
                    + "and status in ('NEW','NEEDS_ENRICHMENT','FINAL_ASSESSMENT_QUEUED','ASSESSING')", reason, workspace, id, version);
        });
    }

    public IntakeAdmission.View read(String workspace, UUID id) {
        var state = admission.find(workspace, id);
        if (state == null) throw CapabilityException.notFound("Intake admission not found");
        if (state.readiness() == null) return metadata(state);
        try {
            return guard.evaluate(workspace, id, session -> {
                contextRights.verifyContextual(workspace, session.scope(), readiness.digest(), inputs.policyDigest());
                if (state.finalAssessment() != null && !state.finalAssessment().path("provenance").path("modelDigest").isNull()) {
                    modelRights.verifyAssessment(workspace, assessmentPolicy.bundleDigest());
                }
                if (session.shadow() || state.contextRevision() != session.snapshot().contextRevision()
                        || !session.snapshot().digest().equals(state.contextDigest()) || !policyDigest().equals(state.policyDigest())) return metadata(state);
                boolean reviewer = session.actor().hasRole("coordinator", "agent");
                return new IntakeAdmission.View(state.contractVersion(), id, state.contextRevision(), state.version(), state.reviewRound(),
                        state.status(), state.deadlineAt(), state.sealedRevision(), state.contextDigest(), state.policyDigest(), state.reason(),
                        state.lateNotice(), state.readiness(), reviewer ? state.duplicatePreflight() : null, state.finalAssessment(), state.reviewTaskMayExist(), false);
            });
        } catch (RuntimeException denied) { return metadata(state); }
    }

    private IntakeAdmission.View metadata(IntakeAdmission.View state) {
        return new IntakeAdmission.View(state.contractVersion(), state.ticketId(), state.contextRevision(), state.version(), state.reviewRound(),
                state.status(), state.deadlineAt(), state.sealedRevision(), state.contextDigest(), state.policyDigest(), state.reason(),
                state.lateNotice(), null, null, null, state.reviewTaskMayExist(), false);
    }

    private String policyDigest() {
        return ContextPolicyCatalog.digest(admission.policyDigest() + ":" + readiness.digest() + ":" + inputs.policyDigest() + ":"
                + assessmentPolicy.bundleDigest() + ":" + model.mode() + ":" + model.modelDigest() + ":" + model.policyDigest() + ":" + duplicates.policyDigest());
    }

    private void lock(UUID id) { jdbc.queryForList("select id from servicedesk_ticket where id=? for update", id); }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (java.io.IOException failure) { throw new IllegalStateException("Cannot persist intake result", failure); }
    }
}
