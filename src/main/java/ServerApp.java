import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.HttpResponseException;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * HTTP-слой веб-версии: маршруты Javalin, разбор JSON, защита страницы преподавателя.
 * Логика теста — в {@link ServerQuizService}, данные — в {@link ServerRepository}.
 * API преподавателя закрыто общим паролем (заголовок Basic, логин любой). Сама страница /admin
 * данных не содержит и открыта: пароль вводится в её форму, а не в окно браузера.
 */
final class ServerApp {

    private static final Logger LOG = LoggerFactory.getLogger(ServerApp.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final int MAX_BODY = 4096;
    private static final int MAX_PAGE = 1000;
    private static final int AUTH_FAILS = 10;                   // неверных паролей с одного адреса
    private static final long AUTH_WINDOW_MS = 60_000;          // за это время, затем 429

    private final ServerConfig config;
    private final ServerRepository repo;
    private final ServerQuizService quiz;
    private final Javalin app;
    private final byte[] adminHash;
    private final Map<String, Deque<Long>> authFails = new ConcurrentHashMap<>();

    ServerApp(ServerConfig config, ServerRepository repo, ServerQuizService quiz) {
        this.config = config;
        this.repo = repo;
        this.quiz = quiz;
        this.adminHash = config.adminEnabled() ? sha256(config.adminPassword()) : null;

        app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.http.maxRequestSize = MAX_BODY;
            cfg.staticFiles.add(sf -> {
                sf.hostedPath = "/";
                sf.directory = "/web";
                sf.location = Location.CLASSPATH;
                sf.headers = Map.of("Cache-Control", "no-cache");
            });
        });
        routes();
    }

    /** Запускает сервер на порту из настроек (0 — свободный порт) и возвращает фактический порт. */
    int start() {
        app.start(config.port());
        return app.port();
    }

    void stop() {
        app.stop();
    }

    private void routes() {
        app.after(ctx -> {
            ctx.header("X-Content-Type-Options", "nosniff");
            ctx.header("Referrer-Policy", "no-referrer");
            ctx.header("X-Frame-Options", "DENY");
            ctx.header("Content-Security-Policy", "default-src 'self'; img-src 'self' data:; frame-ancestors 'none'");
            if (ctx.path().startsWith("/api/")) ctx.header("Cache-Control", "no-store");
        });

        app.exception(ServerError.class, (e, ctx) -> error(ctx, e.status, e.getMessage()));
        app.exception(HttpResponseException.class, (e, ctx) -> error(ctx, e.getStatus(), e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> {
            LOG.error("Необработанная ошибка {} {}", ctx.method(), ctx.path(), e);
            error(ctx, 500, "Внутренняя ошибка сервера.");
        });
        app.error(404, ctx -> {
            if (ctx.path().startsWith("/api/")) error(ctx, 404, "Не найдено.");
        });

        // ---- курсанты ----
        app.get("/api/health", ctx -> ctx.json(Map.of("status", "ok")));
        app.get("/api/themes", ctx -> ctx.json(themes()));
        app.post("/api/login", ctx -> {
            JsonNode b = body(ctx);
            ctx.json(quiz.login(text(b, "group"), text(b, "surname")));
        });
        app.post("/api/sessions", ctx -> {
            JsonNode b = body(ctx);
            Theme theme = theme(text(b, "theme"));
            Level level = theme.hasLevels ? level(text(b, "level")) : null;
            ctx.status(201).json(quiz.start(text(b, "group"), text(b, "surname"), theme, level));
        });
        app.get("/api/sessions/{id}", ctx -> ctx.json(quiz.state(ctx.pathParam("id"))));
        app.post("/api/sessions/{id}/answer", ctx -> {
            JsonNode values = body(ctx).get("values");
            if (values == null || !values.isArray()) throw ServerError.badRequest("Нужен массив values.");
            List<String> list = new ArrayList<>();
            for (JsonNode v : values) list.add(v.isNull() ? null : v.asText());
            ctx.json(quiz.answer(ctx.pathParam("id"), list));
        });
        app.post("/api/sessions/{id}/next", ctx -> ctx.json(quiz.next(ctx.pathParam("id"))));

        // ---- преподаватель ----
        app.before("/api/admin/*", this::requireAdmin);
        app.get("/admin", ctx -> resource(ctx, "/admin/admin.html", "text/html; charset=utf-8"));
        app.get("/admin/admin.js", ctx -> resource(ctx, "/admin/admin.js", "text/javascript; charset=utf-8"));
        app.get("/api/admin/groups", ctx -> ctx.json(repo.groups()));
        app.get("/api/admin/results", ctx -> {
            ServerRepository.Filter f = filter(ctx);
            int limit = intParam(ctx, "limit", 200, 1, MAX_PAGE);
            int offset = intParam(ctx, "offset", 0, 0, Integer.MAX_VALUE);
            List<Object> items = new ArrayList<>();
            for (ServerRepository.ResultRow r : repo.find(f, limit, offset)) items.add(rowJson(r));
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("total", repo.count(f));
            out.put("items", items);
            ctx.json(out);
        });
        app.get("/api/admin/results.csv", ctx -> {
            ServerRepository.Filter f = filter(ctx);
            byte[] csv = ServerCsv.render(repo.find(f, Integer.MAX_VALUE, 0));
            ctx.contentType("text/csv; charset=utf-8");
            ctx.header("Content-Disposition", "attachment; filename=\"electroquiz-results.csv\"");
            ctx.result(csv);
        });
    }

    // ---------- вспомогательное ----------

    private static void error(Context ctx, int status, String message) {
        ctx.status(status).json(Map.of("error", message == null ? "Ошибка." : message));
    }

    private static JsonNode body(Context ctx) {
        try {
            JsonNode n = JSON.readTree(ctx.body());
            if (n == null || !n.isObject()) throw ServerError.badRequest("Ожидался JSON-объект.");
            return n;
        } catch (JsonProcessingException ex) {
            throw ServerError.badRequest("Некорректный JSON.");
        }
    }

    /** Строковое поле JSON; отсутствующее или не строка/число дают null. */
    private static String text(JsonNode b, String field) {
        JsonNode n = b.get(field);
        return n == null || !n.isValueNode() || n.isNull() ? null : n.asText();
    }

    private static Theme theme(String id) {
        try {
            return Theme.valueOf(id == null ? "" : id);
        } catch (IllegalArgumentException ex) {
            throw ServerError.badRequest("Неизвестная тема.");
        }
    }

    private static Level level(String id) {
        try {
            return Level.valueOf(id == null ? "" : id);
        } catch (IllegalArgumentException ex) {
            throw ServerError.badRequest("Выберите сложность.");
        }
    }

    private static Map<String, Object> themes() {
        List<Object> list = new ArrayList<>();
        for (Theme t : Theme.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", t.name());
            m.put("title", t.toString());
            m.put("hasLevels", t.hasLevels);
            m.put("description", t.description());
            m.put("ready", t.ready());                    // тему «в разработке» не выбрать
            m.put("timeLimitSec", t.timeLimitSec(t.effectiveLevel(Level.EASY)));      // лёгкий уровень
            m.put("timeLimitHardSec", t.timeLimitSec(Level.HARD));
            list.add(m);
        }
        List<Object> levels = new ArrayList<>();
        for (Level l : Level.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", l.name());
            m.put("title", l.title);
            m.put("rules", l.rules());
            levels.add(m);
        }
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("themes", list);
        out.put("levels", levels);
        return out;
    }

    private static Map<String, Object> rowJson(ServerRepository.ResultRow r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.id());
        m.put("date", ResultLog.DATE.format(r.when()));
        m.put("time", ResultLog.TIME.format(r.when()));
        m.put("group", r.group());
        m.put("surname", r.surname());
        m.put("theme", r.theme().title);
        m.put("level", r.theme().hasLevels && r.level() != null ? r.level().title : "—");
        m.put("grade", Grading.format(r.grade()));
        return m;
    }

    private static ServerRepository.Filter filter(Context ctx) {
        String g = ctx.queryParam("group");
        String t = ctx.queryParam("theme");
        return new ServerRepository.Filter(
                g == null || g.isBlank() ? null : ServerInput.group(g),
                t == null || t.isBlank() ? null : theme(t),
                date(ctx.queryParam("from")),
                date(ctx.queryParam("to")));
    }

    private static LocalDate date(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (DateTimeParseException ex) {
            throw ServerError.badRequest("Дата должна быть в формате ГГГГ-ММ-ДД.");
        }
    }

    private static int intParam(Context ctx, String name, int def, int min, int max) {
        String s = ctx.queryParam(name);
        if (s == null || s.isBlank()) return def;
        try {
            return Math.min(max, Math.max(min, Integer.parseInt(s.trim())));
        } catch (NumberFormatException ex) {
            throw ServerError.badRequest("Параметр " + name + " должен быть числом.");
        }
    }

    private static void resource(Context ctx, String path, String type) throws IOException {
        try (InputStream in = ServerApp.class.getResourceAsStream(path)) {
            if (in == null) throw ServerError.notFound("Не найдено.");
            ctx.contentType(type);
            ctx.header("Cache-Control", "no-cache");
            ctx.result(in.readAllBytes());
        }
    }

    // ---------- пароль преподавателя ----------

    /**
     * Заголовок Basic: логин игнорируется, проверяется только пароль. Перебор ограничен по адресу.
     * WWW-Authenticate не отправляется намеренно: иначе браузер показывает своё окно входа,
     * в котором легко вписать пароль в поле логина, и запоминает неверный пароль.
     */
    private void requireAdmin(Context ctx) {
        if (adminHash == null) {
            throw new ServerError(503, "Просмотр результатов отключён: не задан ELECTROQUIZ_ADMIN_PASSWORD.");
        }
        String ip = ctx.ip();
        if (tooManyFails(ip)) throw new ServerError(429, "Слишком много попыток, подождите минуту.");
        String h = ctx.header("Authorization");
        if (h != null && h.regionMatches(true, 0, "Basic ", 0, 6)) {
            try {
                String cred = new String(Base64.getDecoder().decode(h.substring(6).trim()), StandardCharsets.UTF_8);
                String pass = cred.substring(cred.indexOf(':') + 1);
                if (MessageDigest.isEqual(sha256(pass), adminHash)) return;     // сравнение хешей — за постоянное время
            } catch (IllegalArgumentException ex) {
                // мусор в заголовке — как неверный пароль
            }
            registerFail(ip);
            throw new ServerError(401, "Неверный пароль преподавателя.");
        }
        throw new ServerError(401, "Нужен пароль преподавателя.");
    }

    private boolean tooManyFails(String ip) {
        Deque<Long> q = authFails.get(ip);
        if (q == null) return false;
        synchronized (q) {
            long limit = System.currentTimeMillis() - AUTH_WINDOW_MS;
            while (!q.isEmpty() && q.peekFirst() < limit) q.pollFirst();
            return q.size() >= AUTH_FAILS;
        }
    }

    private void registerFail(String ip) {
        Deque<Long> q = authFails.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (q) {
            q.addLast(System.currentTimeMillis());
        }
    }

    private static byte[] sha256(String s) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
