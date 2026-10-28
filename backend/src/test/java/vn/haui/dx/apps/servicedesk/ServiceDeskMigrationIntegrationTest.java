package vn.haui.dx.apps.servicedesk;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class ServiceDeskMigrationIntegrationTest {
    @ParameterizedTest
    @ValueSource(ints = {23,24})
    void v25PreservesExistingRequestsAndPermitsAuditedSupersession(int version) {
        var source = new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"),"workflow",System.getenv("FILE_TEST_DB_PASSWORD"));
        String schema = "estimate_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        Flyway.configure().dataSource(source).schemas(schema).target(Integer.toString(version)).load().migrate();
        var jdbc = new JdbcTemplate(source);
        UUID pending = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".hpdi_estimate_request(id,workspace,process_instance_id,task_id,generation,business_key,label,requested_by,current_minutes,proposed_minutes,reason)"
            + " values(?,'lab-a','process','task','review','ticket','Estimate','agent-a',90,120,'More work')",pending);
        for (String status : java.util.List.of("APPROVED","REJECTED"))
            jdbc.update("insert into " + schema + ".hpdi_estimate_request(id,workspace,process_instance_id,task_id,generation,business_key,label,requested_by,current_minutes,proposed_minutes,reason,status,decided_by,decided_at)"
                + " values(?,'lab-a','process','task','review','ticket','Estimate','agent-a',90,120,'More work',?,'coordinator',clock_timestamp())",UUID.randomUUID(),status);
        var before = jdbc.queryForList("select * from " + schema + ".hpdi_estimate_request order by id");
        var upgraded = Flyway.configure().dataSource(source).schemas(schema).load();
        assertEquals(25-version,upgraded.migrate().migrationsExecuted);
        var after = jdbc.queryForList("select * from " + schema + ".hpdi_estimate_request order by id");
        after.forEach(row -> { row.remove("superseded_by"); row.remove("superseded_at"); row.remove("superseded_reason"); });
        assertEquals(before,after);
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class, () -> jdbc.update(
            "update " + schema + ".hpdi_estimate_request set status='SUPERSEDED' where id=?",pending));
        jdbc.update("update " + schema + ".hpdi_estimate_request set status='SUPERSEDED',superseded_by='coordinator',superseded_at=clock_timestamp(),superseded_reason='ASSIGNMENT_CHANGED' where id=?",pending);
        jdbc.update("insert into " + schema + ".hpdi_estimate_request(id,workspace,process_instance_id,task_id,generation,business_key,label,requested_by,current_minutes,proposed_minutes,reason)"
            + " values(?,'lab-a','process','task','review','ticket','Estimate','agent-b',90,130,'New assignee')",UUID.randomUUID());
        assertEquals(1,jdbc.queryForObject("select count(*) from " + schema + ".hpdi_estimate_request where status='PENDING'",Integer.class));
        assertEquals(0,upgraded.migrate().migrationsExecuted);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
    }

    @org.junit.jupiter.api.Test
    void v15BackfillsOnlyChangedReviewedHandleAssignmentsAndPreservesV14() throws Exception {
        var source=new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"),"workflow",System.getenv("FILE_TEST_DB_PASSWORD"));
        String schema="r2_upgrade_"+UUID.randomUUID().toString().replace("-","");
        Flyway.configure().dataSource(source).schemas(schema).target("14").load().migrate();
        var jdbc=new JdbcTemplate(source);
        for (String process : java.util.List.of("reviewed-a","legacy-a","reviewed-b"))
            jdbc.update("insert into "+schema+".servicedesk_ticket(id,workspace,creator,client_request_id,title,description,process_instance_id,process_key)"
                + " values(?,?, 'owner',?,'Upgrade test','Synthetic',?,?)",UUID.randomUUID(),process.endsWith("b") ? "lab-b" : "lab-a",
                UUID.randomUUID(),process,process.startsWith("legacy") ? ServiceDeskProcess.KEY : ServiceDeskProcess.REVIEWED_KEY);
        jdbc.update("insert into "+schema+".hpdi_agent_duty(workspace,subject,on_duty,note) values('lab-a','agent-a',true,'Preserve duty')");
        seedAssignmentAudit(jdbc,schema,"task.assign","reviewed-a","handle","agent-a",null);
        seedAssignmentAudit(jdbc,schema,"task.assign","reviewed-a","handle","agent-a","agent-a");
        seedAssignmentAudit(jdbc,schema,"task.assign","reviewed-a","handle","agent-b","agent-a");
        seedAssignmentAudit(jdbc,schema,"task.assign","reviewed-a","review","agent-a",null);
        seedAssignmentAudit(jdbc,schema,"task.assign","legacy-a","handle","agent-a",null);
        seedAssignmentAudit(jdbc,schema,"task.assign","reviewed-b","handle","agent-a",null);
        seedAssignmentAudit(jdbc,schema,"task.assign","reviewed-a","handle",null,null);
        seedAssignmentAudit(jdbc,schema,"task.claim","reviewed-a",null,null,null);
        var checksums=jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where success and version is not null order by installed_rank");
        var duties=jdbc.queryForList("select * from "+schema+".hpdi_agent_duty");
        var audits=jdbc.queryForList("select * from "+schema+".audit_event order by id");
        var upgraded=Flyway.configure().dataSource(source).schemas(schema).target("15").load();
        assertEquals(1,upgraded.migrate().migrationsExecuted);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        assertEquals(checksums,jdbc.queryForList("select version,checksum from "+schema+".flyway_schema_history where success and version<>'15' order by installed_rank"));
        assertEquals(duties,jdbc.queryForList("select * from "+schema+".hpdi_agent_duty"));
        assertEquals(audits,jdbc.queryForList("select * from "+schema+".audit_event order by id"));
        assertEquals(3,jdbc.queryForObject("select count(*) from "+schema+".servicedesk_assignment_event",Integer.class));
        assertEquals(java.util.List.of(java.time.Instant.parse("2026-10-27T01:23:00Z")),jdbc.query("select distinct assigned_at from "+schema
            + ".servicedesk_assignment_event",(row,index) -> row.getTimestamp(1).toInstant()));
        assertEquals(java.util.List.of("agent-a","agent-b"),jdbc.queryForList("select subject from "+schema
            + ".servicedesk_assignment_event where workspace='lab-a' order by subject",String.class));
        assertEquals(1,jdbc.queryForObject("select count(*) from "+schema+".servicedesk_assignment_event where workspace='lab-b'",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from "+schema+".servicedesk_assignment_event where subject='claim-agent'",Integer.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from "+schema+".servicedesk_assignment_cursor",Integer.class));
        assertEquals(0,upgraded.migrate().migrationsExecuted);
    }

    private void seedAssignmentAudit(JdbcTemplate jdbc, String schema, String action, String process,
                                     String taskKey, String subject, String previous) throws Exception {
        var details=new java.util.HashMap<String,Object>();
        details.put("processInstanceId",process);
        if (taskKey!=null) details.put("taskKey",taskKey);
        if (subject!=null) details.put("assignee",subject);
        if (previous!=null) details.put("previousAssignee",previous);
        jdbc.update("insert into "+schema+".audit_event(id,actor,action,resource_type,resource,workspace,request_id,details,occurred_at)"
            + " values(?,'claim-agent',?,'task',?,?,?,cast(? as jsonb),?)",UUID.randomUUID(),action,UUID.randomUUID().toString(),
            process.endsWith("b") ? "lab-b" : "lab-a",UUID.randomUUID().toString(),new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(details),
            java.sql.Timestamp.from(java.time.Instant.parse("2026-10-27T01:23:00Z")));
    }

    @ParameterizedTest
    @ValueSource(ints = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24})
    void upgradesDevelopWithoutChangingPersistedWork(int baselineVersion) {
        var source = new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"),
                "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        String schema = "ai08_upgrade_" + UUID.randomUUID().toString().replace("-", "");
        var baseline = Flyway.configure().dataSource(source).schemas(schema)
                .target(Integer.toString(baselineVersion)).load();
        baseline.migrate();

        var jdbc = new JdbcTemplate(source);
        UUID ticket = UUID.randomUUID();
        jdbc.update("insert into " + schema + ".servicedesk_ticket"
                + "(id,workspace,creator,client_request_id,title,description,process_instance_id,process_key,awaiting_review)"
                + " values (?,'lab-a','owner',?,'Synthetic upgrade','Synthetic description','existing-process','service-desk-reviewed',true)",
                ticket, UUID.randomUUID());
        jdbc.update("insert into " + schema + ".servicedesk_review"
                + "(ticket_id,category,priority,estimate_minutes,estimate_basis,review_task_id,reviewed_by,reason)"
                + " values (?,'HARDWARE','HIGH',90,'DEFAULT','existing-review','coordinator','Synthetic review')", ticket);
        jdbc.update("insert into " + schema + ".servicedesk_estimate_history"
                + "(ticket_id,review_task_id,category,priority,minutes,basis,changed_by,reason)"
                + " values (?,'existing-review','HARDWARE','HIGH',90,'DEFAULT','coordinator','Synthetic history')", ticket);
        var ai08 = seedAi08(jdbc, schema, ticket, baselineVersion);
        var checksums = jdbc.queryForList("select version,script,checksum from " + schema
                + ".flyway_schema_history where success and version is not null order by installed_rank");
        var before = jdbc.queryForMap("select * from " + schema + ".servicedesk_ticket where id=?", ticket);
        var review = jdbc.queryForMap("select * from " + schema + ".servicedesk_review where ticket_id=?", ticket);
        var history = jdbc.queryForList("select * from " + schema + ".servicedesk_estimate_history where ticket_id=?", ticket);

        if (baselineVersion >= 10) {
            jdbc.update("insert into " + schema + ".servicedesk_assessment(ticket_id) values (?)", ticket);
            jdbc.update("insert into " + schema + ".servicedesk_assessment_history(ticket_id,attempt,event)"
                    + " values (?,0,'QUEUED')", ticket);
        }
        var assessment = baselineVersion >= 10
                ? jdbc.queryForMap("select * from " + schema + ".servicedesk_assessment where ticket_id=?", ticket) : null;
        var assessmentHistory = baselineVersion >= 10
                ? jdbc.queryForList("select * from " + schema + ".servicedesk_assessment_history where ticket_id=?", ticket) : null;

        var upgraded = Flyway.configure().dataSource(source).schemas(schema).load();
        assertEquals(25 - baselineVersion, upgraded.migrate().migrationsExecuted);
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".servicedesk_sla", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".hpdi_alert_event", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".hpdi_alert_recipient", Long.class));
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".servicedesk_alert_monitor", Long.class));
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        var after = jdbc.queryForMap("select * from " + schema + ".servicedesk_ticket where id=?", ticket);
        if (baselineVersion < 11) assertEquals(0L, after.remove("context_revision"));
        assertEquals(before, after);
        assertEquals(review, jdbc.queryForMap("select * from " + schema + ".servicedesk_review where ticket_id=?", ticket));
        assertEquals(history, jdbc.queryForList("select * from " + schema + ".servicedesk_estimate_history where ticket_id=?", ticket));
        if (baselineVersion >= 10) {
            assertEquals(assessment, jdbc.queryForMap("select * from " + schema + ".servicedesk_assessment where ticket_id=?", ticket));
            assertEquals(assessmentHistory, jdbc.queryForList(
                    "select * from " + schema + ".servicedesk_assessment_history where ticket_id=?", ticket));
        }
        for (String table : new String[] {"servicedesk_ticket_clarification", "servicedesk_enrichment_case", "servicedesk_contextual_attempt"}) {
            assertEquals(ai08.getOrDefault(table, java.util.List.of()),
                    jdbc.queryForList("select * from " + schema + "." + table + " where ticket_id=?", ticket));
        }
        assertEquals(checksums, jdbc.queryForList("select version,script,checksum from " + schema
                + ".flyway_schema_history where success and cast(version as integer)<=? order by installed_rank", baselineVersion));
        assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + ".hpdi_agent_duty", Long.class));
        assertEquals(0, upgraded.migrate().migrationsExecuted);
    }

    static java.util.Map<String, java.util.List<java.util.Map<String, Object>>> seedAi08(
            JdbcTemplate jdbc, String schema, UUID ticket, int version) {
        var snapshots = new java.util.HashMap<String, java.util.List<java.util.Map<String, Object>>>();
        if (version >= 11) {
            jdbc.update("update " + schema + ".servicedesk_ticket set context_revision=1 where id=?", ticket);
            jdbc.update("insert into " + schema + ".servicedesk_ticket_clarification"
                    + "(id,workspace,ticket_id,actor,client_answer_id,expected_revision,new_revision,answers_json,payload_digest)"
                    + " values (?,'lab-a',?,'owner',?,0,1,'[{\"slot\":\"location\",\"value\":\"lab\"}]',?)",
                    UUID.randomUUID(), ticket, UUID.randomUUID(), "a".repeat(64));
            snapshots.put("servicedesk_ticket_clarification", jdbc.queryForList("select * from " + schema + ".servicedesk_ticket_clarification where ticket_id=?", ticket));
        }
        if (version >= 12) {
            jdbc.update("insert into " + schema + ".servicedesk_enrichment_case"
                    + "(ticket_id,workspace,context_revision,policy_digest,status) values (?,'lab-a',1,?,'AWAITING_ANSWER')", ticket, "b".repeat(64));
            snapshots.put("servicedesk_enrichment_case", jdbc.queryForList("select * from " + schema + ".servicedesk_enrichment_case where ticket_id=?", ticket));
        }
        if (version >= 13) {
            jdbc.update("insert into " + schema + ".servicedesk_contextual_attempt"
                    + "(workspace,ticket_id,input_digest,processing_digest,policy_digest,model_digest,state,category,claim_id,completed_at)"
                    + " values ('lab-a',?,?,?,?,?,'DONE','HARDWARE',?,current_timestamp)",
                    ticket, "c".repeat(64), "d".repeat(64), "b".repeat(64), "e".repeat(64), UUID.randomUUID());
            snapshots.put("servicedesk_contextual_attempt", jdbc.queryForList("select * from " + schema + ".servicedesk_contextual_attempt where ticket_id=?", ticket));
        }
        return snapshots;
    }
}
