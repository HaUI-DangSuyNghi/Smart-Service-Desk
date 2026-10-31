package vn.haui.dx.core;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class SettingsMigrationProfileTest {
    @Test void cleanAndUpgradeProfileHistoryAndReconnect() {
        String url = System.getenv("FILE_TEST_JDBC_URL"), password = System.getenv("FILE_TEST_DB_PASSWORD");
        var source = new DriverManagerDataSource(url, "workflow", password);
        boolean core = Boolean.getBoolean("dx.proof.core-only");
        boolean second = Boolean.getBoolean("dx.proof.second-domain");
        boolean serviceSeed = getClass().getResource("/db/migration/apps/servicedesk/V33__servicedesk_settings.sql") != null;
        for (boolean upgrade : new boolean[]{false, true}) {
            String schema = "settings_" + UUID.randomUUID().toString().replace("-", "");
            var jdbc = new JdbcTemplate(new DriverManagerDataSource(url + "?currentSchema=" + schema, "workflow", password));
            try {
                var before = java.util.List.<java.util.Map<String,Object>>of();
                if (upgrade) {
                    Flyway.configure().dataSource(source).schemas(schema).target(core ? "4" : "31").load().migrate();
                    before = jdbc.queryForList("select version,checksum from flyway_schema_history where version is not null order by installed_rank");
                    jdbc.update("insert into audit_event(id,actor,action,resource_type,resource,workspace,request_id,details) values(?, 'proof','proof.before','proof','1','lab-a','proof','{}')", UUID.randomUUID());
                    if (!core && serviceSeed) {
                        Flyway.configure().dataSource(source).schemas(schema).target("32").load().migrate();
                        assertEquals(1, jdbc.queryForObject("select version from hpdi_work_settings", Integer.class));
                        assertNull(jdbc.queryForObject("select to_regclass('servicedesk_settings')::text", String.class));
                    }
                }
                var flyway = Flyway.configure().dataSource(source).schemas(schema).load();
                flyway.migrate(); assertTrue(flyway.validateWithResult().validationSuccessful);
                assertEquals(core ? "4" : serviceSeed ? "38" : "32", flyway.info().current().getVersion().toString());
                if (upgrade) {
                    assertEquals(before, jdbc.queryForList("select version,checksum from flyway_schema_history where version is not null and version::int<=" + (core ? "4" : "31") + " order by installed_rank"));
                    assertEquals(1, jdbc.queryForObject("select count(*) from audit_event", Integer.class));
                }
                assertEquals(!core, jdbc.queryForObject("select to_regclass('hpdi_work_settings') is not null", Boolean.class));
                assertEquals(!core && !second && serviceSeed, jdbc.queryForObject("select to_regclass('servicedesk_settings') is not null", Boolean.class));
                if (!core) {
                    jdbc.update("update hpdi_work_settings set version=2,updated_by='persistent' where id=1");
                    var reconnected = new JdbcTemplate(new DriverManagerDataSource(url + "?currentSchema=" + schema, "workflow", password));
                    assertEquals("persistent", reconnected.queryForObject("select updated_by from hpdi_work_settings", String.class));
                    flyway.migrate(); assertEquals(2, reconnected.queryForObject("select version from hpdi_work_settings", Integer.class));
                    assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update hpdi_work_settings set id=2"));
                    assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update hpdi_work_settings set version=0"));
                    assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update hpdi_work_settings set payload='{}'"));
                    if (serviceSeed) {
                        jdbc.update("update servicedesk_settings set version=2,updated_by='persistent' where id=1");
                        assertEquals("persistent", reconnected.queryForObject("select updated_by from servicedesk_settings", String.class));
                        flyway.migrate(); assertEquals(2, reconnected.queryForObject("select version from servicedesk_settings", Integer.class));
                        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update servicedesk_settings set id=2"));
                        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update servicedesk_settings set version=0"));
                        assertThrows(org.springframework.dao.DataAccessException.class, () -> jdbc.update("update servicedesk_settings set payload='{}'"));
                    }
                }
            } finally { new JdbcTemplate(source).execute("drop schema if exists " + schema + " cascade"); }
        }
    }
}
