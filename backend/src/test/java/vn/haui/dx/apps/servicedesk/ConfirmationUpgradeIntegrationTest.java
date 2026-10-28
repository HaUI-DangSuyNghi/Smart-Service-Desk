package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties="serviceDesk.confirmation.scheduled=false")
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class ConfirmationUpgradeIntegrationTest {
    private static final String SCHEMA = "confirm_upgrade_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID TICKET = UUID.randomUUID();
    private static String processId;
    private static String generation;
    private static java.time.Instant startedAt;
    private static List<Map<String,Object>> checksums;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        String url = System.getenv("FILE_TEST_JDBC_URL").replace("/file_storage", "/duty_upgrade");
        var source = new DriverManagerDataSource(url,"workflow",System.getenv("FILE_TEST_DB_PASSWORD"));
        Flyway.configure().dataSource(source).schemas(SCHEMA).target("27").load().migrate();
        var scoped = new DriverManagerDataSource(url+"?currentSchema="+SCHEMA,"workflow",System.getenv("FILE_TEST_DB_PASSWORD"));
        var jdbc = new JdbcTemplate(scoped);
        checksums = jdbc.queryForList("select version,checksum from flyway_schema_history where version is not null order by installed_rank");
        var config = new org.flowable.spring.SpringProcessEngineConfiguration();
        config.setDataSource(scoped);
        config.setTransactionManager(new org.springframework.jdbc.datasource.DataSourceTransactionManager(scoped));
        config.setDatabaseSchema(SCHEMA); config.setDatabaseSchemaUpdate("true"); config.setHistory("full"); config.setAsyncExecutorActivate(false);
        var engine = config.buildProcessEngine();
        try {
            engine.getRepositoryService().createDeployment().addClasspathResource("apps/servicedesk/process/service-desk-reviewed.bpmn20.xml").deploy();
            processId = engine.getRuntimeService().startProcessInstanceByKey(ServiceDeskProcess.REVIEWED_KEY,TICKET.toString(),
                Map.of("workspace","lab-a","initiator","owner")).getId();
            var review = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
            engine.getTaskService().complete(review.getId(),Map.of("outcome_review","approved"));
            var handle = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
            engine.getTaskService().setVariableLocal(handle.getId(),"outcome","resolved");
            engine.getTaskService().complete(handle.getId(),Map.of("outcome_handle","resolved"));
            var confirm = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
            generation = confirm.getId(); startedAt = confirm.getCreateTime().toInstant();
            jdbc.update("insert into process_scope(id,workspace,owner,process_key,business_key) values(?,'lab-a','owner',?,?)",processId,ServiceDeskProcess.REVIEWED_KEY,TICKET.toString());
            jdbc.update("insert into servicedesk_ticket(id,workspace,creator,client_request_id,title,description,process_instance_id,process_key)"
                + " values(?,'lab-a','owner',?,'Legacy confirm','Migration fixture',?,?)",TICKET,UUID.randomUUID(),processId,ServiceDeskProcess.REVIEWED_KEY);
            jdbc.update("insert into servicedesk_sla(workspace,ticket_id,process_instance_id,review_task_id,handle_task_id,policy,base_priority,priority,approved_at,start_by,resolve_by,first_started_at,resolved_at,checked_at)"
                + " values('lab-a',?,?,?,?,'{}','HIGH','HIGH',current_timestamp,current_timestamp,current_timestamp,current_timestamp,current_timestamp,current_timestamp)",TICKET,processId,review.getId(),handle.getId());
        } finally { engine.close(); }
        assertNull(jdbc.queryForObject("select to_regclass('"+SCHEMA+".servicedesk_confirmation')",String.class));
        Flyway.configure().dataSource(source).schemas(SCHEMA).target("29").load().migrate();
        assertEquals(0,jdbc.queryForObject("select count(*) from servicedesk_confirmation",Integer.class));
        properties.add("spring.datasource.url",()->url+"?currentSchema="+SCHEMA);
        properties.add("spring.datasource.username",()->"workflow");
        properties.add("spring.datasource.password",()->System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.flyway.schemas",()->SCHEMA);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",()->"http://127.0.0.1:1/jwks");
    }

    @Autowired JdbcTemplate jdbc;
    @Autowired vn.haui.dx.hpdi.process.api.ProcessTimers timers;
    @Autowired TicketConfirmation confirmations;
    @org.springframework.boot.test.mock.mockito.SpyBean vn.haui.dx.core.identity.api.ServiceIdentity identity;

    @Test void v27ToV29ToV30EnrollsNativeActiveConfirmWithoutMutatingTicket() {
        doAnswer(invocation -> { ((Runnable)invocation.getArgument(2)).run(); return null; })
            .when(identity).run(eq("lab-a"),eq("workflow:timer"),any());
        var before = jdbc.queryForList("select * from servicedesk_ticket");
        timers.enrollConfirmations(); timers.enrollConfirmations();
        var state = confirmations.read("lab-a",TICKET);
        assertNotNull(state); assertEquals(generation,state.confirmGeneration());
        assertEquals(startedAt,state.confirmStartedAt());
        assertEquals(startedAt.plus(java.time.Duration.ofHours(72)),state.confirmDeadlineAt());
        assertNull(state.closedBy()); assertEquals(before,jdbc.queryForList("select * from servicedesk_ticket"));
        assertEquals(1,jdbc.queryForObject("select count(*) from audit_event where action='servicedesk.confirmation.enrolled'",Integer.class));
        assertEquals(checksums,jdbc.queryForList("select version,checksum from flyway_schema_history where cast(version as integer)<=27 order by installed_rank"));
    }
}
