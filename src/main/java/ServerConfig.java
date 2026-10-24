import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Настройки сервера. Источники по убыванию приоритета: системное свойство (-Delectroquiz.port=…),
 * переменная окружения (ELECTROQUIZ_PORT), значение по умолчанию.
 * Пароль преподавателя нигде не хранится в коде: без него страница преподавателя отключена.
 *
 * @param port          порт HTTP
 * @param dbFile        файл SQLite
 * @param adminPassword пароль преподавателя; null — просмотр результатов отключён
 */
record ServerConfig(int port, Path dbFile, String adminPassword) {

    static final int DEFAULT_PORT = 8080;

    boolean adminEnabled() {
        return adminPassword != null && !adminPassword.isEmpty();
    }

    static ServerConfig load(Map<String, String> env) {
        int port = DEFAULT_PORT;
        String p = pick(env, "electroquiz.port", "ELECTROQUIZ_PORT");
        if (p != null) {
            try {
                port = Integer.parseInt(p.trim());
            } catch (NumberFormatException ex) {
                throw new IllegalArgumentException("Некорректный порт: " + p);
            }
            if (port < 0 || port > 65535) throw new IllegalArgumentException("Некорректный порт: " + p);
        }
        String db = pick(env, "electroquiz.db", "ELECTROQUIZ_DB");
        Path dbFile = db != null ? Paths.get(db) : defaultDbDir().resolve("electroquiz.db");
        return new ServerConfig(port, dbFile, pick(env, "electroquiz.adminPassword", "ELECTROQUIZ_ADMIN_PASSWORD"));
    }

    private static String pick(Map<String, String> env, String prop, String var) {
        String v = System.getProperty(prop);
        if (v == null || v.isBlank()) v = env.get(var);
        return (v == null || v.isBlank()) ? null : v;
    }

    /** Папка с jar-файлом; при запуске из IDE (классы, а не jar) — текущая папка. */
    private static Path defaultDbDir() {
        try {
            Path loc = Paths.get(ServerConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (loc.toString().endsWith(".jar")) return loc.toAbsolutePath().getParent();
        } catch (URISyntaxException | RuntimeException ex) {
            // не удалось определить — берём текущую папку
        }
        return Paths.get("").toAbsolutePath();
    }
}
