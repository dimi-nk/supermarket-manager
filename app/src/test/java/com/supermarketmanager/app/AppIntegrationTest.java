package com.supermarketmanager.app;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** The whole app over real HTTP: REST, error mapping, SPA routing, the local-only guard and MCP. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AppIntegrationTest {
    /** Created eagerly: Spring reads the properties before JUnit would inject a @TempDir. */
    static final Path dataDir = createTempDir();

    static Path createTempDir() {
        try {
            Path dir = java.nio.file.Files.createTempDirectory("sm-app-test");
            dir.toFile().deleteOnExit();
            return dir;
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("sm.data-dir", () -> dataDir.toString());
    }

    @LocalServerPort
    int port;

    final HttpClient http = HttpClient.newHttpClient();
    final JsonMapper json = JsonMapper.builder().build();

    HttpResponse<String> send(String method, String path, String body, String... headers) throws Exception {
        var b = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        if (body != null) b.header("content-type", "application/json");
        for (int i = 0; i < headers.length; i += 2) b.header(headers[i], headers[i + 1]);
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString());
    }

    JsonNode ok(HttpResponse<String> r) {
        assertThat(r.statusCode()).as(r.body()).isBetween(200, 299);
        return json.readTree(r.body());
    }

    @BeforeAll
    void loadExamples() throws Exception {
        ok(send("POST", "/api/pantry/examples", "{}"));
    }

    @Test
    @Order(1)
    void systemInfoUsesTheConfiguredDataDir() throws Exception {
        JsonNode info = ok(send("GET", "/api/system/info", null));
        assertThat(info.get("app").asString()).isEqualTo("supermarket-manager");
        assertThat(info.get("dataDir").asString()).isEqualTo(dataDir.toString());
        assertThat(dataDir.resolve("supermarket-manager.db")).exists();
    }

    @Test
    @Order(2)
    void deltaAcceptsAnIsoDate() throws Exception {
        JsonNode d = ok(send("GET", "/api/pantry/delta?profileId=high-protein&at=" + LocalDate.now().plusMonths(2), null));
        assertThat(d.get("profileId").asString()).isEqualTo("high-protein");
        assertThat(d.get("basket").get("lines").size()).isPositive();
    }

    @Test
    @Order(3)
    void pipelineRunThenApplyOnce() throws Exception {
        JsonNode run = ok(send("POST", "/api/pipeline/run", """
                {"source":{"type":"pantry"},"candidates":"price:demo","resolve":{"resolver":"optimizer","maxRetailers":2},"export":true}"""));
        String id = run.get("runId").asString();
        assertThat(run.get("resolved").get("unresolved").size()).isZero();
        assertThat(run.get("checklist").asString()).startsWith("# Shopping list");

        assertThat(ok(send("POST", "/api/pipeline/runs/" + id + "/apply", "{}")).get("applied").size()).isPositive();
        HttpResponse<String> again = send("POST", "/api/pipeline/runs/" + id + "/apply", "{}");
        assertThat(again.statusCode()).isEqualTo(409);
        assertThat(json.readTree(again.body()).get("detail").asString()).contains("already applied");
    }

    @Test
    @Order(4)
    void optionalPrimitiveFieldsMayBeOmitted() throws Exception {
        JsonNode p = ok(send("POST", "/api/pantry/profiles", "{\"name\":\"Summer\"}"));
        assertThat(p.get("id").asString()).isEqualTo("summer");
        assertThat(p.get("active").asBoolean()).isFalse();
    }

    @Test
    @Order(4)
    void errorsAreProblemDetails() throws Exception {
        HttpResponse<String> missing = send("GET", "/api/pipeline/runs/nope", null);
        assertThat(missing.statusCode()).isEqualTo(404);
        assertThat(missing.headers().firstValue("content-type").orElse("")).contains("problem+json");

        HttpResponse<String> bad = send("POST", "/api/rules/sets", """
                {"name":"x","rules":[{"type":"UNIT_PRICE"}]}""");
        assertThat(bad.statusCode()).isEqualTo(400);
        assertThat(bad.body()).contains("min and/or max");

        HttpResponse<String> inUse = send("DELETE", "/api/pantry/categories/milk", null);
        assertThat(inUse.statusCode()).isEqualTo(400);
        assertThat(inUse.body()).contains("still used");
    }

    @Test
    @Order(5)
    void crossSiteRequestsAreRefused() throws Exception {
        assertThat(send("POST", "/api/system/shutdown", null, "Origin", "https://evil.example").statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/system/info", null, "Origin", "http://localhost:5173").statusCode()).isEqualTo(200);
    }

    @Test
    @Order(6)
    void clientRoutesServeTheReactApp() throws Exception {
        Assumptions.assumeTrue(getClass().getResource("/static/index.html") != null, "frontend not built (-Dskip.frontend)");
        HttpResponse<String> r = send("GET", "/pantry", null);
        assertThat(r.statusCode()).isEqualTo(200);
        assertThat(r.body()).contains("<div id=\"root\">");
    }

    @Test
    @Order(7)
    void mcpListsEveryModulesToolsAndRunsOne() throws Exception {
        String accept = "application/json, text/event-stream";
        HttpResponse<String> init = send("POST", "/mcp", """
                {"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"test","version":"1"}}}""",
                "accept", accept);
        assertThat(init.statusCode()).isEqualTo(200);
        String session = init.headers().firstValue("mcp-session-id").orElseThrow();
        send("POST", "/mcp", "{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\"}", "accept", accept, "mcp-session-id", session);

        JsonNode tools = rpc(send("POST", "/mcp", "{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\"}",
                "accept", accept, "mcp-session-id", session));
        var names = new java.util.TreeSet<String>();
        tools.get("result").get("tools").forEach(t -> names.add(t.get("name").asString()));
        assertThat(names).hasSize(25).contains("pantry_delta", "rules_add_rule", "prices_search", "supermarkets_search", "pipeline_run");

        // "high-protein" (in force next month) still has needs; the "regular" profile was bought in an earlier test.
        JsonNode call = rpc(send("POST", "/mcp", """
                {"jsonrpc":"2.0","id":3,"method":"tools/call","params":{"name":"pipeline_run","arguments":{
                  "profileId":"high-protein","date":"%s","candidateSource":"price:demo","resolver":"cheapest-per-line"}}}"""
                        .formatted(LocalDate.now().plusMonths(2)),
                "accept", accept, "mcp-session-id", session));
        assertThat(call.get("result").has("isError") && call.get("result").get("isError").asBoolean()).isFalse();
        JsonNode result = json.readTree(call.get("result").get("content").get(0).get("text").asString());
        assertThat(result.get("resolved").get("stores").size()).isPositive();
    }

    /** Streamable HTTP answers with plain JSON or a one-event SSE stream. */
    JsonNode rpc(HttpResponse<String> r) {
        assertThat(r.statusCode()).as(r.body()).isEqualTo(200);
        String body = r.body().strip();
        if (!body.startsWith("{")) {
            body = body.lines().filter(l -> l.startsWith("data:")).reduce((a, b) -> b).orElseThrow().substring(5);
        }
        return json.readTree(body);
    }
}
