package vn.haui.dx.apps.servicedesk.intelligence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.sun.net.httpserver.HttpServer;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import vn.haui.dx.core.identity.api.*;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;
import vn.haui.dx.hpdi.intelligence.integration.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"service-desk.background.scheduled=false","service-desk.background.workspaces=background-a"})
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="BACKGROUND_TEST_JDBC_URL",matches=".+/background_assessment")
class BackgroundTicketAssessmentTest {
    private static final String BASE="/api/apps/servicedesk/v1/workspaces/background-a/tickets";
    private static final RSAKey KEY;
    private static final HttpServer SERVER;
    private static volatile boolean invalidToken;
    private static volatile boolean invalidIssuer;
    private static volatile boolean invalidAudience;
    private static volatile int tokenStatus=200;
    static {
        try {
            KEY=new RSAKeyGenerator(2048).keyID("background-test").generate();
            SERVER=HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
            SERVER.createContext("/jwks",exchange -> {
                byte[] bytes=new JWKSet(KEY.toPublicJWK()).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200,bytes.length);
                try(var output=exchange.getResponseBody()) { output.write(bytes); }
            });
            SERVER.createContext("/token",exchange -> {
                exchange.getRequestBody().readAllBytes();
                byte[] bytes=("{\"access_token\":\""+(invalidToken?"invalid.jwt.signature":token())+"\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(tokenStatus,bytes.length);
                try(var output=exchange.getResponseBody()) { output.write(bytes); }
            });
            SERVER.start();
        } catch(Exception failure) { throw new ExceptionInInitializerError(failure); }
    }
    private static String token() {
        var encoder=new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(KEY)));
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(SignatureAlgorithm.RS256).keyId(KEY.getKeyID()).build(),
            JwtClaimsSet.builder().issuer(invalidIssuer?"http://wrong-test/realm":"http://background-test/realm")
                .audience(List.of(invalidAudience?"wrong-audience":"dx-core"))
                .subject("background-worker").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(120))
                .claim("azp","dx-assessment-worker").claim("preferred_username","service-account-dx-assessment-worker")
                .claim("groups",List.of("/workspaces/background-a/assessment-worker", "/workspaces/background-a/alert-worker")).build())).getTokenValue();
    }
    @AfterAll static void stop() { SERVER.stop(0); }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url",() -> System.getenv("BACKGROUND_TEST_JDBC_URL"));
        registry.add("spring.datasource.username",() -> "workflow");
        registry.add("spring.datasource.password",() -> System.getenv("FILE_TEST_DB_PASSWORD"));
        registry.add("dx.storage.root",() -> System.getProperty("java.io.tmpdir")+"/background-assessment-test");
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",() -> "http://127.0.0.1:"+SERVER.getAddress().getPort()+"/jwks");
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri",() -> "http://background-test/realm");
        registry.add("dx.background.token-url",() -> "http://127.0.0.1:"+SERVER.getAddress().getPort()+"/token");
        registry.add("dx.background.client-secret",() -> UUID.randomUUID().toString());
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired BackgroundTicketAssessment worker;
    @Autowired org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.intake.IntakeCoordinator> intake;
    @Autowired org.springframework.beans.factory.ObjectProvider<vn.haui.dx.apps.servicedesk.TicketAlerts> alertProvider;
    @Autowired WorkspaceDirectory directory;
    @Autowired PlatformTransactionManager manager;
    @Autowired vn.haui.dx.hpdi.intelligence.api.IntelligenceCapability capability;
    @Autowired vn.haui.dx.hpdi.intelligence.api.BackgroundProcessing processing;
    @Autowired vn.haui.dx.hpdi.human.api.ActivityJournal audit;
    @Autowired vn.haui.dx.hpdi.human.api.AlertProcessing alertProcessing;
    @Autowired vn.haui.dx.hpdi.human.api.WorkAlerts workAlerts;
    @Autowired vn.haui.dx.hpdi.human.internal.AlertDelivery alertDelivery;
    @MockBean IntelligenceTransport transport;

    @BeforeEach void setup() {
        invalidToken=false;
        invalidIssuer=false;
        invalidAudience=false;
        tokenStatus=200;
        invalidToken=false;
        jdbc.update("delete from servicedesk_assessment_history");
        jdbc.update("delete from servicedesk_assessment");
        when(directory.rolePermissions("background-a")).thenReturn(Map.of(
            "requester",List.of(new DirectoryPermission("request:create",null)),
            "assessment-worker",List.of(new DirectoryPermission("intelligence:process",null)),
            "coordinator",List.of(new DirectoryPermission("task:handle",null),new DirectoryPermission("task:assign",null))));
        when(directory.members("background-a")).thenReturn(List.of(
            new DirectoryMember("background-worker","worker",null,Set.of(new WorkspaceRole("assessment-worker"))),
            new DirectoryMember("owner","owner",null,Set.of(new WorkspaceRole("requester")))));
        doAnswer(invocation -> success(invocation.getArgument(0))).when(transport).triage(any());
    }
    private JsonNode success(TriageRequest.Envelope envelope) {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive(),"AI must run outside transaction");
        assertNotNull(envelope,"TriageRequest.Envelope must not be null");
        assertEquals("background-worker",envelope.trustedContext().subject());
        var result=TriageResponses.success(envelope);
        result.withObject("result").put("category","NETWORK").put("priority","LOW").put("suggestedGroup","IT-NETWORK");
        var explanation=result.withObject("explanation");
        explanation.put("impact","I1").put("urgency","U2");
        explanation.putArray("matchedRules").add("CAT-NET-001").add("IMPACT-USER-001").add("URGENCY-DEFERRED-001").add("PRI-001");
        result.withObject("runtime").put("rulesVersion","0.3.0");
        return result;
    }
    private org.springframework.test.web.servlet.request.RequestPostProcessor as(String subject,String workspace,String role) {
        return jwt().jwt(value -> value.subject(subject).claim("groups",List.of("/workspaces/"+workspace+"/"+role)));
    }
    private UUID create(boolean makeAvailable) throws Exception {
        var result=mvc.perform(post(BASE).with(as("owner","background-a","requester")).contentType("application/json")
            .content(json.writeValueAsString(Map.of("clientRequestId",UUID.randomUUID(),"title","Network support","description","Saved ticket description"))))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.assessment.status").value("PENDING"))
            .andReturn().getResponse().getContentAsString();
        UUID id=UUID.fromString(json.readTree(result).path("id").asText());
        if (makeAvailable) due(id);
        return id;
    }
    private UUID create() throws Exception { return create(true); }
    private String state(UUID id) { return jdbc.queryForObject("select status from servicedesk_assessment where ticket_id=?",String.class,id); }
    private void due(UUID id) { jdbc.update("update servicedesk_assessment set available_at=clock_timestamp()-interval '1 second' where ticket_id=?",id); }

    @Test void signedAlertWorkerRequiresAnIndependentNonBusinessGrantAndRestoresIdentity() {
        var grants = new java.util.HashMap<>(directory.rolePermissions("background-a"));
        grants.put("alert-worker", List.of(new DirectoryPermission("alerts:process", null)));
        grants.put("coordinator", List.of(new DirectoryPermission("request:view-all", null)));
        when(directory.rolePermissions("background-a")).thenReturn(grants);
        when(directory.members("background-a")).thenReturn(List.of(
            new DirectoryMember("background-worker", "worker", null, Set.of(new WorkspaceRole("assessment-worker"), new WorkspaceRole("alert-worker"))),
            new DirectoryMember("duty-without-handle-grant", "duty", null, Set.of(new WorkspaceRole("agent"))),
            new DirectoryMember("alert-coordinator", "coordinator", null, Set.of(new WorkspaceRole("coordinator")))));
        String process = "alert-process-" + UUID.randomUUID();
        new org.springframework.transaction.support.TransactionTemplate(manager).executeWithoutResult(status -> workAlerts.publish(new vn.haui.dx.hpdi.human.api.WorkAlerts.Event(
            "background-a", vn.haui.dx.hpdi.human.api.WorkAlerts.Type.CRITICAL_NEW, vn.haui.dx.hpdi.human.api.WorkAlerts.Severity.CRITICAL,
            process, "room-request", "synthetic-business-key", null, "review-1", "Critical approved", true, null)));
        var worker = new vn.haui.dx.hpdi.human.internal.AlertDeliveryWorker(jdbc, alertProcessing, alertDelivery, true);
        worker.run();
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_alert_recipient r join hpdi_alert_event e on e.id=r.event_id where e.process_instance_id=? and r.subject='alert-coordinator'", Integer.class, process));
        when(directory.supportGroupMembers("background-a", "hardware")).thenReturn(Set.of("duty-without-handle-grant"));
        jdbc.update("insert into hpdi_agent_duty(workspace,subject,on_duty) values('background-a','duty-without-handle-grant',true) on conflict(workspace,subject) do update set on_duty=true");
        alertProcessing.run("background-a", () -> assertEquals(Map.of("duty-without-handle-grant", true), alertProcessing.duty("background-a", "hardware")));
        grants.remove("alert-worker");
        assertThrows(RuntimeException.class, () -> alertProcessing.run("background-a", () -> fail("Revoked grant must not execute")));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }

    @Test void creationNeverInvokesAiAndGetOnlyReadsStoredReadyResult() throws Exception {
        var id=create(false); verifyNoInteractions(transport);
        assertTrue(worker.processOne()); assertEquals("READY",state(id));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        for(int count=0;count<2;count++) mvc.perform(get(BASE+"/"+id).with(as("owner","background-a","requester")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.assessment.result.category").value("NETWORK"))
            .andExpect(jsonPath("$.assessment.result.profileVersion").value("service-desk@0.3.0"))
            .andExpect(jsonPath("$.assessment.result.modelVersion").isEmpty())
            .andExpect(jsonPath("$.state").value("AWAITING_REVIEW"));
        assertFalse(worker.processOne()); verify(transport,times(1)).triage(any());
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.assessment.ready' and resource=?",Integer.class,id.toString()));
        var response=mvc.perform(get(BASE+"/"+id).with(as("owner","background-a","requester"))).andReturn().getResponse().getContentAsString();
        java.nio.file.Files.writeString(java.nio.file.Path.of("target/background-assessment-http-evidence.json"),response);
    }
    @Test void jobIsInvisibleBeforeCommitAndImmediatelyAvailableAfterCommit() throws Exception {
        var executor=Executors.newSingleThreadExecutor();
        try {
            var id=new org.springframework.transaction.support.TransactionTemplate(manager).execute(status -> {
                try {
                    var created=create(false);
                    assertFalse(executor.submit(worker::processOne).get(10,TimeUnit.SECONDS));
                    verifyNoInteractions(transport);
                    return created;
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            });
            assertTrue(executor.submit(worker::processOne).get(10,TimeUnit.SECONDS));
            assertEquals("READY",state(id));
        } finally { executor.shutdownNow(); }
    }
    @Test void automaticModelScopeClaimsAuthorizedJobsOutsideTheLegacyList() throws Exception {
        var reference = create();
        assertTrue(worker.processOne());
        var result = worker.read("background-a", reference).result();
        var id = create();
        var model = mock(BackgroundModelAssessment.class);
        when(model.enabled()).thenReturn(true);
        when(model.automaticWorkspaces()).thenReturn(true);
        when(model.assess(any())).thenReturn(result);
        @SuppressWarnings("unchecked")
        org.springframework.beans.factory.ObjectProvider<BackgroundModelAssessment> provider = mock(org.springframework.beans.factory.ObjectProvider.class);
        when(provider.getObject()).thenReturn(model);
        var automatic = new BackgroundTicketAssessment(jdbc, json, capability, processing, audit, manager,
                false, List.of("unrelated-workspace"), intake, alertProvider, provider);
        assertTrue(automatic.processOne());
        assertEquals("READY", state(id));
        verify(model).assess(argThat(ticket -> ticket.id().equals(id) && ticket.workspace().equals("background-a")));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void futureAvailabilityAndActiveLeaseAreNotClaimed() throws Exception {
        var id=create(false);
        jdbc.update("update servicedesk_assessment set available_at=clock_timestamp()+interval '1 hour' where ticket_id=?",id);
        assertFalse(worker.processOne());
        due(id);
        jdbc.update("update servicedesk_assessment set lease_token=?,lease_until=clock_timestamp()+interval '1 hour' where ticket_id=?",UUID.randomUUID(),id);
        assertFalse(worker.processOne());
        assertEquals(0,jdbc.queryForObject("select attempts from servicedesk_assessment where ticket_id=?",Integer.class,id));
        verifyNoInteractions(transport);
    }
    @Test void transientFailureRetriesThenSucceedsWithoutDuplicateAssessment() throws Exception {
        var id=create(false);
        doThrow(IntelligenceFailure.timeout()).when(transport).triage(any());
        assertTrue(worker.processOne()); assertEquals("PENDING",state(id));
        assertFalse(worker.processOne());
        assertEquals("INTELLIGENCE_TIMEOUT",jdbc.queryForObject("select error_code from servicedesk_assessment where ticket_id=?",String.class,id));
        due(id);
        doAnswer(invocation -> success(invocation.getArgument(0))).when(transport).triage(any());
        assertTrue(worker.processOne()); assertEquals("READY",state(id));
        assertFalse(worker.processOne());
        assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_assessment where ticket_id=?",Integer.class,id));
        assertEquals(2,jdbc.queryForObject("select attempts from servicedesk_assessment where ticket_id=?",Integer.class,id));
        verify(transport,times(2)).triage(any());
    }
    @Test void permanentContractFailureDegradesWithoutRetry() throws Exception {
        var id=create(false);
        doThrow(IntelligenceFailure.invalid()).when(transport).triage(any());
        assertTrue(worker.processOne()); assertEquals("DEGRADED",state(id));
        due(id); assertFalse(worker.processOne());
        assertEquals("INTELLIGENCE_CONTRACT_FAILED",jdbc.queryForObject("select error_code from servicedesk_assessment where ticket_id=?",String.class,id));
        verify(transport,times(1)).triage(any());
    }
    @Test void rollbackLeavesNoOrphanOutbox() throws Exception {
        jdbc.execute("create function reject_background_ticket() returns trigger language plpgsql as $$ begin if new.action='servicedesk.ticket.create' then raise exception 'reject' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger reject_background before insert on audit_event for each row execute function reject_background_ticket()");
        long before=jdbc.queryForObject("select count(*) from servicedesk_ticket",Long.class);
        try {
            mvc.perform(post(BASE).with(as("owner","background-a","requester")).contentType("application/json")
                .content(json.writeValueAsString(Map.of("clientRequestId",UUID.randomUUID(),"title","Rollback","description","No orphan"))))
                .andExpect(status().isConflict());
            assertEquals(before,jdbc.queryForObject("select count(*) from servicedesk_ticket",Long.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_assessment",Integer.class));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_assessment_history",Integer.class));
        } finally { jdbc.execute("drop trigger reject_background on audit_event"); jdbc.execute("drop function reject_background_ticket()"); }
    }
    @Test void timeoutRetriesBackoffAndTerminalDegradedDoNotBlockReview() throws Exception {
        var id=create(); doThrow(IntelligenceFailure.timeout()).when(transport).triage(any());
        for(int attempt=1;attempt<=3;attempt++) {
            assertTrue(worker.processOne());
            assertEquals(attempt,jdbc.queryForObject("select attempts from servicedesk_assessment where ticket_id=?",Integer.class,id));
            String expected=attempt<3?"PENDING":"DEGRADED";
            assertEquals(expected,state(id));
            if ("PENDING".equals(expected)) { assertFalse(worker.processOne()); due(id); }
        }
        mvc.perform(post(BASE+"/"+id+"/review").with(as("coordinator","background-a","coordinator")).contentType("application/json")
            .content("{\"category\":\"HARDWARE\",\"priority\":\"HIGH\",\"assignee\":null,\"reason\":\"Manual override\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.classification.category").value("HARDWARE"))
            .andExpect(jsonPath("$.assessment.status").value("DEGRADED"));
        verify(transport,times(3)).triage(any());
    }
    @Test void twoWorkersDoNotInferTwiceAndCoordinatorOverrideSurvivesLateResult() throws Exception {
        var id=create(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(15,TimeUnit.SECONDS)); return success(invocation.getArgument(0)); }).when(transport).triage(any());
        var executor=Executors.newSingleThreadExecutor();
        try {
            var running=executor.submit(worker::processOne); assertTrue(entered.await(15,TimeUnit.SECONDS));
            assertFalse(worker.processOne());
            mvc.perform(post(BASE+"/"+id+"/review").with(as("coordinator","background-a","coordinator")).contentType("application/json")
                .content("{\"category\":\"HARDWARE\",\"priority\":\"HIGH\",\"assignee\":null,\"reason\":\"Coordinator decides\"}"))
                .andExpect(status().isOk());
            release.countDown(); assertTrue(running.get(20,TimeUnit.SECONDS));
            mvc.perform(get(BASE+"/"+id).with(as("owner","background-a","requester"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.assessment.result.category").value("NETWORK"))
                .andExpect(jsonPath("$.classification.category").value("HARDWARE")).andExpect(jsonPath("$.state").value("ASSIGNED"));
        } finally { release.countDown(); executor.shutdownNow(); }
        verify(transport,times(1)).triage(any());
    }
    @Test void expiredLeaseAfterRestartIsRecoverableAndLastCrashedAttemptTerminates() throws Exception {
        var id=create();
        jdbc.update("update servicedesk_assessment set attempts=1,lease_token=?,lease_until=clock_timestamp()-interval '1 second' where ticket_id=?",UUID.randomUUID(),id);
        var restarted=new BackgroundTicketAssessment(jdbc,json,capability,processing,audit,manager,false,List.of("background-a"),intake,alertProvider);
        assertTrue(restarted.processOne()); assertEquals("READY",state(id));
        var exhausted=create();
        jdbc.update("update servicedesk_assessment set attempts=3,lease_token=?,lease_until=clock_timestamp()-interval '1 second' where ticket_id=?",UUID.randomUUID(),exhausted);
        assertTrue(restarted.processOne()); assertEquals("DEGRADED",state(exhausted)); verify(transport,times(1)).triage(any());
    }
    @Test void revokedCreatorAndForeignWorkspaceCannotReleaseResult() throws Exception {
        var id=create();
        doAnswer(invocation -> {
            var result=success(invocation.getArgument(0));
            when(directory.members("background-a")).thenReturn(List.of(new DirectoryMember("background-worker","worker",null,Set.of(new WorkspaceRole("assessment-worker")))));
            return result;
        }).when(transport).triage(any());
        worker.processOne(); assertEquals("DEGRADED",state(id));
        mvc.perform(get(BASE+"/"+id).with(as("other","background-a","requester"))).andExpect(status().isNotFound());
        mvc.perform(get(BASE+"/"+id).with(as("owner","background-b","requester"))).andExpect(status().isForbidden());
        assertNull(jdbc.queryForObject("select result::text from servicedesk_assessment where ticket_id=?",String.class,id));
    }
    @Test void invalidServiceJwtFailsClosedWithoutCallingAi() throws Exception {
        var id=create(); invalidToken=true; worker.processOne(); verifyNoInteractions(transport);
        assertEquals("DEGRADED",state(id)); assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void rejectedServiceCredentialsArePermanentRatherThanIntelligenceOutages() throws Exception {
        for (int status : List.of(400,401,403)) {
            var id=create(); tokenStatus=status;
            assertTrue(worker.processOne());
            assertEquals("DEGRADED",state(id));
            assertEquals("AUTHORIZATION_REVOKED",jdbc.queryForObject("select error_code from servicedesk_assessment where ticket_id=?",String.class,id));
        }
        verifyNoInteractions(transport);
    }
    @Test void identityProviderOutageIsRetryable() throws Exception {
        var id=create(); tokenStatus=503;
        assertTrue(worker.processOne());
        assertEquals("PENDING",state(id));
        assertEquals("INTELLIGENCE_UNAVAILABLE",jdbc.queryForObject("select error_code from servicedesk_assessment where ticket_id=?",String.class,id));
        verifyNoInteractions(transport);
    }
    @Test void signedTokenWithWrongIssuerFailsClosed() throws Exception {
        var id=create(false); invalidIssuer=true;
        assertTrue(worker.processOne()); verifyNoInteractions(transport);
        assertEquals("DEGRADED",state(id));
        assertEquals("AUTHORIZATION_REVOKED",jdbc.queryForObject("select error_code from servicedesk_assessment where ticket_id=?",String.class,id));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void signedTokenWithWrongAudienceFailsClosed() throws Exception {
        var id=create(false); invalidAudience=true;
        assertTrue(worker.processOne()); verifyNoInteractions(transport);
        assertEquals("DEGRADED",state(id));
        assertNull(SecurityContextHolder.getContext().getAuthentication());
    }
    @Test void revokedWorkerCannotInvokeAi() throws Exception {
        var id=create(false);
        when(directory.members("background-a")).thenReturn(List.of(new DirectoryMember("owner","owner",null,Set.of(new WorkspaceRole("requester")))));
        assertTrue(worker.processOne()); verifyNoInteractions(transport);
        assertEquals("DEGRADED",state(id));
        assertEquals("AUTHORIZATION_REVOKED",jdbc.queryForObject("select error_code from servicedesk_assessment where ticket_id=?",String.class,id));
    }
    @Test void workerCannotClaimOutsideConfiguredWorkspace() throws Exception {
        var id=create(false);
        var foreign=new BackgroundTicketAssessment(jdbc,json,capability,processing,audit,manager,false,List.of("background-b"),intake,alertProvider);
        assertFalse(foreign.processOne()); verifyNoInteractions(transport);
        assertEquals(0,jdbc.queryForObject("select attempts from servicedesk_assessment where ticket_id=?",Integer.class,id));
        assertTrue(worker.processOne()); assertEquals("READY",state(id));
    }
    @Test void assessmentPrimaryKeyPreventsDuplicateJobs() throws Exception {
        var id=create(false);
        assertThrows(org.springframework.dao.DuplicateKeyException.class,() ->
            jdbc.update("insert into servicedesk_assessment(ticket_id) values(?)",id));
        assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_assessment where ticket_id=?",Integer.class,id));
        assertTrue(worker.processOne()); assertFalse(worker.processOne());
        verify(transport,times(1)).triage(any());
    }
    @Test void failedReadyAuditRollsBackResultAndRetriesAtomically() throws Exception {
        var id=create(false);
        jdbc.execute("create function reject_assessment_ready() returns trigger language plpgsql as $$ begin if new.action='servicedesk.assessment.ready' then raise exception 'reject' using errcode='23514'; end if; return new; end $$");
        jdbc.execute("create trigger reject_assessment_ready before insert on audit_event for each row execute function reject_assessment_ready()");
        try {
            assertTrue(worker.processOne()); assertEquals("PENDING",state(id));
            assertNull(jdbc.queryForObject("select result::text from servicedesk_assessment where ticket_id=?",String.class,id));
            assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_assessment_history where ticket_id=? and event='READY'",Integer.class,id));
            assertEquals(0,jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.assessment.ready' and resource=?",Integer.class,id.toString()));
        } finally {
            jdbc.execute("drop trigger reject_assessment_ready on audit_event");
            jdbc.execute("drop function reject_assessment_ready()");
        }
        due(id); assertTrue(worker.processOne()); assertEquals("READY",state(id));
        assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_assessment_history where ticket_id=? and event='READY'",Integer.class,id));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.assessment.ready' and resource=?",Integer.class,id.toString()));
    }
    @Test void staleLeaseCannotOverwriteNewerCompletedAssessment() throws Exception {
        var id=create(); var entered=new CountDownLatch(1); var release=new CountDownLatch(1);
        doAnswer(invocation -> { entered.countDown(); assertTrue(release.await(15,TimeUnit.SECONDS)); return success(invocation.getArgument(0)); }).when(transport).triage(any());
        var executor=Executors.newSingleThreadExecutor();
        try {
            var old=executor.submit(worker::processOne); assertTrue(entered.await(15,TimeUnit.SECONDS));
            jdbc.update("update servicedesk_assessment set lease_until=clock_timestamp()-interval '1 second' where ticket_id=?",id);
            due(id);
            doAnswer(invocation -> success(invocation.getArgument(0))).when(transport).triage(any());
            assertTrue(worker.processOne()); String result=jdbc.queryForObject("select result::text from servicedesk_assessment where ticket_id=?",String.class,id);
            release.countDown(); assertTrue(old.get(20,TimeUnit.SECONDS));
            assertEquals(result,jdbc.queryForObject("select result::text from servicedesk_assessment where ticket_id=?",String.class,id));
            assertEquals(1,jdbc.queryForObject("select count(*) from servicedesk_assessment_history where ticket_id=? and event='READY'",Integer.class,id));
        } finally { release.countDown(); executor.shutdownNow(); }
    }
}
