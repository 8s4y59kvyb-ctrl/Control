import java.util.EnumMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ход теста: попытки, оценки, время, итог и записанный результат. */
class ServerQuizServiceTest {

    private static final long SEED = 7;

    private ServerRepository repo;
    private ServerQuizService quiz;
    private ServerTestSupport.TestClock clock;

    @BeforeEach
    void setUp(@TempDir Path dir) {
        repo = new ServerRepository(new ServerDatabase(dir.resolve("t.db")));
        clock = new ServerTestSupport.TestClock();
        quiz = new ServerQuizService(repo, clock, ServerTestSupport.seeded(SEED));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object o) {
        return (Map<String, Object>) o;
    }

    private Map<String, Object> start(Theme theme, Level level) {
        return quiz.start("км-21", " иванов ", theme, level);
    }

    @Test
    void firstTryOnEasyGivesFiveAndIsRecorded() {
        Map<String, Object> st = start(Theme.OHM, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals("task", st.get("status"));
        assertEquals("КМ-21", st.get("group"));
        assertEquals("Иванов", st.get("surname"));
        assertEquals(1, st.get("attemptsMax"));

        assertEquals(2, st.get("tasksTotal"));                          // в теме 1 прямая и обратная задачи
        for (int n = 0; n < Theme.OHM.tasks; n++) {
            st = quiz.answer(id, ServerTestSupport.answers(Theme.OHM, Level.EASY, SEED, n));
            assertEquals("task_done", st.get("status"));
            assertEquals(5, st.get("taskGrade"));
            st = quiz.next(id);
        }
        assertEquals("finished", st.get("status"));
        assertEquals("5", map(st.get("result")).get("finalGrade"));

        List<ServerRepository.ResultRow> rows = repo.find(ServerRepository.Filter.NONE, 10, 0);
        assertEquals(1, rows.size());
        assertEquals("КМ-21", rows.get(0).group());
        assertEquals(5.0, rows.get(0).grade());
        assertEquals(Level.EASY, rows.get(0).level());

        // повторное «Далее» не задваивает результат
        quiz.next(id);
        assertEquals(1, repo.count(ServerRepository.Filter.NONE));
    }

    @Test
    void hardLevelGradesByAttemptAndShowsHintOnWrongAnswer() {
        Map<String, Object> st = start(Theme.OHM, Level.HARD);
        String id = (String) st.get("sessionId");
        assertEquals(3, st.get("attemptsMax"));
        assertEquals(15 * 60, st.get("timeLimitSec"));                 // сложный уровень темы 1 — 15 минут
        assertEquals(15L * 60, ((Number) st.get("secondsLeft")).longValue());
        List<String> right = ServerTestSupport.answers(Theme.OHM, Level.HARD, SEED, 0);

        st = quiz.answer(id, ServerTestSupport.wrong(right));
        assertEquals("task", st.get("status"));
        assertEquals(1, st.get("attempt"));
        assertEquals("bad", st.get("messageKind"));
        assertFalse(((String) st.get("message")).contains("Правильный ответ"));   // ответ до конца задания не раскрывается

        st = quiz.answer(id, right);
        assertEquals("task_done", st.get("status"));
        assertEquals(4, st.get("taskGrade"));                 // верно со 2-й попытки
        assertTrue(((String) st.get("message")).contains("с попытки 2"));
    }

    @Test
    void failedTaskMakesFinalGradeTwo() {
        Map<String, Object> st = start(Theme.EQUIVALENT, Level.HARD);   // нужны два задания
        String id = (String) st.get("sessionId");
        List<String> bad = ServerTestSupport.wrong(ServerTestSupport.answers(Theme.EQUIVALENT, Level.HARD, SEED, 0));
        for (int i = 0; i < 3; i++) st = quiz.answer(id, bad);
        assertEquals(2, st.get("taskGrade"));
        assertTrue(((String) st.get("message")).contains("Правильный ответ"));   // теперь можно показать
        quiz.next(id);
        st = quiz.answer(id, ServerTestSupport.answers(Theme.EQUIVALENT, Level.HARD, SEED, 1));
        assertEquals(5, st.get("taskGrade"));
        st = quiz.next(id);
        assertEquals("2", map(st.get("result")).get("finalGrade"));   // одна двойка — итог 2, а не среднее
        assertFalse((Boolean) map(st.get("result")).get("passed"));
    }

    @Test
    void invalidInputDoesNotSpendAttempt() {
        Map<String, Object> st = start(Theme.EQUIVALENT, Level.EASY);
        String id = (String) st.get("sessionId");
        st = quiz.answer(id, List.of("abc"));
        assertEquals(0, st.get("attempt"));
        assertEquals("warn", st.get("messageKind"));
        assertEquals(0, quiz.answer(id, List.of("NaN")).get("attempt"));
        assertEquals(0, quiz.answer(id, List.of("1".repeat(100))).get("attempt"));
        assertThrows(ServerError.class, () -> quiz.answer(id, List.of("1", "2")));     // не то число полей
    }

    @Test
    void twoAnswerThemeAcceptsCommaDecimalAndCountsPartial() {
        Map<String, Object> st = start(Theme.DIVIDERS, null);          // уровня нет, всегда 3 попытки
        String id = (String) st.get("sessionId");
        assertEquals(3, st.get("attemptsMax"));
        assertNull(st.get("level"));
        List<String> right = ServerTestSupport.answers(Theme.DIVIDERS, null, SEED, 0);
        st = quiz.answer(id, List.of(right.get(0), "0"));
        assertTrue(((String) st.get("message")).contains("верно введено 1 из 2"));
        st = quiz.answer(id, right.stream().map(v -> v.replace('.', ',')).toList());
        assertEquals("task_done", st.get("status"));
        assertEquals(4, st.get("taskGrade"));
    }

    @Test
    void timeoutGivesTwoAndLateAnswerIsRejected() {
        Map<String, Object> st = start(Theme.OHM, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals((long) Theme.OHM.timeLimitSec(Level.EASY), ((Number) st.get("secondsLeft")).longValue());
        clock.advanceSeconds(Theme.OHM.timeLimitSec(Level.EASY) + 1);               // в пределах запаса на сеть
        assertEquals("task", quiz.state(id).get("status"));
        clock.advanceSeconds(ServerQuizService.GRACE_SEC + 1);

        st = quiz.answer(id, ServerTestSupport.answers(Theme.OHM, Level.EASY, SEED, 0));   // верный, но поздно
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertTrue(((String) st.get("message")).startsWith("Время вышло"));
    }

    @Test
    void stateNeverContainsCorrectValues() {
        Map<String, Object> st = start(Theme.DIVIDERS, null);
        String json = st.toString();
        for (String v : ServerTestSupport.answers(Theme.DIVIDERS, null, SEED, 0)) {
            assertFalse(json.contains("value"), "в состоянии не должно быть поля со значением");
            assertFalse(map(st.get("task")).toString().contains("=" + v));
        }
        for (Object f : (List<?>) map(st.get("task")).get("fields")) {
            assertEquals(List.of("symbol", "unit"), List.copyOf(map(f).keySet()));
        }
        Map<String, Object> scheme = map(map(st.get("task")).get("scheme"));
        assertFalse(((List<?>) scheme.get("ops")).isEmpty());
        assertFalse(((List<?>) scheme.get("flow")).isEmpty());
    }

    @Test
    void acChartShownOnlyAfterCorrectAnswer() {
        Map<String, Object> st = start(Theme.AC_REACTANCE, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals(2, st.get("tasksTotal"));
        assertEquals(5, ((List<?>) map(st.get("task")).get("fields")).size());  // лёгкий уровень — 5 частот
        assertFalse(map(st.get("task")).containsKey("chart"));                  // до ответа графика нет

        st = quiz.answer(id, ServerTestSupport.answers(Theme.AC_REACTANCE, Level.EASY, SEED, 0));
        assertEquals("task_done", st.get("status"));
        assertEquals(5, st.get("taskGrade"));
        Map<String, Object> chart = map(map(st.get("task")).get("chart"));
        assertFalse(((List<?>) chart.get("ops")).isEmpty());
        assertEquals(5, ((Number) st.get("chartSec")).intValue());

        st = quiz.next(id);                                                      // второе задание — неверно
        st = quiz.answer(id, ServerTestSupport.wrong(ServerTestSupport.answers(Theme.AC_REACTANCE, Level.EASY, SEED, 1)));
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertFalse(map(st.get("task")).containsKey("chart"));
        assertFalse(st.containsKey("chartSec"));
    }

    @Test
    void resonanceChartShownAfterAnyOutcome() {
        Map<String, Object> st = start(Theme.RESONANCE, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals(4, ((List<?>) map(st.get("task")).get("fields")).size());  // f0, ρ, Q, UL
        assertFalse(map(st.get("task")).containsKey("chart"));

        st = quiz.answer(id, ServerTestSupport.wrong(ServerTestSupport.answers(Theme.RESONANCE, Level.EASY, SEED, 0)));
        assertEquals("task_done", st.get("status"));
        assertEquals(2, st.get("taskGrade"));
        assertFalse(((List<?>) map(map(st.get("task")).get("chart")).get("ops")).isEmpty());   // и после неверного
        assertEquals(5, ((Number) st.get("chartSec")).intValue());

        st = quiz.next(id);                                                      // второе задание — время вышло
        assertFalse(map(st.get("task")).containsKey("chart"));
        clock.advanceSeconds(Theme.RESONANCE.timeLimitSec(Level.EASY) + ServerQuizService.GRACE_SEC + 1);
        st = quiz.state(id);
        assertEquals("task_done", st.get("status"));
        assertTrue(map(st.get("task")).containsKey("chart"));
    }

    @Test
    void protocolErrors() {
        assertEquals(404, assertThrows(ServerError.class, () -> quiz.state("нет")).status);
        assertEquals(404, assertThrows(ServerError.class, () -> quiz.answer(null, List.of("1"))).status);
        Map<String, Object> st = start(Theme.EQUIVALENT, Level.EASY);
        String id = (String) st.get("sessionId");
        assertEquals(409, assertThrows(ServerError.class, () -> quiz.next(id)).status);   // задание ещё не закончено
        quiz.answer(id, List.of("1"));                                                   // неверно: одна попытка
        assertEquals(409, assertThrows(ServerError.class, () -> quiz.answer(id, List.of("1"))).status);
        assertEquals(400, assertThrows(ServerError.class, () -> quiz.start("", "Иванов", Theme.OHM, Level.EASY)).status);
        assertEquals(400, assertThrows(ServerError.class, () -> quiz.start("КМ-21", "Иванов", Theme.OHM, null)).status);
    }

    @Test
    void inputIsNormalizedAndValidated() {
        assertEquals("КМ-21", ServerInput.group("  км-21 "));
        assertEquals("10-52", ServerInput.group("10-52"));
        assertEquals("Иванов-Петров", ServerInput.surname("иванов-ПЕТРОВ"));
        assertEquals("Иванов Иван", ServerInput.surname("  иванов    иван "));
        assertEquals("Иванов Иван Иванович", ServerInput.surname("иванов иван иванович"));
        for (String bad : new String[]{"", "   ", null, "=1+1", "a;b", "x".repeat(81), "Иван<script>", "12"}) {
            assertThrows(ServerError.class, () -> ServerInput.surname(bad), String.valueOf(bad));
        }
        for (String bad : new String[]{"", null, "-1", "=SUM(A1)", "К;М", "1".repeat(21)}) {
            assertThrows(ServerError.class, () -> ServerInput.group(bad), String.valueOf(bad));
        }
    }

    @Test
    void manyCadetsAtOnceDoNotInterfere() throws Exception {
        ServerQuizService svc = new ServerQuizService(repo);            // настоящие часы и случайность
        int n = 24;
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<String>> jobs = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                final int k = i;
                jobs.add(pool.submit(() -> {
                    Map<String, Object> st = svc.start("КМ-2" + (k % 3), "Курсант" + "абвгдежзик".charAt(k / 10) + "абвгдежзик".charAt(k % 10),
                            Theme.EQUIVALENT, Level.EASY);
                    String id = (String) st.get("sessionId");
                    for (int t = 0; t < Theme.EQUIVALENT.tasks; t++) {
                        svc.answer(id, List.of("0"));                   // неверно -> 2, одна попытка
                        st = svc.next(id);
                    }
                    return (String) st.get("status");
                }));
            }
            for (Future<String> job : jobs) assertEquals("finished", job.get());
        } finally {
            pool.shutdownNow();
        }
        assertEquals(n, repo.count(ServerRepository.Filter.NONE));
        assertEquals(n, svc.activeCount());
    }

    /** Доводит тест до конца, пропуская время каждого задания. */
    private void finishByTimeout(String id, Theme theme, Level level) {
        for (int n = 0; n < theme.tasks; n++) {
            clock.advanceSeconds(theme.timeLimitSec(level) + ServerQuizService.GRACE_SEC + 1);
            assertEquals("task_done", quiz.state(id).get("status"));  // время вышло — задание закрыто
            quiz.next(id);
        }
        assertEquals("finished", quiz.state(id).get("status"));
    }

    @Test
    void retakeOfSameThemeOnlyAfterPause() {
        finishByTimeout((String) start(Theme.OHM, Level.EASY).get("sessionId"), Theme.OHM, Level.EASY);

        ServerError e = assertThrows(ServerError.class, () -> start(Theme.OHM, Level.HARD));
        assertEquals(409, e.status);
        assertTrue(e.getMessage().contains("15 мин."), e.getMessage());
        assertEquals("task", start(Theme.DIVIDERS, null).get("status"));   // другую тему — сразу

        clock.advanceSeconds(ServerSettings.DEFAULT_RETAKE_MIN * 60 - 60);
        assertThrows(ServerError.class, () -> start(Theme.OHM, Level.EASY));
        clock.advanceSeconds(60);
        assertEquals("task", start(Theme.OHM, Level.EASY).get("status"));
    }

    @Test
    void sameCadetWithTwoTabsGetsIndependentSessions() {
        String a = (String) start(Theme.EQUIVALENT, Level.EASY).get("sessionId");
        String b = (String) start(Theme.EQUIVALENT, Level.EASY).get("sessionId");
        assertFalse(a.equals(b));
        quiz.answer(a, List.of("0"));
        assertEquals("task", quiz.state(b).get("status"));
        assertEquals(1, repo.cadetCount());
    }

    @Test
    void settingsApplyToNewTestsOnly() {
        String running = (String) start(Theme.EQUIVALENT, Level.EASY).get("sessionId");
        Map<Theme, ThemeRules> rules = new EnumMap<>(Theme.class);
        rules.put(Theme.EQUIVALENT, new ThemeRules(3 * 60, 4 * 60, 3));
        quiz.settings().save(rules, 0);

        Map<String, Object> st = start(Theme.EQUIVALENT, Level.HARD);
        assertEquals(3, st.get("tasksTotal"));
        assertEquals(4 * 60, st.get("timeLimitSec"));
        assertTrue(((String) map(st.get("task")).get("text")).endsWith("отводится 4 минуты."));
        Map<String, Object> old = quiz.state(running);              // начатый тест — по прежним правилам
        assertEquals(2, old.get("tasksTotal"));
        assertEquals(7 * 60, old.get("timeLimitSec"));

        // сохранённое переживает перезапуск сервера, сброс возвращает значения из кода
        ServerSettings again = new ServerSettings(repo);
        assertEquals(new ThemeRules(3 * 60, 4 * 60, 3), again.rules(Theme.EQUIVALENT));
        assertEquals(Theme.OHM.defaults(), again.rules(Theme.OHM));
        assertEquals(0, again.values().retakeMin());
        again.reset();
        assertEquals(Theme.EQUIVALENT.defaults(), new ServerSettings(repo).rules(Theme.EQUIVALENT));
        assertEquals(ServerSettings.DEFAULT_RETAKE_MIN, new ServerSettings(repo).values().retakeMin());
    }

    @Test
    void zeroRetakePauseAllowsImmediateRetake() {
        quiz.settings().save(Map.of(), 0);
        finishByTimeout((String) start(Theme.OHM, Level.EASY).get("sessionId"), Theme.OHM, Level.EASY);
        assertEquals("task", start(Theme.OHM, Level.EASY).get("status"));
    }

    @Test
    void invalidSettingsAreRejected() {
        ServerSettings s = quiz.settings();
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(0, 60, 1)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(61 * 60, 60, 1)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(90, 60, 1)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(60, 60, 0)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(Theme.OHM, new ThemeRules(60, 60, 6)), 15));
        assertThrows(ServerError.class, () -> s.save(Map.of(), -1));
        assertThrows(ServerError.class, () -> s.save(Map.of(), ServerSettings.MAX_RETAKE_MIN + 1));
        assertEquals(Theme.OHM.defaults(), s.rules(Theme.OHM));      // ничего не записалось

        // в теме без выбора сложности время одно — берётся время сложного уровня
        s.save(Map.of(Theme.DIVIDERS, new ThemeRules(60, 8 * 60, 2)), 15);
        assertEquals(new ThemeRules(8 * 60, 8 * 60, 2), s.rules(Theme.DIVIDERS));
    }
}
