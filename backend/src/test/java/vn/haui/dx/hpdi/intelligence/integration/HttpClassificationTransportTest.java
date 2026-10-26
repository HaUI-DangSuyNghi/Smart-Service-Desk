package vn.haui.dx.hpdi.intelligence.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import vn.haui.dx.hpdi.intelligence.api.ClassificationInput;
import vn.haui.dx.hpdi.intelligence.api.IntelligenceFailure;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

class HttpClassificationTransportTest {
    @ParameterizedTest
    @ValueSource(
            strings = {
                "valid",
                "class",
                "null_class",
                "state",
                "score",
                "authority",
                "model",
                "input",
                "extra",
                "unavailable"
            })
    void boundedHttpContractPreservesNeutralPinsAndNeverRetries(String mutation) throws Exception {
        var json = new ObjectMapper();
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/intelligence/v1/classify",
                exchange -> {
                    calls.incrementAndGet();
                    assertEquals(
                            "Bearer " + "x".repeat(40),
                            exchange.getRequestHeaders().getFirst("Authorization"));
                    var request = json.readTree(exchange.getRequestBody().readAllBytes());
                    assertEquals(
                            Map.of("room", "Café 🚀 available"),
                            json.convertValue(request.get("input"), Map.class));
                    ObjectNode response = request.deepCopy();
                    response.remove("input");
                    response.remove("allowedClasses");
                    response.put("operationalAuthority", false);
                    var observation = response.putObject("observation");
                    observation.put("decision", "PROPOSE").put("classId", "AVAILABLE");
                    observation.putNull("score");
                    observation
                            .put("scoreSemantics", "NOT_AVAILABLE")
                            .put("evidenceStatus", "UNVERIFIED_BY_MODEL");
                    observation.put("operationalAuthority", false);
                    switch (mutation) {
                        case "class" -> observation.put("classId", "FOREIGN");
                        case "null_class" -> observation.putNull("classId");
                        case "state" -> observation.put("decision", "ABSTAIN");
                        case "score" -> observation.put("score", .99);
                        case "authority" -> observation.put("operationalAuthority", true);
                        case "model" -> response.put("modelDigest", "f".repeat(64));
                        case "input" -> response.put("inputDigest", "f".repeat(64));
                        case "extra" -> observation.put("route", "ADMIN");
                        default -> {}
                    }
                    byte[] body = json.writeValueAsBytes(response);
                    exchange.getResponseHeaders().add("Content-Type", "application/json");
                    exchange.sendResponseHeaders(
                            mutation.equals("unavailable") ? 503 : 200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            var transport =
                    new HttpClassificationTransport(
                            new ClassificationConfiguration.Settings(
                                    "http://127.0.0.1:" + server.getAddress().getPort(),
                                    "x".repeat(40)),
                            json);
            var input =
                    new ClassificationInput(
                            Map.of("room", "Café 🚀 available"),
                            List.of("AVAILABLE", "OCCUPIED"),
                            "a".repeat(64),
                            "b".repeat(64));
            if (mutation.equals("valid")) {
                var value = transport.observe(input);
                assertEquals("AVAILABLE", value.classId());
                assertFalse(value.operationalAuthority());
            } else {
                assertThrows(IntelligenceFailure.class, () -> transport.observe(input));
            }
            assertEquals(1, calls.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void circuitStopsRepeatedUnavailableCallsWithoutRetry() throws Exception {
        var calls = new AtomicInteger();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/internal/intelligence/v1/classify",
                exchange -> {
                    calls.incrementAndGet();
                    exchange.getRequestBody().readAllBytes();
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();
                });
        server.start();
        try {
            var transport =
                    new HttpClassificationTransport(
                            new ClassificationConfiguration.Settings(
                                    "http://127.0.0.1:" + server.getAddress().getPort(),
                                    "x".repeat(40)),
                            new ObjectMapper());
            var input =
                    new ClassificationInput(
                            Map.of("input", "owned sample"),
                            List.of("A", "B"),
                            "a".repeat(64),
                            "b".repeat(64));
            for (int attempt = 0; attempt < 5; attempt++) {
                assertThrows(IntelligenceFailure.class, () -> transport.observe(input));
            }
            assertEquals(3, calls.get());
        } finally {
            server.stop(0);
        }
    }
}
