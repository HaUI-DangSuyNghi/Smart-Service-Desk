package vn.haui.dx.hpdi.human;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class AgentScheduleMigrationTest {
    @Test void upgradeFromPreV31PreservesDutyChecksumsAndScheduleAcrossConnections() {
        String url=System.getenv("FILE_TEST_JDBC_URL");
        String password=System.getenv("FILE_TEST_DB_PASSWORD");
        var source=new DriverManagerDataSource(url,"workflow",password);
        String schema="shift_"+UUID.randomUUID().toString().replace("-","");
        var priorVersion=java.util.Arrays.stream(Flyway.configure().dataSource(source).schemas(schema).load().info().all())
            .map(org.flywaydb.core.api.MigrationInfo::getVersion).filter(java.util.Objects::nonNull)
            .filter(version -> version.compareTo(org.flywaydb.core.api.MigrationVersion.fromVersion("31"))<0)
            .max(java.util.Comparator.naturalOrder()).orElseThrow();
        Flyway.configure().dataSource(source).schemas(schema).target(priorVersion).load().migrate();
        var scoped=new DriverManagerDataSource(url+"?currentSchema="+schema,"workflow",password);
        var jdbc=new JdbcTemplate(scoped);
        jdbc.update("insert into hpdi_agent_duty(workspace,subject,on_duty,note) values('lab-a','agent',true,'Preserve me')");
        var before=jdbc.queryForList("select version,checksum from flyway_schema_history where version is not null order by installed_rank");
        var upgrade=Flyway.configure().dataSource(source).schemas(schema).load();
        upgrade.migrate();
        assertTrue(upgrade.validateWithResult().validationSuccessful);
        assertEquals(before,jdbc.queryForList("select version,checksum from flyway_schema_history where version is not null and version::int<=30 order by installed_rank"));
        assertTrue(jdbc.queryForObject("select on_duty from hpdi_agent_duty where subject='agent'",Boolean.class));
        assertEquals(0,jdbc.queryForObject("select count(*) from hpdi_agent_schedule",Integer.class));
        jdbc.update("insert into hpdi_agent_schedule(workspace,subject,version,enabled) values('lab-a','agent',1,true)");
        jdbc.update("insert into hpdi_agent_shift(workspace,subject,version,starts_at,ends_at,timezone) values('lab-a','agent',1,'2026-10-28T07:00:00+07:00','2026-10-28T11:30:00+07:00','Asia/Ho_Chi_Minh')");
        var reconnected=new JdbcTemplate(new DriverManagerDataSource(url+"?currentSchema="+schema,"workflow",password));
        assertEquals("Asia/Ho_Chi_Minh",reconnected.queryForObject("select timezone from hpdi_agent_shift",String.class));
        assertEquals(java.time.Instant.parse("2026-10-28T00:00:00Z"),reconnected.queryForObject("select starts_at from hpdi_agent_shift",java.sql.Timestamp.class).toInstant());
    }
}
