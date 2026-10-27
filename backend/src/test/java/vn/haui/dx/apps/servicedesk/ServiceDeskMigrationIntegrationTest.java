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
    @ValueSource(ints = {9, 10})
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
        var before = jdbc.queryForMap("select * from " + schema + ".servicedesk_ticket where id=?", ticket);
        var review = jdbc.queryForMap("select * from " + schema + ".servicedesk_review where ticket_id=?", ticket);
        var history = jdbc.queryForList("select * from " + schema + ".servicedesk_estimate_history where ticket_id=?", ticket);

        if (baselineVersion == 10) {
            jdbc.update("insert into " + schema + ".servicedesk_assessment(ticket_id) values (?)", ticket);
            jdbc.update("insert into " + schema + ".servicedesk_assessment_history(ticket_id,attempt,event)"
                    + " values (?,0,'QUEUED')", ticket);
        }
        var assessment = baselineVersion == 10
                ? jdbc.queryForMap("select * from " + schema + ".servicedesk_assessment where ticket_id=?", ticket) : null;
        var assessmentHistory = baselineVersion == 10
                ? jdbc.queryForList("select * from " + schema + ".servicedesk_assessment_history where ticket_id=?", ticket) : null;

        var upgraded = Flyway.configure().dataSource(source).schemas(schema).load();
        assertEquals(14 - baselineVersion, upgraded.migrate().migrationsExecuted);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        var after = jdbc.queryForMap("select * from " + schema + ".servicedesk_ticket where id=?", ticket);
        assertEquals(0L, after.remove("context_revision"));
        assertEquals(before, after);
        assertEquals(review, jdbc.queryForMap("select * from " + schema + ".servicedesk_review where ticket_id=?", ticket));
        assertEquals(history, jdbc.queryForList("select * from " + schema + ".servicedesk_estimate_history where ticket_id=?", ticket));
        if (baselineVersion == 10) {
            assertEquals(assessment, jdbc.queryForMap("select * from " + schema + ".servicedesk_assessment where ticket_id=?", ticket));
            assertEquals(assessmentHistory, jdbc.queryForList(
                    "select * from " + schema + ".servicedesk_assessment_history where ticket_id=?", ticket));
        }
        for (String table : new String[] {"servicedesk_ticket_clarification", "servicedesk_enrichment_case", "servicedesk_contextual_attempt"}) {
            assertEquals(0L, jdbc.queryForObject("select count(*) from " + schema + "." + table, Long.class));
        }
        assertEquals(0, upgraded.migrate().migrationsExecuted);
    }
}
