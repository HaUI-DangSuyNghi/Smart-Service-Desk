package vn.haui.dx.hpdi.settings;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="FRESH_ADMIN_TEST_URL", matches="http://(127\\.0\\.0\\.1|keycloak):[0-9]+")
@EnabledIfEnvironmentVariable(named="FILE_TEST_JDBC_URL", matches=".+/file_storage")
class SettingsHttpAcceptanceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    private final String keycloak = System.getenv("FRESH_ADMIN_TEST_URL");
    private final String password = System.getenv("FRESH_ADMIN_TEST_PASSWORD");
    private final String realm = "fresh-admin-proof";
    private final List<Map<String,Object>> evidence = Collections.synchronizedList(new ArrayList<>());
    private String bootstrap;
    private String app;
    private Process process;
    private Path directory;
    private int generation;

    @Test void realSignedHttpAuthorizationTransactionsAndJvmRestart() throws Exception {
        directory = Files.createDirectories(Path.of("target", "settings-http-" + UUID.randomUUID()));
        bootstrap = token("master", "admin-cli", "grant_type=password&username=admin&password=" + encode(password));
        String name = "settings-" + UUID.randomUUID();
        var user = kc("POST", "/users", json.writeValueAsString(Map.of("username", name, "enabled", true,
            "firstName", "Settings", "lastName", "Proof", "email", name + "@example.com", "emailVerified", true,
            "credentials", List.of(Map.of("type", "password", "value", password, "temporary", false)))), 201);
        String subject = user.headers().firstValue("Location").orElseThrow().replaceAll(".*/", "");
        String role = "[" + kc("GET", "/roles/platform-admin", null, 200).body() + "]";
        String schema = "http_settings_" + UUID.randomUUID().toString().replace("-", "");
        String database = System.getenv("FILE_TEST_JDBC_URL");
        String dbPassword = System.getenv("FILE_TEST_DB_PASSWORD");
        var databaseJdbc = new JdbcTemplate(new DriverManagerDataSource(database, "workflow", dbPassword));
        databaseJdbc.execute("create schema " + schema);
        var jdbc = new JdbcTemplate(new DriverManagerDataSource(database + "?currentSchema=" + schema, "workflow", dbPassword));
        var proxy = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        proxy.createContext("/", exchange -> {
            try {
                var request = HttpRequest.newBuilder(URI.create(keycloak + exchange.getRequestURI())).timeout(Duration.ofSeconds(5));
                for (String header : List.of("Authorization", "Content-Type")) {
                    String value = exchange.getRequestHeaders().getFirst(header);
                    if (value != null) request.header(header, value);
                }
                var reply = http.send(request.method(exchange.getRequestMethod(),
                    HttpRequest.BodyPublishers.ofByteArray(exchange.getRequestBody().readAllBytes())).build(), HttpResponse.BodyHandlers.ofByteArray());
                exchange.sendResponseHeaders(reply.statusCode(), reply.body().length);
                exchange.getResponseBody().write(reply.body());
            } catch (Exception failure) { exchange.sendResponseHeaders(503, -1); }
            finally { exchange.close(); }
        });
        proxy.start();
        String serviceId = null;
        try {
            kc("POST", "/users/" + subject + "/role-mappings/realm", role, 204);
            String adminJwt = token(realm, "core-probe", "grant_type=password&username=" + name + "&password=" + encode(password));
            start(database + "?currentSchema=" + schema, dbPassword, proxy.getAddress().getPort());
            var paths = new ArrayList<>(List.of("/api/hpdi/v1/settings/work"));
            if (!Boolean.getBoolean("dx.proof.second-domain")) paths.add("/api/apps/servicedesk/v1/settings");
            for (String path : paths) {
                call("GET", path, null, null, 401); call("PUT", path, null, "{}", 401);
                call("GET", path, "invalid.jwt", null, 401);
                for (String regular : List.of("requester1", "agent1", "coordinator1")) {
                    String jwt = token(realm, "core-probe", "grant_type=password&username=" + regular + "&password=" + encode(password));
                    deniedBoth(path, jwt, 403);
                    if (path.contains("hpdi")) {
                        call("GET", "/api/core/v1/workspaces/system:platform/audit", jwt, null, 403);
                        call("GET", "/api/core/v1/workspaces/system%3Aplatform/audit", jwt, null, 403);
                    }
                }
                ObjectNode before = call("GET", path, adminJwt, null, 200);
                ObjectNode change = writable(before);
                if (path.contains("hpdi")) change.withObject("assignment").put("pendingLoadFactor", 0.6);
                else change.withObject("estimateDefaults").withObject("HARDWARE").put("MEDIUM", 61);
                ObjectNode after = call("PUT", path, adminJwt, change.toString(), 200);
                assertEquals(before.path("version").asLong() + 1, after.path("version").asLong());
                assertEquals(subject, after.path("updatedBy").asText());
                call("PUT", path, adminJwt, change.toString(), 409);
                var invalid = writable(after); invalid.put("version", 1.5);
                call("PUT", path, adminJwt, invalid.toString(), 400);
                invalid = writable(after); invalid.put("updatedBy", "forged"); call("PUT", path, adminJwt, invalid.toString(), 400);
                invalid = writable(after); invalid.withObject(path.contains("hpdi") ? "assignment" : "categoryGroups").put("unexpected", 1);
                call("PUT", path, adminJwt, invalid.toString(), 400);
                call("PUT", path, adminJwt, "{\"version\":1,\"version\":2}", 400);
                var executor = Executors.newFixedThreadPool(2);
                var latch = new CountDownLatch(1);
                String concurrent = writable(after).toString();
                try {
                    var first = executor.submit(() -> { latch.await(); return status("PUT", path, adminJwt, concurrent); });
                    var second = executor.submit(() -> { latch.await(); return status("PUT", path, adminJwt, concurrent); });
                    latch.countDown(); assertEquals(List.of(200,409), java.util.stream.Stream.of(first.get(),second.get()).sorted().toList());
                } finally { executor.shutdownNow(); }
                assertEquals(2, jdbc.queryForObject("select count(*) from audit_event where action=?", Integer.class,
                    path.contains("hpdi") ? "hpdi.work-settings.update" : "servicedesk.settings.update"));
                ObjectNode unchanged = call("GET", path, adminJwt, null, 200);
                jdbc.execute("create function reject_settings_audit() returns trigger language plpgsql as $$ begin raise exception 'audit proof'; end $$");
                jdbc.execute("create trigger reject_settings_audit before insert on audit_event for each row execute function reject_settings_audit()");
                try { call("PUT", path, adminJwt, writable(unchanged).toString(), 500); }
                finally { jdbc.execute("drop trigger reject_settings_audit on audit_event"); jdbc.execute("drop function reject_settings_audit()"); }
                assertEquals(unchanged, call("GET", path, adminJwt, null, 200));
                assertEquals(2, jdbc.queryForObject("select count(*) from audit_event where action=?", Integer.class,
                    path.contains("hpdi") ? "hpdi.work-settings.update" : "servicedesk.settings.update"));
                call("PUT", path, adminJwt, writable(unchanged).toString(), 200);
                assertEquals(0, jdbc.queryForObject("select count(*) from audit_event where action=? and (actor<>? or request_id<>'11111111-1111-4111-8111-111111111111' or workspace<>'system:platform' or (details->'after'->>'version')::bigint<>(details->'before'->>'version')::bigint+1)",
                    Integer.class, path.contains("hpdi") ? "hpdi.work-settings.update" : "servicedesk.settings.update", subject));
            }
            kc("DELETE", "/users/" + subject + "/role-mappings/realm", role, 204);
            for (String path : paths) deniedBoth(path, adminJwt, 403);
            kc("POST", "/users/" + subject + "/role-mappings/realm", role, 204);
            kc("PUT", "/users/" + subject, "{\"enabled\":false}", 204);
            for (String path : paths) deniedBoth(path, adminJwt, 403);
            kc("PUT", "/users/" + subject, "{\"enabled\":true}", 204);
            String service = "settings-service-" + UUID.randomUUID(), secret = UUID.randomUUID().toString();
            kc("POST", "/clients", json.writeValueAsString(Map.of("clientId", service, "secret", secret,
                "serviceAccountsEnabled", true, "publicClient", false, "protocol", "openid-connect",
                "protocolMappers", List.of(Map.of("name", "core-audience", "protocol", "openid-connect", "protocolMapper", "oidc-audience-mapper",
                    "config", Map.of("included.custom.audience", "dx-core", "access.token.claim", "true"))))), 201);
            serviceId = json.readTree(kc("GET", "/clients?clientId=" + service, null, 200).body()).get(0).path("id").asText();
            String serviceSubject = json.readTree(kc("GET", "/clients/" + serviceId + "/service-account-user", null, 200).body()).path("id").asText();
            kc("POST", "/users/" + serviceSubject + "/role-mappings/realm", role, 204);
            String serviceJwt = token(realm, service, "grant_type=client_credentials&client_secret=" + encode(secret));
            for (String path : paths) deniedBoth(path, serviceJwt, 403);
            var saved = new ArrayList<ObjectNode>();
            for (String path : paths) saved.add(call("GET", path, adminJwt, null, 200));
            call("GET", "/api/core/v1/audit/system", adminJwt, null, 404);
            stop(); start(database + "?currentSchema=" + schema, dbPassword, proxy.getAddress().getPort());
            for (int index = 0; index < paths.size(); index++) assertEquals(saved.get(index), call("GET", paths.get(index), adminJwt, null, 200));
            assertTrue(jdbc.queryForObject("select count(*)>0 from audit_event", Boolean.class));
            proxy.stop(0);
            for (String path : paths) deniedBoth(path, adminJwt, 503);
        } finally {
            stop(); proxy.stop(0);
            if (serviceId != null) kc("DELETE", "/clients/" + serviceId, null, 204);
            kc("DELETE", "/users/" + subject, null, 204);
            databaseJdbc.execute("drop schema " + schema + " cascade");
            json.writerWithDefaultPrettyPrinter().writeValue(directory.resolve("responses.json").toFile(), evidence);
        }
    }

    private void start(String database, String dbPassword, int directoryPort) throws Exception {
        int port;
        try (var socket = new java.net.ServerSocket(0)) { port = socket.getLocalPort(); }
        app = "http://127.0.0.1:" + port;
        String classpath = String.join(java.io.File.pathSeparator, Arrays.stream(
                System.getProperty("surefire.test.class.path", System.getProperty("java.class.path")).split(java.io.File.pathSeparator))
            .filter(entry -> !entry.replace('\\', '/').endsWith("/target/test-classes")).toList());
        var arguments = new ArrayList<>(List.of(Path.of(System.getProperty("java.home"), "bin", "java").toString(), "-Xmx512m", "-cp",
            classpath, "vn.haui.dx.assembly.DxOsApplication",
            "--server.port=" + port, "--spring.datasource.url=" + database, "--spring.datasource.username=workflow", "--spring.datasource.password=" + dbPassword,
            "--spring.flyway.schemas=" + database.substring(database.indexOf("currentSchema=") + 14),
            "--flowable.database-schema=" + database.substring(database.indexOf("currentSchema=") + 14),
            "--spring.datasource.hikari.maximum-pool-size=3", "--spring.security.oauth2.resourceserver.jwt.issuer-uri=" + keycloak + "/realms/" + realm,
            "--spring.security.oauth2.resourceserver.jwt.jwk-set-uri=" + keycloak + "/realms/" + realm + "/protocol/openid-connect/certs",
            "--spring.security.oauth2.resourceserver.jwt.audiences=dx-core", "--dx.directory.url=http://127.0.0.1:" + directoryPort,
            "--dx.directory.realm=" + realm, "--dx.directory.client-secret=" + System.getenv("FRESH_ADMIN_TEST_DIRECTORY_SECRET"),
            "--dx.intelligence.triage.base-url=", "--service-desk.background.scheduled=false", "--service-desk.sla.scheduled=false"));
        if (System.getProperty("settings.jar") != null) {
            arguments.subList(2, 5).clear();
            arguments.addAll(2, List.of("-jar", System.getProperty("settings.jar")));
        }
        process = new ProcessBuilder(arguments).redirectErrorStream(true).redirectOutput(directory.resolve("jvm-" + (++generation) + ".log").toFile()).start();
        for (int attempt = 0; attempt < 120; attempt++) {
            assertTrue(process.isAlive(), "Application exited; inspect " + directory);
            try {
                var reply = http.send(HttpRequest.newBuilder(URI.create(app + "/actuator/health")).timeout(Duration.ofSeconds(1)).GET().build(), HttpResponse.BodyHandlers.discarding());
                if (reply.statusCode() == 200) return;
            } catch (Exception notReady) { }
            Thread.sleep(500);
        }
        fail("Application readiness timed out: " + directory);
    }
    private void stop() throws Exception {
        if (process != null) {
            process.destroy();
            if (!process.waitFor(15, TimeUnit.SECONDS)) { process.destroyForcibly(); assertTrue(process.waitFor(10, TimeUnit.SECONDS)); }
            process = null;
        }
    }
    private ObjectNode writable(ObjectNode response) {
        ObjectNode copy = response.deepCopy(); copy.remove(List.of("updatedAt", "updatedBy")); return copy;
    }
    private void deniedBoth(String path, String jwt, int expected) throws Exception {
        call("GET", path, jwt, null, expected); call("PUT", path, jwt, "{}", expected);
    }
    private ObjectNode call(String method, String path, String jwt, String body, int expected) throws Exception {
        var reply = response(method, path, jwt, body);
        assertEquals(expected, reply.statusCode(), method + " " + path + " " + reply.body());
        return (ObjectNode) json.readTree(reply.body());
    }
    private int status(String method, String path, String jwt, String body) throws Exception { return response(method,path,jwt,body).statusCode(); }
    private HttpResponse<String> response(String method, String path, String jwt, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(app + path)).timeout(Duration.ofSeconds(15));
        if (jwt != null) request.header("Authorization", "Bearer " + jwt);
        request.header("Content-Type", "application/json");
        request.header("X-Request-ID", "11111111-1111-4111-8111-111111111111");
        var reply = http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        evidence.add(Map.of("path", path, "method", method, "status", reply.statusCode(), "bodyJson", reply.body()));
        return reply;
    }
    private HttpResponse<String> kc(String method, String path, String body, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create(keycloak + "/admin/realms/" + realm + path)).timeout(Duration.ofSeconds(10))
            .header("Authorization", "Bearer " + bootstrap).header("Content-Type", "application/json");
        var reply = http.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, reply.statusCode(), "Keycloak fixture " + method + " " + path);
        return reply;
    }
    private String token(String tokenRealm, String client, String form) throws Exception {
        var reply = http.send(HttpRequest.newBuilder(URI.create(keycloak + "/realms/" + tokenRealm + "/protocol/openid-connect/token"))
            .timeout(Duration.ofSeconds(10)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString("client_id=" + encode(client) + "&" + form)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, reply.statusCode(), "Fixture token request");
        return json.readTree(reply.body()).path("access_token").asText();
    }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
