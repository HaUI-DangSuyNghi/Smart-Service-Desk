package vn.haui.dx.apps.servicedesk;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import vn.haui.dx.core.identity.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class AgentDutyUpgradeIntegrationTest {
    private static final String SCHEMA = "duty_runtime_" + UUID.randomUUID().toString().replace("-", "");
    private static final UUID TICKET = UUID.randomUUID();
    private static List<Map<String, Object>> checksums;
    private static Map<String, List<Map<String, Object>>> ai08;
    private static String processId;
    private static String reviewTaskId;

    @DynamicPropertySource static void properties(DynamicPropertyRegistry properties) {
        String url = System.getenv("FILE_TEST_JDBC_URL").replace("/file_storage", "/duty_upgrade");
        var source = new DriverManagerDataSource(url, "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        var baseline = Flyway.configure().dataSource(source).schemas(SCHEMA).target("13").load();
        baseline.migrate();
        assertTrue(baseline.validateWithResult().validationSuccessful);
        var jdbc = new JdbcTemplate(source);
        var scoped = new DriverManagerDataSource(url + "?currentSchema=" + SCHEMA, "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        var configuration = new org.flowable.spring.SpringProcessEngineConfiguration();
        configuration.setDataSource(scoped);
        configuration.setTransactionManager(new org.springframework.jdbc.datasource.DataSourceTransactionManager(scoped));
        configuration.setDatabaseSchemaUpdate("true");
        configuration.setHistory("full");
        configuration.setAsyncExecutorActivate(false);
        var previousEngine = configuration.buildProcessEngine();
        try {
            previousEngine.getRepositoryService().createDeployment()
                    .addClasspathResource("apps/servicedesk/process/service-desk-reviewed.bpmn20.xml").deploy();
            processId = previousEngine.getRuntimeService().startProcessInstanceByKey(ServiceDeskProcess.REVIEWED_KEY,
                    TICKET.toString(), Map.of("workspace", "lab-a", "initiator", "owner")).getId();
            reviewTaskId = previousEngine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult().getId();
        } finally { previousEngine.close(); }
        jdbc.update("insert into " + SCHEMA + ".process_scope(id,workspace,owner,process_key,business_key)"
                + " values (?,'lab-a','owner','service-desk-reviewed',?)", processId, TICKET.toString());
        jdbc.update("insert into " + SCHEMA + ".servicedesk_ticket"
                + "(id,workspace,creator,client_request_id,title,description,process_instance_id,process_key,awaiting_review)"
                + " values (?,'lab-a','owner',?,'AI08 retained','AI08 retained',?,'service-desk-reviewed',true)", TICKET, UUID.randomUUID(), processId);
        ai08 = ServiceDeskMigrationIntegrationTest.seedAi08(jdbc, SCHEMA, TICKET, 13);
        checksums = jdbc.queryForList("select version,script,checksum from " + SCHEMA
                + ".flyway_schema_history where version in ('11','12','13') order by installed_rank");
        assertEquals(3, checksums.size());
        assertNull(jdbc.queryForObject("select to_regclass(?)", String.class, SCHEMA + ".hpdi_agent_duty"));
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> "workflow");
        properties.add("spring.datasource.password", () -> System.getenv("FILE_TEST_DB_PASSWORD"));
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> "http://127.0.0.1:1/jwks");
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired WorkspaceDirectory directory;
    @Autowired Flyway flyway;
    @Autowired org.flowable.engine.ProcessEngine engine;

    @Test void bootsFlowableAndServesDutyAfterUpgradingAppliedAi08WithoutChangingHistory() throws Exception {
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals("30", flyway.info().current().getVersion().toString());
        assertEquals(0, jdbc.queryForObject("select count(*) from servicedesk_sla", Integer.class));
        assertEquals(checksums, jdbc.queryForList("select version,script,checksum from flyway_schema_history where version in ('11','12','13') order by installed_rank"));
        for (var snapshot : ai08.entrySet())
            assertEquals(snapshot.getValue(), jdbc.queryForList("select * from " + snapshot.getKey() + " where ticket_id=?", TICKET));
        assertEquals(1L, jdbc.queryForObject("select context_revision from servicedesk_ticket where id=?", Long.class, TICKET));
        assertNotNull(engine.getRepositoryService().createProcessDefinitionQuery().processDefinitionKey(ServiceDeskProcess.REVIEWED_KEY).latestVersion().singleResult());
        assertEquals(reviewTaskId, engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult().getId());
        String subject = "upgrade-agent";
        when(directory.members("lab-a")).thenReturn(List.of(new DirectoryMember(subject,subject,null,Set.of(WorkspaceRole.AGENT))));
        var actor = jwt().jwt(token -> token.subject(subject).claim("groups", List.of("/workspaces/lab-a/agent")));
        String duty = "/api/hpdi/v1/workspaces/lab-a/duty";
        mvc.perform(get(duty).with(actor)).andExpect(status().isOk()).andExpect(jsonPath("$[0].onDuty").value(false));
        mvc.perform(put(duty + "/me").with(actor).contentType("application/json").content("{\"onDuty\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.onDuty").value(true));
        assertEquals(1, jdbc.queryForObject("select count(*) from hpdi_agent_duty where workspace='lab-a' and subject=? and on_duty",Integer.class,subject));
        assertEquals(1, jdbc.queryForObject("select count(*) from audit_event where workspace='lab-a' and action='human.duty.changed' and resource=?",Integer.class,subject));
        when(directory.supportGroupMembers("lab-a", "hardware")).thenReturn(Set.of(subject));
        var coordinator = jwt().jwt(token -> token.subject("coordinator").claim("groups", List.of("/workspaces/lab-a/coordinator")));
        mvc.perform(post("/api/apps/servicedesk/v1/workspaces/lab-a/tickets/" + TICKET + "/review").with(coordinator)
                .contentType("application/json").content("{\"category\":\"HARDWARE\",\"priority\":\"HIGH\",\"assignee\":\"upgrade-agent\",\"reason\":\"Reviewed after upgrade\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assignee").value(subject));
        assertEquals(90, jdbc.queryForObject("select estimate_minutes from servicedesk_review where ticket_id=?",Integer.class,TICKET));
        var handle = engine.getTaskService().createTaskQuery().processInstanceId(processId).singleResult();
        assertEquals("handle", handle.getTaskDefinitionKey());
        mvc.perform(post("/api/hpdi/v1/workspaces/lab-a/tasks/" + handle.getId() + "/start").with(actor))
                .andExpect(status().isOk()).andExpect(jsonPath("$.workState").value("IN_PROGRESS"));
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }
}
