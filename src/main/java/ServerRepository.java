import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Доступ к данным: курсанты, сессии тестов и результаты. Чистый JDBC, все значения — через параметры. */
final class ServerRepository {

    /** Время в базе хранится текстом ISO, поэтому сравнение строк совпадает с сравнением времени. */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    /** Строка журнала результатов. level == null — тема без выбора сложности. */
    record ResultRow(long id, LocalDateTime when, String group, String surname,
                     Theme theme, Level level, double grade) {
    }

    /** Условия отбора; null — условие не задано. Даты включительно. */
    record Filter(String group, Theme theme, LocalDate from, LocalDate to) {
        static final Filter NONE = new Filter(null, null, null, null);
    }

    private final ServerDatabase db;

    ServerRepository(ServerDatabase db) {
        this.db = db;
    }

    /** Находит курсанта по группе и фамилии или заводит нового; возвращает его id. */
    long upsertCadet(String group, String surname, LocalDateTime now) {
        try (Connection c = db.open()) {
            try (PreparedStatement ins = c.prepareStatement(
                    "INSERT OR IGNORE INTO cadets (grp, surname, created_at) VALUES (?, ?, ?)")) {
                ins.setString(1, group);
                ins.setString(2, surname);
                ins.setString(3, ISO.format(now));
                ins.executeUpdate();
            }
            try (PreparedStatement sel = c.prepareStatement("SELECT id FROM cadets WHERE grp = ? AND surname = ?")) {
                sel.setString(1, group);
                sel.setString(2, surname);
                try (ResultSet rs = sel.executeQuery()) {
                    rs.next();
                    return rs.getLong(1);
                }
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    long cadetCount() {
        return scalar("SELECT COUNT(*) FROM cadets");
    }

    void createSession(String id, long cadetId, Theme theme, Level level, LocalDateTime now) {
        try (Connection c = db.open();
             PreparedStatement ps = c.prepareStatement(
                     "INSERT INTO sessions (id, cadet_id, theme, level, started_at) VALUES (?, ?, ?, ?, ?)")) {
            ps.setString(1, id);
            ps.setLong(2, cadetId);
            ps.setString(3, theme.name());
            ps.setString(4, level == null ? null : level.name());
            ps.setString(5, ISO.format(now));
            ps.executeUpdate();
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /** Статус сессии: «in_progress», «finished» или null, если такой сессии нет. */
    String sessionStatus(String id) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement("SELECT status FROM sessions WHERE id = ?")) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /**
     * Закрывает сессию и записывает результат одной транзакцией; возвращает номер записи.
     * Повторное завершение той же сессии невозможно (UNIQUE по session_id) — результат не задвоится.
     */
    long finishSession(String sessionId, long cadetId, String group, String surname, Theme theme, Level level,
                       double grade, List<Integer> taskGrades, LocalDateTime now) {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try {
                try (PreparedStatement up = c.prepareStatement(
                        "UPDATE sessions SET status = 'finished', finished_at = ? WHERE id = ? AND status = 'in_progress'")) {
                    up.setString(1, ISO.format(now));
                    up.setString(2, sessionId);
                    if (up.executeUpdate() != 1) throw new IllegalStateException("Сессия уже завершена: " + sessionId);
                }
                long id;
                try (PreparedStatement ins = c.prepareStatement(
                        "INSERT INTO results (session_id, cadet_id, taken_at, grp, surname, theme, level, grade, task_grades) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)", Statement.RETURN_GENERATED_KEYS)) {
                    ins.setString(1, sessionId);
                    ins.setLong(2, cadetId);
                    ins.setString(3, ISO.format(now));
                    ins.setString(4, group);
                    ins.setString(5, surname);
                    ins.setString(6, theme.name());
                    ins.setString(7, level == null ? null : level.name());
                    ins.setDouble(8, grade);
                    ins.setString(9, taskGrades.stream().map(String::valueOf).collect(Collectors.joining(",")));
                    ins.executeUpdate();
                    try (ResultSet keys = ins.getGeneratedKeys()) {
                        keys.next();
                        id = keys.getLong(1);
                    }
                }
                c.commit();
                return id;
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /** Когда курсант последний раз закончил тест по теме; null — ещё не заканчивал. */
    LocalDateTime lastFinished(long cadetId, Theme theme) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(
                "SELECT MAX(taken_at) FROM results WHERE cadet_id = ? AND theme = ?")) {
            ps.setLong(1, cadetId);
            ps.setString(2, theme.name());
            try (ResultSet rs = ps.executeQuery()) {
                String s = rs.next() ? rs.getString(1) : null;
                return s == null ? null : LocalDateTime.parse(s, ISO);
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /**
     * Записи журнала по фильтру: по группе, внутри группы по фамилии (русский алфавит),
     * у одного курсанта новые попытки сверху. Сортировка в SQL, чтобы LIMIT/OFFSET делили уже упорядоченный список.
     */
    List<ResultRow> find(Filter f, int limit, int offset) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT id, taken_at, grp, surname, theme, level, grade FROM results" + where(f, args)
                + " ORDER BY grp COLLATE " + ServerDatabase.RU + ", surname COLLATE " + ServerDatabase.RU
                + ", taken_at DESC, id DESC LIMIT ? OFFSET ?";
        args.add(limit);
        args.add(offset);
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                List<ResultRow> rows = new ArrayList<>();
                while (rs.next()) {
                    String level = rs.getString(6);
                    rows.add(new ResultRow(rs.getLong(1), LocalDateTime.parse(rs.getString(2), ISO),
                            rs.getString(3), rs.getString(4), Theme.valueOf(rs.getString(5)),
                            level == null ? null : Level.valueOf(level), rs.getDouble(7)));
                }
                return rows;
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    long count(Filter f) {
        List<Object> args = new ArrayList<>();
        String sql = "SELECT COUNT(*) FROM results" + where(f, args);
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement(sql)) {
            bind(ps, args);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    // ---------- настройки ----------

    /** Изменённые правила тем. Строки тем, которых больше нет в коде, пропускаются. */
    Map<Theme, ThemeRules> themeRules() {
        try (Connection c = db.open(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT theme, easy_sec, hard_sec, tasks FROM theme_settings")) {
            Map<Theme, ThemeRules> map = new EnumMap<>(Theme.class);
            while (rs.next()) {
                String name = rs.getString(1);
                Theme t = Arrays.stream(Theme.values()).filter(x -> x.name().equals(name)).findFirst().orElse(null);
                if (t != null) map.put(t, new ThemeRules(rs.getInt(2), rs.getInt(3), rs.getInt(4)));
            }
            return map;
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /** Значение настройки или null, если её не меняли. */
    String setting(String name) {
        try (Connection c = db.open(); PreparedStatement ps = c.prepareStatement("SELECT value FROM settings WHERE name = ?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /**
     * Записывает правила тем и настройки одной транзакцией (заменяя прежние значения).
     * Значение null удаляет строку: дальше действует значение из кода.
     */
    void saveSettings(Map<Theme, ThemeRules> rules, Map<String, String> settings) {
        try (Connection c = db.open()) {
            c.setAutoCommit(false);
            try (PreparedStatement th = c.prepareStatement(
                         "INSERT OR REPLACE INTO theme_settings (theme, easy_sec, hard_sec, tasks) VALUES (?, ?, ?, ?)");
                 PreparedStatement thDel = c.prepareStatement("DELETE FROM theme_settings WHERE theme = ?");
                 PreparedStatement st = c.prepareStatement("INSERT OR REPLACE INTO settings (name, value) VALUES (?, ?)");
                 PreparedStatement stDel = c.prepareStatement("DELETE FROM settings WHERE name = ?")) {
                for (Map.Entry<Theme, ThemeRules> e : rules.entrySet()) {
                    if (e.getValue() == null) {
                        thDel.setString(1, e.getKey().name());
                        thDel.executeUpdate();
                        continue;
                    }
                    th.setString(1, e.getKey().name());
                    th.setInt(2, e.getValue().easySec());
                    th.setInt(3, e.getValue().hardSec());
                    th.setInt(4, e.getValue().tasks());
                    th.executeUpdate();
                }
                for (Map.Entry<String, String> e : settings.entrySet()) {
                    if (e.getValue() == null) {
                        stDel.setString(1, e.getKey());
                        stDel.executeUpdate();
                        continue;
                    }
                    st.setString(1, e.getKey());
                    st.setString(2, e.getValue());
                    st.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException ex) {
                c.rollback();
                throw ex;
            }
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /** Удаляет все изменённые настройки: дальше действуют значения из кода. */
    void resetSettings() {
        try (Connection c = db.open(); Statement st = c.createStatement()) {
            st.executeUpdate("DELETE FROM theme_settings");
            st.executeUpdate("DELETE FROM settings");
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    /** Группы, по которым есть результаты, для выпадающего списка фильтра. */
    List<String> groups() {
        try (Connection c = db.open(); Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT DISTINCT grp FROM results ORDER BY grp COLLATE " + ServerDatabase.RU)) {
            List<String> list = new ArrayList<>();
            while (rs.next()) list.add(rs.getString(1));
            return list;
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    private static String where(Filter f, List<Object> args) {
        List<String> parts = new ArrayList<>();
        if (f.group() != null) {
            parts.add("grp = ?");
            args.add(f.group());
        }
        if (f.theme() != null) {
            parts.add("theme = ?");
            args.add(f.theme().name());
        }
        if (f.from() != null) {
            parts.add("taken_at >= ?");
            args.add(ISO.format(f.from().atStartOfDay()));
        }
        if (f.to() != null) {
            parts.add("taken_at < ?");                       // до начала следующих суток — «по» включительно
            args.add(ISO.format(f.to().plusDays(1).atStartOfDay()));
        }
        return parts.isEmpty() ? "" : " WHERE " + String.join(" AND ", parts);
    }

    private static void bind(PreparedStatement ps, List<Object> args) throws SQLException {
        for (int i = 0; i < args.size(); i++) ps.setObject(i + 1, args.get(i));
    }

    private long scalar(String sql) {
        try (Connection c = db.open(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException ex) {
            throw fail(ex);
        }
    }

    private static IllegalStateException fail(SQLException ex) {
        return new IllegalStateException("Ошибка базы данных: " + ex.getMessage(), ex);
    }
}
