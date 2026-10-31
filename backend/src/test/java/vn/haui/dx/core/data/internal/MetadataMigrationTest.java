package vn.haui.dx.core.data.internal;

import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named = "FILE_TEST_JDBC_URL", matches = ".+/file_storage")
class MetadataMigrationTest {
    private DriverManagerDataSource source;
    private JdbcTemplate jdbc;

    @BeforeAll void setup() {
        source = new DriverManagerDataSource(System.getenv("FILE_TEST_JDBC_URL"), "workflow", System.getenv("FILE_TEST_DB_PASSWORD"));
        jdbc = new JdbcTemplate(source);
    }

    @Test void v3ToV4UpgradePreservesLegacyMetadataAndEnforcesWorkspaceScopedForeignKey() {
        Flyway.configure().dataSource(source).schemas("meta_migration_probe").target("3").load().migrate();
        UUID metaId = UUID.randomUUID();
        jdbc.update("insert into meta_migration_probe.metadata(id,workspace,title,source,version,usage_rights,checksum,owner) "
            + "values (?,'lab-a','Legacy Title','src:v3','v1','CC0',?,'agent-1')", metaId, "0".repeat(64));

        // Upgrade to V4
        Flyway.configure().dataSource(source).schemas("meta_migration_probe").load().migrate();
        var row = jdbc.queryForMap("select * from meta_migration_probe.metadata where id=?", metaId);
        assertEquals("Legacy Title", row.get("title"));
        assertNull(row.get("file_id"));

        // Create a file in lab-a and another in lab-b
        UUID fileA = UUID.randomUUID();
        UUID fileB = UUID.randomUUID();
        jdbc.update("insert into meta_migration_probe.file_object(id,workspace,owner,storage_key,media_type,byte_size,checksum,original_name) "
            + "values (?,'lab-a','agent-1',?,'text/plain',1,?,'fileA.txt')", fileA, UUID.randomUUID(), "0".repeat(64));
        jdbc.update("insert into meta_migration_probe.file_object(id,workspace,owner,storage_key,media_type,byte_size,checksum,original_name) "
            + "values (?,'lab-b','agent-2',?,'text/plain',1,?,'fileB.txt')", fileB, UUID.randomUUID(), "0".repeat(64));

        // Valid link in same workspace
        UUID validMeta = UUID.randomUUID();
        assertDoesNotThrow(() -> jdbc.update("insert into meta_migration_probe.metadata(id,workspace,title,source,version,usage_rights,checksum,file_id,owner) "
            + "values (?,'lab-a','Valid File Meta','src:file','v1','CC0',?,?,?)", validMeta, "0".repeat(64), fileA, "agent-1"));

        // DB constraint prevents cross-workspace file link (Foreign key is (workspace, file_id))
        UUID crossMeta = UUID.randomUUID();
        assertThrows(DataIntegrityViolationException.class, () ->
            jdbc.update("insert into meta_migration_probe.metadata(id,workspace,title,source,version,usage_rights,checksum,file_id,owner) "
                + "values (?,'lab-a','Cross Meta','src:cross','v1','CC0',?,?,?)", crossMeta, "0".repeat(64), fileB, "agent-1"));
    }
}
