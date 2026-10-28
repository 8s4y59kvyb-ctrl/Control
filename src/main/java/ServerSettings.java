import java.time.Duration;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * Настройки, которые преподаватель меняет на странице /admin: время на задание и число заданий по темам,
 * пауза перед пересдачей. Хранятся в базе; чего там нет — берётся из {@link Theme#defaults()}.
 * Тест запоминает правила темы при старте, поэтому сохранение действует только на новые тесты.
 */
final class ServerSettings {

    static final int MIN_MINUTES = 1, MAX_MINUTES = 60;           // время на одно задание
    static final int MIN_TASKS = 1, MAX_TASKS = 5;                // заданий в тесте
    static final int DEFAULT_RETAKE_MIN = 15, MAX_RETAKE_MIN = 240;   // пауза перед пересдачей; 0 — без паузы
    private static final String RETAKE = "retake_min";

    /** Снимок настроек: правила всех тем и пауза перед пересдачей в минутах. */
    record Values(Map<Theme, ThemeRules> rules, int retakeMin) {
    }

    private final ServerRepository repo;
    private volatile Values current;

    ServerSettings(ServerRepository repo) {
        this.repo = repo;
        this.current = load();
    }

    Values values() {
        return current;
    }

    ThemeRules rules(Theme theme) {
        return current.rules().get(theme);
    }

    Duration retakePause() {
        return Duration.ofMinutes(current.retakeMin());
    }

    /**
     * Проверяет и сохраняет новые значения. Темы, которых нет в rules, не меняются.
     * В теме без выбора сложности время одно: берётся время сложного уровня.
     */
    synchronized Values save(Map<Theme, ThemeRules> rules, int retakeMin) {
        if (retakeMin < 0 || retakeMin > MAX_RETAKE_MIN) {
            throw ServerError.badRequest("Пауза перед пересдачей — от 0 до " + MAX_RETAKE_MIN + " минут.");
        }
        Map<Theme, ThemeRules> checked = new EnumMap<>(Theme.class);
        for (Map.Entry<Theme, ThemeRules> e : rules.entrySet()) {
            Theme t = e.getKey();
            ThemeRules r = e.getValue();
            if (!t.hasLevels) r = new ThemeRules(r.hardSec(), r.hardSec(), r.tasks());
            checkMinutes(t, r.easySec());
            checkMinutes(t, r.hardSec());
            if (r.tasks() < MIN_TASKS || r.tasks() > MAX_TASKS) {
                throw ServerError.badRequest("Тема " + (t.ordinal() + 1) + ": заданий — от " + MIN_TASKS
                        + " до " + MAX_TASKS + ".");
            }
            checked.put(t, r);
        }
        repo.saveSettings(checked, Map.of(RETAKE, String.valueOf(retakeMin)));
        current = load();
        return current;
    }

    /** Возвращает значения из кода. */
    synchronized Values reset() {
        repo.resetSettings();
        current = load();
        return current;
    }

    private static void checkMinutes(Theme t, int sec) {
        if (sec % 60 != 0 || sec < MIN_MINUTES * 60 || sec > MAX_MINUTES * 60) {
            throw ServerError.badRequest("Тема " + (t.ordinal() + 1) + ": время на задание — целое число минут от "
                    + MIN_MINUTES + " до " + MAX_MINUTES + ".");
        }
    }

    private Values load() {
        Map<Theme, ThemeRules> saved = repo.themeRules();
        Map<Theme, ThemeRules> all = new EnumMap<>(Theme.class);
        for (Theme t : Theme.values()) all.put(t, saved.getOrDefault(t, t.defaults()));
        int retake = DEFAULT_RETAKE_MIN;
        String s = repo.setting(RETAKE);
        if (s != null) {
            try {
                retake = Integer.parseInt(s);
            } catch (NumberFormatException ex) {
                // испорченное значение — как будто не меняли
            }
        }
        return new Values(Collections.unmodifiableMap(all), retake);
    }
}
