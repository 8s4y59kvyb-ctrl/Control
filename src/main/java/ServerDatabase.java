import org.sqlite.Collation;
import org.sqlite.SQLiteConfig;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.Collator;
import java.util.Locale;

/**
 * SQLite без пула: соединение открывается на каждую операцию (для группы курсантов это дёшево),
 * а busy_timeout заставляет параллельные записи ждать друг друга, а не падать. Режим WAL не включаем:
 * записей мало (по одной на тест), а лишние файлы -wal/-shm мешают копировать базу и удалять её на Windows.
 */
final class ServerDatabase {

    private static final String[] SCHEMA = {
            """
            CREATE TABLE IF NOT EXISTS cadets (
                id         INTEGER PRIMARY KEY AUTOINCREMENT,
                grp        TEXT NOT NULL,
                surname    TEXT NOT NULL,
                created_at TEXT NOT NULL,
                UNIQUE (grp, surname)
            )""",
            """
            CREATE TABLE IF NOT EXISTS sessions (
                id          TEXT PRIMARY KEY,
                cadet_id    INTEGER NOT NULL REFERENCES cadets(id),
                theme       TEXT NOT NULL,
                level       TEXT,
                started_at  TEXT NOT NULL,
                finished_at TEXT,
                status      TEXT NOT NULL DEFAULT 'in_progress' CHECK (status IN ('in_progress', 'finished'))
            )""",
            """
            CREATE TABLE IF NOT EXISTS results (
                id          INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id  TEXT NOT NULL UNIQUE REFERENCES sessions(id),
                cadet_id    INTEGER NOT NULL REFERENCES cadets(id),
                taken_at    TEXT NOT NULL,
                grp         TEXT NOT NULL,
                surname     TEXT NOT NULL,
                theme       TEXT NOT NULL,
                level       TEXT,
                grade       REAL NOT NULL,
                task_grades TEXT NOT NULL
            )""",
            // настройки со страницы преподавателя; темы без строки работают по значениям из Theme
            """
            CREATE TABLE IF NOT EXISTS theme_settings (
                theme    TEXT PRIMARY KEY,
                easy_sec INTEGER NOT NULL,
                hard_sec INTEGER NOT NULL,
                tasks    INTEGER NOT NULL
            )""",
            """
            CREATE TABLE IF NOT EXISTS settings (
                name  TEXT PRIMARY KEY,
                value TEXT NOT NULL
            )""",
            "CREATE INDEX IF NOT EXISTS idx_results_taken_at ON results(taken_at)",
            "CREATE INDEX IF NOT EXISTS idx_results_grp ON results(grp)",
            "CREATE INDEX IF NOT EXISTS idx_results_theme ON results(theme)",
            "CREATE INDEX IF NOT EXISTS idx_sessions_cadet ON sessions(cadet_id)"
    };

    private final String url;
    private final SQLiteConfig config = new SQLiteConfig();

    /** Создаёт файл и таблицы, если их ещё нет. */
    ServerDatabase(Path file) {
        try {
            Path dir = file.toAbsolutePath().getParent();
            if (dir != null) Files.createDirectories(dir);
        } catch (IOException ex) {
            throw new IllegalStateException("Не удалось создать папку базы данных: " + file, ex);
        }
        url = "jdbc:sqlite:" + file.toAbsolutePath();
        config.setBusyTimeout(5000);
        config.enforceForeignKeys(true);
        try (Connection c = open(); Statement st = c.createStatement()) {
            for (String sql : SCHEMA) st.execute(sql);
            st.execute("PRAGMA user_version = 2");
        } catch (SQLException ex) {
            throw new IllegalStateException("Не удалось подготовить базу данных " + file, ex);
        }
    }

    /**
     * Имя сортировки по русскому алфавиту. Встроенная BINARY сравнивает коды символов: «Ё» (U+0401) встала бы
     * перед «А», а заглавные буквы — перед строчными. Сортировка живёт в соединении, поэтому регистрируется при каждом open().
     */
    static final String RU = "RU_ALPHA";

    Connection open() throws SQLException {
        Connection c = DriverManager.getConnection(url, config.toProperties());
        try {
            Collator collator = Collator.getInstance(new Locale("ru", "RU"));
            Collation.create(c, RU, new Collation() {
                @Override
                protected int xCompare(String a, String b) {
                    return collator.compare(a, b);
                }
            });
        } catch (SQLException | RuntimeException ex) {
            c.close();
            throw ex;
        }
        return c;
    }
}
