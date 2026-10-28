import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** HTTP-слой на реальном сервере со случайным портом и временной базой. */
class ServerAppTest {

    private static final long SEED = 11;
    private static final ObjectMapper JSON = new ObjectMapper();

    private ServerApp server;
    private String base;
    private final HttpClient http = HttpClient.newHttpClient();

    @BeforeEach
    void setUp(@TempDir Path dir) {
        ServerConfig cfg = new ServerConfig(0, dir.resolve("t.db"), "secret");
        ServerRepository repo = new ServerRepository(new ServerDatabase(cfg.dbFile()));
        server = new ServerApp(cfg, repo, new ServerQuizService(repo,
                java.time.Clock.systemDefaultZone(), ServerTestSupport.seeded(SEED)));
        base = "http://localhost:" + server.start();
    }

    @AfterEach
    void tearDown() {
        server.stop();
    }

    private HttpResponse<String> send(String method, String path, String body, String auth) throws Exception {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (body == null) {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        if (auth != null) {
            b.header("Authorization", "Basic " + Base64.getEncoder().encodeToString(("admin:" + auth).getBytes(StandardCharsets.UTF_8)));
        }
        return http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private JsonNode json(HttpResponse<String> r) throws Exception {
        return JSON.readTree(r.body());
    }

    private static String answerBody(List<String> values) {
        return "{\"values\":[" + String.join(",", values.stream().map(v -> "\"" + v + "\"").toList()) + "]}";
    }

    @Test
    void servesFrontendAndThemes() throws Exception {
        HttpResponse<String> index = send("GET", "/", null, null);
        assertEquals(200, index.statusCode());
        assertTrue(index.body().contains("ElectroQuiz"));
        assertEquals(200, send("GET", "/app.js", null, null).statusCode());
        assertEquals("nosniff", index.headers().firstValue("X-Content-Type-Options").orElse(""));

        JsonNode meta = json(send("GET", "/api/themes", null, null));
        assertEquals(5, meta.get("themes").size());
        assertEquals("OHM", meta.get("themes").get(0).get("id").asText());
        assertTrue(meta.get("themes").get(0).get("ready").asBoolean());
        assertTrue(meta.get("themes").get(3).get("ready").asBoolean());      // переменный ток — готов
        assertEquals("RESONANCE", meta.get("themes").get(4).get("id").asText());
        assertTrue(meta.get("themes").get(4).get("description").asText().contains("резонансную частоту"));
        assertEquals(2, meta.get("levels").size());
    }

    @Test
    void fullQuizOverHttpEndsInJournalAndCsv() throws Exception {
        HttpResponse<String> login = send("POST", "/api/login", "{\"group\":\"км-21\",\"surname\":\"иванов\"}", null);
        assertEquals(200, login.statusCode());
        assertEquals("КМ-21", json(login).get("group").asText());

        HttpResponse<String> created = send("POST", "/api/sessions",
                "{\"group\":\"км-21\",\"surname\":\"иванов\",\"theme\":\"OHM\",\"level\":\"EASY\"}", null);
        assertEquals(201, created.statusCode());
        JsonNode st = json(created);
        String id = st.get("sessionId").asText();
        assertTrue(st.get("task").get("scheme").get("ops").size() > 5);
        assertEquals(200, send("GET", "/api/sessions/" + id, null, null).statusCode());

        for (int n = 0; n < Theme.OHM.tasks; n++) {
            st = json(send("POST", "/api/sessions/" + id + "/answer",
                    answerBody(ServerTestSupport.answers(Theme.OHM, Level.EASY, SEED, n)), null));
            assertEquals("task_done", st.get("status").asText());
            assertEquals(5, st.get("taskGrade").asInt());
            st = json(send("POST", "/api/sessions/" + id + "/next", null, null));
        }
        assertEquals("finished", st.get("status").asText());
        assertEquals("5", st.get("result").get("finalGrade").asText());

        // преподаватель видит запись и может отфильтровать
        JsonNode list = json(send("GET", "/api/admin/results?group=" + enc("км-21"), null, "secret"));
        assertEquals(1, list.get("total").asInt());
        assertEquals("Иванов", list.get("items").get(0).get("surname").asText());
        assertEquals(0, json(send("GET", "/api/admin/results?group=" + enc("ЭМ-1"), null, "secret")).get("total").asInt());
        assertEquals(1, json(send("GET", "/api/admin/results?theme=OHM&from=2000-01-01", null, "secret")).get("total").asInt());

        HttpResponse<String> csv = send("GET", "/api/admin/results.csv", null, "secret");
        assertEquals(200, csv.statusCode());
        assertTrue(csv.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
        String[] lines = csv.body().split("\r\n");
        assertEquals("\uFEFF№;Дата;Время;Группа;Имя;Тема;Сложность;Оценка", lines[0]);
        assertTrue(lines[1].matches("1;\\d\\d\\.\\d\\d\\.\\d{4};\\d\\d:\\d\\d:\\d\\d;КМ-21;Иванов;" + Theme.OHM.title + ";Лёгкий;5"), lines[1]);
    }

    @Test
    void rejectsBadRequests() throws Exception {
        assertEquals(400, send("POST", "/api/login", "{\"group\":\"\",\"surname\":\"Иванов\"}", null).statusCode());
        assertEquals(400, send("POST", "/api/login", "не json", null).statusCode());
        assertEquals(400, send("POST", "/api/login", "[1,2]", null).statusCode());
        assertEquals(400, send("POST", "/api/sessions",
                "{\"group\":\"КМ-21\",\"surname\":\"Иванов\",\"theme\":\"NOPE\"}", null).statusCode());
        assertEquals(400, send("POST", "/api/sessions",
                "{\"group\":\"КМ-21\",\"surname\":\"Иванов\",\"theme\":\"OHM\"}", null).statusCode());   // нет сложности
        assertEquals(404, send("GET", "/api/sessions/unknown", null, null).statusCode());
        assertEquals(404, send("POST", "/api/sessions/unknown/answer", "{\"values\":[\"1\"]}", null).statusCode());
        assertEquals(404, send("GET", "/api/nothing", null, null).statusCode());
        assertEquals(413, send("POST", "/api/login", "{\"group\":\"" + "x".repeat(10_000) + "\"}", null).statusCode());

        JsonNode err = json(send("POST", "/api/login", "{\"group\":\"КМ-21\",\"surname\":\"=cmd\"}", null));
        assertFalse(err.get("error").asText().isEmpty());

        String id = json(send("POST", "/api/sessions",
                "{\"group\":\"КМ-21\",\"surname\":\"Иванов\",\"theme\":\"DIVIDERS\"}", null)).get("sessionId").asText();
        assertEquals(400, send("POST", "/api/sessions/" + id + "/answer", "{\"values\":\"1\"}", null).statusCode());
        assertEquals(400, send("POST", "/api/sessions/" + id + "/answer", "{\"values\":[\"1\"]}", null).statusCode());  // нужно два
        assertEquals(409, send("POST", "/api/sessions/" + id + "/next", null, null).statusCode());
    }

    @Test
    void adminAreaRequiresPassword() throws Exception {
        for (String path : new String[]{"/api/admin/results", "/api/admin/results.csv", "/api/admin/groups"}) {
            HttpResponse<String> none = send("GET", path, null, null);
            assertEquals(401, none.statusCode(), path);
            // без WWW-Authenticate браузер не показывает своё окно входа — пароль вводится в форму страницы
            assertTrue(none.headers().firstValue("WWW-Authenticate").isEmpty(), path);
            assertEquals(401, send("GET", path, null, "wrong").statusCode(), path);
            assertEquals(200, send("GET", path, null, "secret").statusCode(), path);
        }
    }

    @Test
    void adminPageIsOpenButHasNoData() throws Exception {
        for (String path : new String[]{"/admin", "/admin/admin.js"}) {
            assertEquals(200, send("GET", path, null, null).statusCode(), path);
        }
    }

    @Test
    void adminIsDisabledWithoutPasswordAndBruteForceIsLimited(@TempDir Path dir) throws Exception {
        for (int i = 0; i < 10; i++) assertEquals(401, send("GET", "/api/admin/groups", null, "bad" + i).statusCode());
        assertEquals(429, send("GET", "/api/admin/groups", null, "secret").statusCode());   // даже верный пароль — после блокировки

        ServerConfig cfg = new ServerConfig(0, dir.resolve("x.db"), null);
        ServerRepository repo = new ServerRepository(new ServerDatabase(cfg.dbFile()));
        ServerApp open = new ServerApp(cfg, repo, new ServerQuizService(repo));
        try {
            String url = "http://localhost:" + open.start() + "/api/admin/results";
            HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(url)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(503, r.statusCode());
        } finally {
            open.stop();
        }
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @Test
    void teacherChangesSettings() throws Exception {
        assertEquals(401, send("GET", "/api/admin/settings", null, null).statusCode());
        assertEquals(401, send("PUT", "/api/admin/settings", "{}", "wrong").statusCode());

        JsonNode s = json(send("GET", "/api/admin/settings", null, "secret"));
        assertEquals(Theme.values().length, s.get("themes").size());
        assertEquals(10, s.get("themes").get(0).get("easyMin").asInt());
        assertEquals(15, s.get("retakeMin").asInt());

        String body = "{\"retakeMin\":5,\"themes\":[{\"id\":\"OHM\",\"easyMin\":\"3\",\"hardMin\":4,\"tasks\":3}]}";
        HttpResponse<String> saved = send("PUT", "/api/admin/settings", body, "secret");
        assertEquals(200, saved.statusCode(), saved.body());
        assertEquals(5, json(saved).get("retakeMin").asInt());

        JsonNode ohm = json(send("GET", "/api/themes", null, null)).get("themes").get(0);
        assertEquals(3 * 60, ohm.get("timeLimitSec").asInt());
        assertEquals(4 * 60, ohm.get("timeLimitHardSec").asInt());
        assertEquals(3, ohm.get("tasks").asInt());
        assertTrue(ohm.get("description").asText().startsWith("Три задания"));

        HttpResponse<String> bad = send("PUT", "/api/admin/settings",
                "{\"retakeMin\":5,\"themes\":[{\"id\":\"OHM\",\"easyMin\":0,\"hardMin\":4,\"tasks\":2}]}", "secret");
        assertEquals(400, bad.statusCode());
        assertTrue(json(bad).get("error").asText().contains("Тема 1"));
        assertEquals(400, send("PUT", "/api/admin/settings", "{\"retakeMin\":\"x\",\"themes\":[]}", "secret").statusCode());

        JsonNode reset = json(send("POST", "/api/admin/settings/reset", null, "secret"));
        assertEquals(10, reset.get("themes").get(0).get("easyMin").asInt());
        assertEquals(15, reset.get("retakeMin").asInt());
    }
}
